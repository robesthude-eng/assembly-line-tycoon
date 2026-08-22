package com.example.assemblylinetycoon.data

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.data.local.datastore.model.SavedGameState
import com.example.assemblylinetycoon.data.local.datastore.model.SavedMachine
import com.example.assemblylinetycoon.data.mapper.GameStateMapper
import com.example.assemblylinetycoon.domain.engine.FactoryBuilder
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import com.example.assemblylinetycoon.domain.model.ProductionStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты преобразования «домен ↔ файл сохранения».
 *
 * Главное здесь — круг `domain → data → domain` без потерь: любая забытая
 * строчка в маппере означает, что игрок теряет часть завода при перезапуске,
 * и заметит это только он.
 */
class GameStateMapperTest {

    private fun builtFactory(): GameState {
        var state = GameState.NEW_GAME.copy(coins = 9_999L)
        state = FactoryBuilder.place(state, GridPosition(1, 1), MachineType.SPAWNER)
        state = FactoryBuilder.placeBelt(state, GridPosition(2, 1), Direction.RIGHT)
        state = FactoryBuilder.placeBelt(state, GridPosition(3, 1), Direction.DOWN)
        state = FactoryBuilder.place(state, GridPosition(3, 2), MachineType.SMELTER)
        state = FactoryBuilder.upgrade(state, state.machineAt(GridPosition(3, 2))!!.id)
        return state
    }

    @Test // круг «домен → данные → домен» не меняет ни одного значения
    fun roundTripKeepsEverything() {
        val original = builtFactory().copy(
            unlockedTechMask = 0b1011L,
            unlockedSlots = 3,
            baselineProductionRate = 12.5,
            overdriveUntilMillis = 1_700_000_500_000L,
            lastSavedAtMillis = 1_700_000_000_000L,
            lastTickAtMillis = 1_700_000_000_050L,
            movingItems = listOf(
                MovingItem("iron_ore", 2, GridPosition(2, 1), GridPosition(3, 1), 0.37f),
            ),
            stats = ProductionStats(
                itemsProduced = 120L,
                itemsExported = 90L,
                coinsEarned = 4_500L,
                simulatedMillis = 600_000L,
            ),
        )

        val restored = GameStateMapper.toDomain(GameStateMapper.toData(original))

        assertEquals(original, restored)
    }

    @Test // деньги остаются целыми: копейки в Double ломают экономику
    fun coinsStayLong() {
        val huge = GameState.NEW_GAME.copy(coins = 9_007_199_254_740_993L)

        val restored = GameStateMapper.toDomain(GameStateMapper.toData(huge))

        // Значение больше, чем Double умеет представить точно: если валюта
        // где-нибудь пройдёт через плавающую точку, тест это поймает.
        assertEquals(9_007_199_254_740_993L, restored.coins)
    }

    @Test // раскладка завода восстанавливается клетка в клетку
    fun factoryLayoutIsRestored() {
        val original = builtFactory()

        val restored = GameStateMapper.toDomain(GameStateMapper.toData(original))

        assertEquals(original.grid, restored.grid)
        assertEquals(MachineType.SPAWNER, restored.machineAt(GridPosition(1, 1))?.type)
        assertEquals(Direction.DOWN, restored.grid[GridPosition(3, 1)]?.direction)
        assertEquals(1, restored.machineAt(GridPosition(3, 2))?.level)
    }

    @Test // состояние машины и её буферы переживают сохранение
    fun machineStateSurvives() {
        val machine = builtFactory().machines.values.first().copy(
            status = MachineStatus.CRAFTING,
            elapsedMillis = 777L,
            inputBuffer = mapOf("iron_ore" to 3),
            outputBuffer = mapOf("iron_ingot" to 1),
        )
        val state = GameState.NEW_GAME.copy(machines = mapOf(machine.id to machine))

        val restored = GameStateMapper.toDomain(GameStateMapper.toData(state))

        assertEquals(machine, restored.machines[machine.id])
    }

    @Test // машина неизвестного типа пропускается, остальной завод цел
    fun unknownMachineTypeIsSkipped() {
        val saved = SavedGameState(
            coins = 500L,
            machines = listOf(
                SavedMachine(id = 1, type = "SMELTER", x = 2, y = 2),
                SavedMachine(id = 2, type = "ТЕЛЕПОРТ_ИЗ_БУДУЩЕГО", x = 3, y = 3),
            ),
        )

        val restored = GameStateMapper.toDomain(saved)

        // Откат на старую версию игры не должен стоить игроку всего завода.
        assertEquals(1, restored.machines.size)
        assertEquals(MachineType.SMELTER, restored.machines[1]?.type)
        assertNull(restored.machines[2])
        assertEquals(500L, restored.coins)
    }

    @Test // счётчик идентификаторов не может оказаться позади живых машин
    fun nextMachineIdNeverCollides() {
        val saved = SavedGameState(
            machines = listOf(SavedMachine(id = 7, type = "PRESS", x = 1, y = 1)),
            nextMachineId = 2, // испорченное или устаревшее значение
        )

        val restored = GameStateMapper.toDomain(saved)

        assertTrue("Новая машина затёрла бы существующую", restored.nextMachineId > 7)
    }

    @Test // недостающие клетки добиваются пустыми, поле всегда полное
    fun shortCellListIsPaddedWithEmptyCells() {
        val saved = SavedGameState(cells = emptyList())

        val grid = GameStateMapper.toDomain(saved).grid

        assertEquals(GameConstants.GRID_WIDTH * GameConstants.GRID_HEIGHT, grid.cells.size)
        assertTrue(grid.cells.all { it.isEmpty })
    }

    @Test // мусор в направлении и статусе заменяется безопасным значением
    fun brokenEnumValuesFallBackInsteadOfCrashing() {
        val saved = SavedGameState(
            machines = listOf(
                SavedMachine(id = 1, type = "SMELTER", facing = "НАИСКОСОК", status = "ПОЁТ"),
            ),
        )

        val machine = GameStateMapper.toDomain(saved).machines.getValue(1)

        assertEquals(Direction.RIGHT, machine.facing)
        assertEquals(MachineStatus.IDLE, machine.status)
    }

    @Test // доля пути предмета зажимается в разумные пределы
    fun itemProgressIsClamped() {
        val saved = GameStateMapper.toData(
            GameState.NEW_GAME.copy(
                movingItems = listOf(
                    MovingItem("iron_ore", 1, GridPosition(0, 0), GridPosition(1, 0), 4.5f),
                ),
            ),
        )

        assertEquals(1f, GameStateMapper.toDomain(saved).movingItems.single().progress, 0.0001f)
    }
}
