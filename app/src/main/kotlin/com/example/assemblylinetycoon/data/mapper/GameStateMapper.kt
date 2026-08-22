package com.example.assemblylinetycoon.data.mapper

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.data.local.datastore.model.SavedCell
import com.example.assemblylinetycoon.data.local.datastore.model.SavedGameState
import com.example.assemblylinetycoon.data.local.datastore.model.SavedItem
import com.example.assemblylinetycoon.data.local.datastore.model.SavedMachine
import com.example.assemblylinetycoon.data.local.datastore.model.SavedStats
import com.example.assemblylinetycoon.domain.model.Cell
import com.example.assemblylinetycoon.domain.model.CellType
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import com.example.assemblylinetycoon.domain.model.ProductionStats

/**
 * Перевод между снапшотом симуляции и моделями файла сохранения.
 *
 * Единственное место в приложении, где эти два мира встречаются. Наружу
 * `Saved*`-классы не выходят: репозиторий отдаёт домену [GameState], и ни
 * презентация, ни движок не знают, в каком виде игра лежит на диске.
 *
 * Чтение всегда снисходительно: испорченное или незнакомое значение заменяется
 * безопасным, а не роняет загрузку. Потерять одну ячейку лучше, чем весь
 * прогресс игрока.
 */
object GameStateMapper {

    // ── домен → данные ──────────────────────────────────────────────────────

    fun toData(state: GameState): SavedGameState = SavedGameState(
        schemaVersion = GameConstants.SAVE_SCHEMA_VERSION,
        coins = state.coins,
        stats = state.stats.toData(),
        gridWidth = state.grid.width,
        gridHeight = state.grid.height,
        cells = state.grid.cells.map { it.toData() },
        machines = state.machines.values.map { it.toData() },
        nextMachineId = state.nextMachineId,
        items = state.movingItems.map { it.toData() },
        unlockedTechMask = state.unlockedTechMask,
        unlockedSlots = state.unlockedSlots,
        baselineProductionRate = state.baselineProductionRate,
        isInitialized = state.isInitialized,
        lastSavedAtMillis = state.lastSavedAtMillis,
        lastTickAtMillis = state.lastTickAtMillis,
        overdriveUntilMillis = state.overdriveUntilMillis,
    )

    private fun Cell.toData(): SavedCell = SavedCell(
        type = type.name,
        direction = direction.name,
        machineId = machineId,
    )

    private fun Machine.toData(): SavedMachine = SavedMachine(
        id = id,
        type = type.name,
        x = position.x,
        y = position.y,
        facing = facing.name,
        level = level,
        recipeOutputId = recipeOutputId,
        status = status.name,
        elapsedMillis = elapsedMillis,
        inputBuffer = inputBuffer,
        outputBuffer = outputBuffer,
    )

    private fun MovingItem.toData(): SavedItem = SavedItem(
        itemId = itemId,
        amount = amount,
        fromX = from.x,
        fromY = from.y,
        toX = to.x,
        toY = to.y,
        progress = progress,
    )

    private fun ProductionStats.toData(): SavedStats = SavedStats(
        itemsProduced = itemsProduced,
        itemsExported = itemsExported,
        coinsEarned = coinsEarned,
        simulatedMillis = simulatedMillis,
    )

    // ── данные → домен ──────────────────────────────────────────────────────

    fun toDomain(saved: SavedGameState): GameState {
        val expectedCells = saved.gridWidth * saved.gridHeight
        // Размер поля мог измениться между версиями игры: недостающие клетки
        // добиваем пустыми, лишние отбрасываем. Падать здесь нельзя —
        // на другом конце игрок с построенным заводом.
        val cells = List(expectedCells) { index ->
            saved.cells.getOrNull(index)?.toDomain() ?: Cell.EMPTY_CELL
        }

        val machines = saved.machines
            .mapNotNull { it.toDomain() }
            .associateBy(Machine::id)

        return GameState(
            schemaVersion = GameConstants.SAVE_SCHEMA_VERSION,
            coins = saved.coins,
            lastSavedAtMillis = saved.lastSavedAtMillis,
            unlockedTechMask = saved.unlockedTechMask,
            baselineProductionRate = saved.baselineProductionRate,
            isInitialized = saved.isInitialized,
            grid = FactoryGrid(
                width = saved.gridWidth,
                height = saved.gridHeight,
                cells = cells,
            ),
            machines = machines,
            // Счётчик не может оказаться меньше уже занятых идентификаторов,
            // иначе новая машина затрёт существующую.
            nextMachineId = maxOf(saved.nextMachineId, (machines.keys.maxOrNull() ?: 0) + 1),
            unlockedSlots = saved.unlockedSlots.coerceAtLeast(1),
            overdriveUntilMillis = saved.overdriveUntilMillis,
            movingItems = saved.items.map { it.toDomain() },
            stats = saved.stats.toDomain(),
            lastTickAtMillis = saved.lastTickAtMillis,
        )
    }

    private fun SavedCell.toDomain(): Cell = Cell(
        type = enumOrNull<CellType>(type) ?: CellType.EMPTY,
        direction = enumOrNull<Direction>(direction) ?: Direction.RIGHT,
        machineId = machineId,
    )

    /**
     * Машина с неизвестным типом пропускается.
     *
     * Такое возможно, если игрок откатился на старую версию игры: типа ещё
     * нет в коде, но он уже есть в файле. Пропустить одну машину — приемлемо,
     * уронить загрузку всего завода — нет.
     */
    private fun SavedMachine.toDomain(): Machine? {
        val machineType = enumOrNull<MachineType>(type) ?: return null
        return Machine(
            id = id,
            type = machineType,
            position = GridPosition(x, y),
            facing = enumOrNull<Direction>(facing) ?: Direction.RIGHT,
            level = level.coerceAtLeast(0),
            recipeOutputId = recipeOutputId,
            status = enumOrNull<MachineStatus>(status) ?: MachineStatus.IDLE,
            elapsedMillis = elapsedMillis.coerceAtLeast(0L),
            inputBuffer = inputBuffer,
            outputBuffer = outputBuffer,
        )
    }

    private fun SavedItem.toDomain(): MovingItem = MovingItem(
        itemId = itemId,
        amount = amount,
        from = GridPosition(fromX, fromY),
        to = GridPosition(toX, toY),
        progress = progress.coerceIn(0f, 1f),
    )

    private fun SavedStats.toDomain(): ProductionStats = ProductionStats(
        itemsProduced = itemsProduced,
        itemsExported = itemsExported,
        coinsEarned = coinsEarned,
        simulatedMillis = simulatedMillis,
    )

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
