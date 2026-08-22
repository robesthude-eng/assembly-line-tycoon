package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.core.utils.MathUtility
import com.example.assemblylinetycoon.domain.catalog.ItemCatalog
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.CellType
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import com.example.assemblylinetycoon.domain.model.ProductionStats
import com.example.assemblylinetycoon.domain.model.Recipe

/**
 * Шаг симуляции завода: чистая функция `состояние + Δt → новое состояние`.
 *
 * Ни корутин, ни времени, ни ввода-вывода: движок вызывает [step], а сам
 * объект ничего не знает ни о тикере, ни об Android. Благодаря этому весь
 * завод проверяется обычными JVM-тестами без диспетчеров и задержек.
 *
 * Детерминированность обеспечивается порядком обхода: машины перебираются по
 * возрастанию идентификатора, предметы — в порядке списка. Случайных величин
 * в симуляции нет, поэтому одинаковый вход всегда даёт одинаковый выход.
 *
 * Порядок фаз внутри такта важен:
 *  1. машины — выработка и выгрузка на ленту;
 *  2. ленты — движение предметов и доставка в приёмники.
 *
 * Обратный порядок дал бы предмету возможность за один тик проехать от
 * машины сразу дальше по ленте — скорость конвейера зависела бы от того,
 * в какой клетке он стоит.
 */
object FactorySimulation {

    /** Предел смен фаз одной машины за такт — защита от зацикливания. */
    private const val MAX_PHASE_TRANSITIONS_PER_TICK = 64

    /**
     * Один шаг симуляции.
     *
     * @param deltaMillis сколько игрового времени прошло. Значение обрезается
     *   [GameConstants.MAX_TICK_DELTA_MS]: после паузы в системе (звонок,
     *   свёрнутое приложение) огромная дельта телепортировала бы предметы
     *   через занятые клетки и ломала бы противодавление.
     */
    fun step(state: GameState, deltaMillis: Long): GameState {
        if (deltaMillis <= 0L) return state
        val delta = deltaMillis.coerceAtMost(GameConstants.MAX_TICK_DELTA_MS)

        val afterMachines = updateMachines(state, delta)
        val afterBelts = moveItems(afterMachines, delta)

        return afterBelts.copy(
            stats = afterBelts.stats.copy(
                simulatedMillis = afterBelts.stats.simulatedMillis + delta,
            ),
        )
    }

    // ── Машины ──────────────────────────────────────────────────────────────

    /**
     * Обновление всех машин: приём такта, накопление прогресса, выгрузка.
     * Машины обходятся по возрастанию идентификатора — порядок постройки.
     */
    private fun updateMachines(state: GameState, delta: Long): GameState {
        var current = state
        state.machines.keys.sorted().forEach { id ->
            current = updateMachine(current, id, delta)
        }
        return current
    }

    /**
     * Фазы одной машины в пределах такта.
     *
     * Смена фазы не «съедает» время: если машина простаивала, а сырьё есть,
     * она в этом же такте начнёт крафт и потратит на него дельту. Иначе один
     * тик уходил бы на каждый переход, и два такта по 100 мс отличались бы
     * от одного такта в 200 мс — симуляция зависела бы от частоты кадров.
     *
     * Счётчик переходов защищает от зацикливания: при дельте 1000 мс и
     * минимальной длительности 100 мс машина успевает не больше десятка
     * циклов, запас взят с избытком.
     */
    private fun updateMachine(state: GameState, machineId: Int, delta: Long): GameState {
        var current = state
        var remaining = delta

        repeat(MAX_PHASE_TRANSITIONS_PER_TICK) {
            val machine = current.machines[machineId] ?: return current
            when (machine.status) {
                // Экспортёр не производит: его работа — приём предметов.
                MachineStatus.IDLE -> {
                    if (machine.type.isSink) return current
                    val started = tryStartCrafting(current, machine)
                    if (started === current) return current      // нет сырья
                    current = started
                }

                MachineStatus.CRAFTING -> {
                    if (remaining <= 0L) return current
                    val (next, used) = advanceCrafting(current, machine, remaining)
                    current = next
                    remaining -= used
                    // Такт не закончился — ждём следующего вызова.
                    if (current.machines[machineId]?.status == MachineStatus.CRAFTING) return current
                }

                MachineStatus.OUTPUT_EJECT -> {
                    val ejected = tryEject(current, machine)
                    if (ejected === current) return current      // выход занят
                    current = ejected
                }
            }
        }
        return current
    }

    /** IDLE → CRAFTING: если хватает сырья, оно списывается и такт начинается. */
    private fun tryStartCrafting(state: GameState, machine: Machine): GameState {
        val recipe = recipeOf(machine) ?: return state
        // Карьеру входы не нужны, остальным — обязательны.
        if (recipe.requiresInputs && !recipe.canCraftFrom(machine.inputBuffer)) return state

        val consumed = if (recipe.requiresInputs) recipe.consumeFrom(machine.inputBuffer) else machine.inputBuffer
        return state.withMachine(
            machine.copy(
                inputBuffer = consumed,
                status = MachineStatus.CRAFTING,
                elapsedMillis = 0L,
            ),
        )
    }

    /**
     * CRAFTING: списание времени с дельты.
     * @return новое состояние и сколько миллисекунд такт израсходовал.
     */
    private fun advanceCrafting(state: GameState, machine: Machine, remaining: Long): Pair<GameState, Long> {
        val recipe = recipeOf(machine)
            ?: return state.withMachine(machine.copy(status = MachineStatus.IDLE)) to 0L

        val duration = MathUtility.craftDuration(recipe.baseDurationMillis, machine.level)
        val needed = duration - machine.elapsedMillis

        if (remaining < needed) {
            return state.withMachine(machine.copy(elapsedMillis = machine.elapsedMillis + remaining)) to remaining
        }

        val produced = machine.outputBuffer.toMutableMap()
        produced[recipe.outputItemId] = (produced[recipe.outputItemId] ?: 0) + recipe.outputAmount

        val next = state
            .withMachine(
                machine.copy(
                    outputBuffer = produced,
                    status = MachineStatus.OUTPUT_EJECT,
                    elapsedMillis = 0L,
                ),
            )
            .copy(
                stats = state.stats.copy(
                    itemsProduced = state.stats.itemsProduced + recipe.outputAmount,
                ),
            )
        return next to needed
    }

    /**
     * OUTPUT_EJECT: попытка выложить результат в клетку перед машиной.
     * Если выход занят или его нет, машина остаётся с готовым предметом —
     * это противодавление на уровне оборудования.
     */
    private fun tryEject(state: GameState, machine: Machine): GameState {
        val entry = machine.outputBuffer.entries.firstOrNull()
            ?: return state.withMachine(machine.copy(status = MachineStatus.IDLE))

        val target = machine.outputPosition
        val cell = state.grid[target] ?: return state
        if (!cell.type.acceptsItems) return state
        if (!state.isCellFree(target)) return state

        val remaining = entry.value - 1
        val buffer = machine.outputBuffer.toMutableMap()
        if (remaining > 0) buffer[entry.key] = remaining else buffer.remove(entry.key)

        val ejected = MovingItem.ejected(
            itemId = entry.key,
            amount = 1,
            from = machine.position,
            to = target,
        )

        return state
            .withMachine(
                machine.copy(
                    outputBuffer = buffer,
                    status = if (buffer.isEmpty()) MachineStatus.IDLE else MachineStatus.OUTPUT_EJECT,
                ),
            )
            .copy(movingItems = state.movingItems + ejected)
    }

    private fun recipeOf(machine: Machine): Recipe? =
        machine.recipeOutputId?.let(RecipeCatalog::forOutput)

    // ── Конвейеры ───────────────────────────────────────────────────────────

    /**
     * Движение предметов по лентам.
     *
     * Каждый предмет продвигается на `delta / BELT_TRAVEL_TIME_MS`. Доехав
     * до конца клетки, он либо переходит в следующую (если та свободна),
     * либо доставляется в машину, либо остаётся стоять с прогрессом 1.0.
     */
    private fun moveItems(state: GameState, delta: Long): GameState {
        if (state.movingItems.isEmpty()) return state

        val step = delta.toFloat() / GameConstants.BELT_TRAVEL_TIME_MS
        // Клетки, занятые предметами, которые в этом такте ещё не сдвинулись.
        val reserved = state.movingItems.mapTo(mutableSetOf(), MovingItem::to)

        var machines = state.machines
        var coins = state.coins
        var exported = 0L
        var earned = 0L
        val result = mutableListOf<MovingItem>()

        state.movingItems.forEach { item ->
            val advanced = (item.progress + step).coerceAtMost(1f)
            if (advanced < 1f) {
                result += item.copy(progress = advanced)
                return@forEach
            }

            val cell = state.grid[item.to]
            if (cell == null) {
                // Клетка исчезла (игрок снёс постройку) — предмет пропадает
                // вместе с ней, зависших в пустоте предметов быть не должно.
                reserved -= item.to
                return@forEach
            }

            when (cell.type) {
                CellType.EXPORTER -> {
                    val price = ItemCatalog.find(item.itemId)?.basePrice ?: 0L
                    val revenue = price * item.amount
                    coins = MathUtility.addCoins(coins, revenue)
                    earned += revenue
                    exported += item.amount
                    reserved -= item.to
                }

                CellType.MACHINE, CellType.SPAWNER -> {
                    val machine = cell.machineId?.let(machines::get)
                    val accepted = machine?.let { tryAccept(it, item) }
                    if (accepted != null) {
                        machines = machines + (accepted.id to accepted)
                        reserved -= item.to
                    } else {
                        // Буфер полон или машина не принимает — предмет ждёт.
                        result += item.copy(progress = 1f)
                    }
                }

                CellType.BELT -> {
                    val next = item.to.neighbor(cell.direction)
                    val nextCell = state.grid[next]
                    val canAdvance = nextCell != null &&
                        nextCell.type.acceptsItems &&
                        next !in reserved
                    if (canAdvance) {
                        reserved -= item.to
                        reserved += next
                        result += item.copy(from = item.to, to = next, progress = 0f)
                    } else {
                        // Тупик или занято: прогресс замирает на единице.
                        result += item.copy(progress = 1f)
                    }
                }

                CellType.EMPTY -> {
                    result += item.copy(progress = 1f)
                }
            }
        }

        return state.copy(
            movingItems = result,
            machines = machines,
            coins = coins,
            stats = state.stats.copy(
                itemsExported = state.stats.itemsExported + exported,
                coinsEarned = state.stats.coinsEarned + earned,
            ),
        )
    }

    /**
     * Приём предмета во входной буфер машины.
     * Возвращает обновлённую машину или null, если принять нельзя.
     */
    private fun tryAccept(machine: Machine, item: MovingItem): Machine? {
        // Карьеру сырьё не нужно: он источник, а не переработчик.
        if (machine.type.isSource) return null

        val stored = machine.inputBuffer[item.itemId] ?: 0
        if (stored + item.amount > Machine.BUFFER_CAPACITY) return null

        return machine.copy(
            inputBuffer = machine.inputBuffer + (item.itemId to stored + item.amount),
        )
    }

    // ── Вспомогательное ─────────────────────────────────────────────────────

    private fun GameState.withMachine(machine: Machine): GameState =
        copy(machines = machines + (machine.id to machine))

    /**
     * Актуализация базовой производительности для офлайн-расчёта.
     *
     * Вызывается при сохранении, а не каждый тик: величина усреднённая,
     * и пересчитывать её 20 раз в секунду бессмысленно.
     */
    fun withRefreshedProductionRate(state: GameState): GameState =
        state.copy(baselineProductionRate = state.stats.coinsPerSecond)

    /** Пустая статистика — вынесено, чтобы не тянуть модель в тесты движка. */
    val emptyStats: ProductionStats get() = ProductionStats.EMPTY

    /** Свободна ли клетка [position] для постройки. */
    fun canBuildAt(state: GameState, position: GridPosition): Boolean {
        val cell = state.grid[position] ?: return false
        return cell.isEmpty && state.isCellFree(position)
    }
}
