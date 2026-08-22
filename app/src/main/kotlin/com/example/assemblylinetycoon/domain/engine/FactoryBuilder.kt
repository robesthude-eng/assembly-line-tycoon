package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.domain.catalog.BeltCatalog
import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.Cell
import com.example.assemblylinetycoon.domain.model.CellType
import com.example.assemblylinetycoon.domain.model.MachineType

/**
 * Строительство и улучшение оборудования.
 *
 * Чистые функции над [GameState], как и вся симуляция: ни корутин, ни времени,
 * ни Android. Отделено от [FactorySimulation] потому, что это разные вещи —
 * там течёт время само по себе, здесь состояние меняется по воле игрока.
 *
 * Правило слоя: цены берутся исключительно из [MachineCatalog]. Ни одна
 * формула стоимости здесь не пишется заново — иначе цена в магазине и цена
 * при списании денег однажды разойдутся.
 *
 * Отказ выражается возвратом **того же самого состояния**: движок не умеет
 * показывать диалоги, а UI и так знает, хватает ли денег, — он спрашивает об
 * этом [canPlace] и [canUpgrade] до отправки команды.
 */
object FactoryBuilder {

    /** Запасное направление, если поле подсказать ничего не смогло. */
    val DEFAULT_FACING: Direction = Direction.RIGHT

    /**
     * Доля стоимости, возвращаемая при сносе.
     *
     * Половина — не для экономики, а против тупика: игрок, поставивший станок
     * не туда, обязан иметь возможность исправиться. Полный возврат сделал бы
     * перестановку бесплатной и обесценил выбор места, ноль (как было раньше)
     * запирал игру намертво: денег на новый станок нет, а старый не работает.
     * Заработать на этом нельзя — продаётся всегда дешевле, чем покупалось.
     */
    const val REFUND_RATE: Double = 0.5

    /**
     * Цена постройки машины типа [type] прямо сейчас.
     *
     * Зависит от того, сколько таких уже стоит на поле: множитель
     * `costGrowth` превращает «поставить ещё один ассемблер» из очевидного
     * решения в выбор.
     */
    fun buildCost(state: GameState, type: MachineType): Long =
        MachineCatalog.buildCost(type, ownedCount = state.machineCount(type))

    /** Цена следующего уровня машины; `null`, если такой машины нет. */
    fun upgradeCost(state: GameState, machineId: Int): Long? =
        state.machines[machineId]?.nextUpgradeCost()

    /** Свободна ли ячейка под постройку: внутри поля и ничем не занята. */
    fun isBuildable(state: GameState, position: GridPosition): Boolean {
        val cell = state.grid[position] ?: return false
        return cell.isEmpty && state.machineAt(position) == null
    }

    /** Можно ли построить: место свободно и денег достаточно. */
    fun canPlace(state: GameState, position: GridPosition, type: MachineType): Boolean =
        isBuildable(state, position) && state.coins >= buildCost(state, type)

    /** Можно ли улучшить: машина существует и денег достаточно. */
    fun canUpgrade(state: GameState, machineId: Int): Boolean {
        val cost = upgradeCost(state, machineId) ?: return false
        return state.coins >= cost
    }

    /**
     * Куда будет смотреть машина, построенная в этой клетке.
     *
     * Направление выдачи выбирается по полю, а не берётся всегда «вправо».
     * Причина простая: станок, поставленный у правого края, выкидывал бы
     * продукцию за границу поля и молча не работал — с точки зрения игрока
     * это выглядит как сломанная игра, а не как его ошибка.
     *
     * Порядок предпочтений: уже проложенная лента рядом → свободная клетка
     * в сторону центра поля → любая клетка внутри поля.
     */
    fun defaultFacing(state: GameState, position: GridPosition): Direction {
        val inside = Direction.entries.filter { state.grid.contains(position.neighbor(it)) }
        if (inside.isEmpty()) return DEFAULT_FACING

        // Лента рядом — почти наверняка то, ради чего станок и ставят.
        inside.firstOrNull { state.grid[position.neighbor(it)]?.isBelt == true }?.let { return it }

        val centerX = (state.grid.width - 1) / 2f
        val centerY = (state.grid.height - 1) / 2f
        val free = inside.filter { state.grid[position.neighbor(it)]?.isEmpty == true }
        val candidates = free.ifEmpty { inside }

        // Из оставшихся — та, что ведёт ближе к центру поля: там больше места
        // для линии, чем у края.
        return candidates.minByOrNull { direction ->
            val next = position.neighbor(direction)
            val dx = next.x - centerX
            val dy = next.y - centerY
            dx * dx + dy * dy
        } ?: DEFAULT_FACING
    }

    /**
     * Постройка машины.
     *
     * Идентификатор берётся из счётчика [GameState.nextMachineId] и только
     * растёт: переиспользование номеров снесённых машин сломало бы ссылки из
     * ячеек поля и открытых диалогов.
     *
     * @return новое состояние либо исходное, если построить нельзя.
     */
    fun place(state: GameState, position: GridPosition, type: MachineType): GameState {
        if (!canPlace(state, position, type)) return state

        val cost = buildCost(state, type)
        val machine = Machine(
            id = state.nextMachineId,
            type = type,
            position = position,
            facing = defaultFacing(state, position),
            level = 0,
            // Рецепт по умолчанию — первый доступный этой машине. Выбор
            // рецепта игроком появится вместе с экраном настройки машины;
            // до тех пор станок должен работать сразу после постройки.
            recipeOutputId = defaultRecipeFor(type),
        )

        return state.copy(
            coins = state.coins - cost,
            machines = state.machines + (machine.id to machine),
            grid = state.grid.withMachine(machine),
            nextMachineId = state.nextMachineId + 1,
        )
    }

    /**
     * Улучшение машины: минус деньги, плюс уровень.
     *
     * Накопленный прогресс такта не сбрасывается. Игрок платит за ускорение,
     * и отматывать ему почти доделанную деталь было бы наказанием за покупку;
     * длительность такта пересчитывается на лету в [FactorySimulation].
     */
    fun upgrade(state: GameState, machineId: Int): GameState {
        if (!canUpgrade(state, machineId)) return state
        val machine = state.machines[machineId] ?: return state
        val cost = machine.nextUpgradeCost()
        val upgraded = machine.copy(level = machine.level + 1)

        return state.copy(
            coins = state.coins - cost,
            machines = state.machines + (machineId to upgraded),
            // Ячейка хранит ссылку на машину, поэтому переписывать её не
            // нужно: тип и направление при улучшении не меняются.
        )
    }

    /** Что машина этого типа начнёт производить сразу после постройки. */
    fun defaultRecipeFor(type: MachineType): String? =
        RecipeCatalog.forMachine(type).firstOrNull()?.outputItemId

    /** Список доступного к постройке оборудования, от дешёвого к дорогому. */
    fun purchasableTypes(): List<MachineType> = MachineCatalog.purchasable()

    // ── Конвейер ────────────────────────────────────────────────────────────

    /** Сколько отрезков ленты уже проложено — вход для расчёта цены. */
    fun beltCount(state: GameState): Int = state.grid.cells.count { it.isBelt }

    /** Цена следующего отрезка конвейера. */
    fun beltCost(state: GameState): Long = BeltCatalog.buildCost(beltCount(state))

    /** Можно ли проложить ленту: место свободно и денег хватает. */
    fun canPlaceBelt(state: GameState, position: GridPosition): Boolean =
        isBuildable(state, position) && state.coins >= beltCost(state)

    /** Прокладка отрезка конвейера. */
    fun placeBelt(state: GameState, position: GridPosition, direction: Direction): GameState {
        if (!canPlaceBelt(state, position)) return state

        return state.copy(
            coins = state.coins - beltCost(state),
            grid = state.grid.withBelt(position, direction),
        )
    }

    /**
     * Поворот ленты или станка — бесплатно (см. [BeltCatalog.ROTATE_COST]).
     *
     * Разворот станка меняет только то, куда он выкладывает результат.
     * Плата за поворот наказывала бы за исправление собственной ошибки и
     * подталкивала сносить и строить заново, что дороже для игрока и глупее
     * по смыслу.
     *
     * Уже готовый предмет в выходном буфере не теряется: он просто поедет
     * в новую сторону на следующем такте.
     */
    fun rotate(state: GameState, position: GridPosition, direction: Direction): GameState {
        val machine = state.machineAt(position)
        if (machine != null) {
            if (machine.facing == direction) return state
            val turned = machine.copy(facing = direction)
            return state.copy(
                machines = state.machines + (machine.id to turned),
                grid = state.grid.withMachine(turned),
            )
        }

        val cell = state.grid[position] ?: return state
        if (!cell.isBelt || cell.direction == direction) return state
        return state.copy(grid = state.grid.withBelt(position, direction))
    }

    /** Совместимость с прежним названием: поворачивает и ленту, и станок. */
    fun rotateBelt(state: GameState, position: GridPosition, direction: Direction): GameState =
        rotate(state, position, direction)

    /** Можно ли повернуть содержимое клетки. */
    fun canRotate(state: GameState, position: GridPosition): Boolean {
        if (state.machineAt(position) != null) return true
        return state.grid[position]?.isBelt == true
    }

    // ── Снос ────────────────────────────────────────────────────────────────

    /** Есть ли в ячейке что сносить. */
    fun canDemolish(state: GameState, position: GridPosition): Boolean {
        val cell = state.grid[position] ?: return false
        return !cell.isEmpty
    }

    /**
     * Сколько вернётся за снос содержимого клетки.
     *
     * Считается от **текущей** цены такой же постройки, а не от той, что
     * игрок когда-то заплатил: цена зависит от количества уже построенного,
     * и хранить историю покупок ради этого не стоит. Улучшения не
     * возвращаются — они потрачены на работу станка.
     */
    fun refundFor(state: GameState, position: GridPosition): Long {
        val machine = state.machineAt(position)
        if (machine != null) {
            // Цена «такой же следующей» падает на один шаг: столько машина
            // стоила бы, будь она последней купленной.
            val owned = (state.machineCount(machine.type) - 1).coerceAtLeast(0)
            return (MachineCatalog.buildCost(machine.type, owned) * REFUND_RATE).toLong()
        }
        if (state.grid[position]?.isBelt == true) {
            val owned = (beltCount(state) - 1).coerceAtLeast(0)
            return (BeltCatalog.buildCost(owned) * REFUND_RATE).toLong()
        }
        return 0L
    }

    /**
     * Снос содержимого ячейки с частичным возвратом денег.
     *
     * Возврата сначала не было вовсе — и это оказалось ошибкой, а не строгим
     * балансом: игрок, поставивший станок у края поля, оставался с мёртвым
     * заводом и суммой, которой не хватает на новый. Игра запиралась
     * без единого сообщения о том, что произошло.
     *
     * Предметы, ехавшие в снесённую клетку или из неё, исчезают вместе с ней:
     * иначе они зависли бы на несуществующей ленте и заблокировали соседей.
     */
    fun demolish(state: GameState, position: GridPosition): GameState {
        if (!canDemolish(state, position)) return state

        val machine = state.machineAt(position)
        val refund = refundFor(state, position)
        return state.copy(
            coins = state.coins + refund,
            grid = state.grid.cleared(position),
            machines = if (machine == null) state.machines else state.machines - machine.id,
            movingItems = state.movingItems.filterNot { it.from == position || it.to == position },
        )
    }

    /** Что стоит в ячейке — для выбора нужного диалога. */
    fun cellTypeAt(state: GameState, position: GridPosition): CellType =
        (state.grid[position] ?: Cell.EMPTY_CELL).type
}
