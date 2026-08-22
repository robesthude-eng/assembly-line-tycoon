package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.domain.model.Cell
import com.example.assemblylinetycoon.domain.model.CellType
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MovingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Движение предметов по конвейеру: скорость, маршрутизация по направлению
 * ленты и остановка перед занятой клеткой.
 */
class ConveyorMovementTest {

    private val travel = GameConstants.BELT_TRAVEL_TIME_MS

    /** Прямая лента длиной [length] вправо, начиная с (0, 0). */
    private fun beltRow(length: Int, direction: Direction = Direction.RIGHT): FactoryGrid =
        (0 until length).fold(FactoryGrid.EMPTY) { grid, x ->
            grid.withBelt(GridPosition(x, 0), direction)
        }

    private fun stateWith(grid: FactoryGrid, vararg items: MovingItem) =
        GameState.EMPTY.copy(grid = grid, movingItems = items.toList())

    @Test // прогресс растёт пропорционально прошедшему времени
    fun progressAdvancesWithDeltaTime() {
        val state = stateWith(
            beltRow(3),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0)),
        )

        val afterHalf = FactorySimulation.step(state, travel / 2)

        assertEquals(0.5f, afterHalf.movingItems.single().progress, 0.001f)
    }

    @Test // за время прохода клетки предмет переезжает в следующую
    fun itemMovesToNextCellWhenProgressCompletes() {
        val state = stateWith(
            beltRow(3),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0)),
        )

        val moved = FactorySimulation.step(state, travel)
        val item = moved.movingItems.single()

        assertEquals(GridPosition(1, 0), item.from)
        assertEquals(GridPosition(2, 0), item.to)
        assertEquals(0f, item.progress, 0.001f)
    }

    @Test // маршрут определяется направлением ленты, а не координатами
    fun directionRoutingWorks() {
        val grid = FactoryGrid.EMPTY
            .withBelt(GridPosition(1, 1), Direction.DOWN)
            .withBelt(GridPosition(1, 2), Direction.LEFT)
            .withBelt(GridPosition(0, 2), Direction.UP)
            .withBelt(GridPosition(0, 1), Direction.UP)

        val state = stateWith(
            grid,
            MovingItem(ItemId.GEAR.key, 1, GridPosition(1, 0), GridPosition(1, 1)),
        )

        val afterFirst = FactorySimulation.step(state, travel)
        assertEquals(GridPosition(1, 2), afterFirst.movingItems.single().to)

        val afterSecond = FactorySimulation.step(afterFirst, travel)
        assertEquals(GridPosition(0, 2), afterSecond.movingItems.single().to)

        val afterThird = FactorySimulation.step(afterSecond, travel)
        assertEquals(GridPosition(0, 1), afterThird.movingItems.single().to)
    }

    @Test // предмет перед занятой клеткой замирает с прогрессом ровно 1.0
    fun blockedItemStopsAtFullProgress() {
        val state = stateWith(
            beltRow(3),
            // Первый предмет уже стоит в клетке (2,0) — она занята.
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 1f),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0), progress = 0.9f),
        )

        val after = FactorySimulation.step(state, travel)
        val follower = after.movingItems.first { it.from == GridPosition(0, 0) }

        assertEquals(1f, follower.progress, 0.001f)
        assertEquals(GridPosition(1, 0), follower.to)
    }

    @Test // затор не рассасывается сам: за несколько тактов ничего не меняется
    fun blockedItemStaysBlockedAcrossTicks() {
        var state = stateWith(
            beltRow(3),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 1f),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0), progress = 1f),
        )

        repeat(10) { state = FactorySimulation.step(state, travel) }

        assertEquals(2, state.movingItems.size)
        assertTrue(state.movingItems.all { it.progress == 1f })
    }

    @Test // как только голова колонны уезжает, следующий предмет трогается
    fun queueResumesWhenHeadLeaves() {
        val grid = beltRow(4)
        var state = stateWith(
            grid,
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 1f),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0), progress = 1f),
        )

        state = FactorySimulation.step(state, travel)

        val head = state.movingItems.first { it.from == GridPosition(2, 0) }
        val follower = state.movingItems.first { it.from == GridPosition(1, 0) }
        assertEquals(GridPosition(3, 0), head.to)
        assertEquals(GridPosition(2, 0), follower.to)
    }

    @Test // на одной клетке ленты не может оказаться два предмета
    fun onlyOneItemPerBeltCell() {
        var state = stateWith(
            beltRow(5),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0)),
            MovingItem(ItemId.COPPER_ORE.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 0.5f),
        )

        repeat(20) { state = FactorySimulation.step(state, travel / 4) }

        val targets = state.movingItems.map(MovingItem::to)
        assertEquals("Клетки должны быть уникальны: $targets", targets.size, targets.toSet().size)
    }

    @Test // в конце ленты предмет останавливается, а не улетает за поле
    fun itemStopsAtDeadEnd() {
        val state = stateWith(
            FactoryGrid.EMPTY.withBelt(GridPosition(9, 0), Direction.RIGHT),
            MovingItem(ItemId.GEAR.key, 1, GridPosition(8, 0), GridPosition(9, 0), progress = 0.5f),
        )

        val after = FactorySimulation.step(state, travel)
        val item = after.movingItems.single()

        assertEquals(GridPosition(9, 0), item.to)
        assertEquals(1f, item.progress, 0.001f)
    }

    @Test // огромная дельта не телепортирует предмет через занятые клетки
    fun hugeDeltaIsClamped() {
        val state = stateWith(
            beltRow(6),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0)),
        )

        val after = FactorySimulation.step(state, deltaMillis = 60_000L)
        val item = after.movingItems.single()

        // За один такт предмет проходит не больше одной клетки.
        assertEquals(GridPosition(2, 0), item.to)
    }

    @Test // предмет, доехавший до экспортёра, продаётся по каталожной цене
    fun exporterConvertsItemsToCoins() {
        val exporter = Machine(id = 1, type = com.example.assemblylinetycoon.domain.model.MachineType.EXPORTER, position = GridPosition(2, 0))
        val grid = FactoryGrid.EMPTY
            .withBelt(GridPosition(1, 0), Direction.RIGHT)
            .withMachine(exporter)

        val state = GameState.EMPTY.copy(
            grid = grid,
            machines = mapOf(1 to exporter),
            movingItems = listOf(
                MovingItem(ItemId.GEAR.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 0.9f),
            ),
        )

        val after = FactorySimulation.step(state, travel)

        assertTrue("Предмет должен исчезнуть с ленты", after.movingItems.isEmpty())
        assertEquals(30L, after.coins)                 // цена шестерни из каталога
        assertEquals(1L, after.stats.itemsExported)
        assertEquals(30L, after.stats.coinsEarned)
    }

    @Test // клетка перед экспортёром освобождается для следующего предмета
    fun exporterFreesCellForNextItem() {
        val exporter = Machine(id = 1, type = com.example.assemblylinetycoon.domain.model.MachineType.EXPORTER, position = GridPosition(2, 0))
        val grid = FactoryGrid.EMPTY
            .withBelt(GridPosition(0, 0), Direction.RIGHT)
            .withBelt(GridPosition(1, 0), Direction.RIGHT)
            .withMachine(exporter)

        var state = GameState.EMPTY.copy(
            grid = grid,
            machines = mapOf(1 to exporter),
            movingItems = listOf(
                MovingItem(ItemId.GEAR.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 1f),
                MovingItem(ItemId.GEAR.key, 1, GridPosition(0, 0), GridPosition(1, 0), progress = 1f),
            ),
        )

        state = FactorySimulation.step(state, travel)

        assertEquals(30L, state.coins)
        assertEquals(GridPosition(2, 0), state.movingItems.single().to)
    }

    @Test // предмет с исчезнувшей клеткой не зависает в пустоте
    fun itemOnRemovedCellDisappears() {
        val state = stateWith(
            FactoryGrid.EMPTY,       // лент нет вовсе
            MovingItem(ItemId.GEAR.key, 1, GridPosition(0, 0), GridPosition(1, 0), progress = 1f),
        )

        val after = FactorySimulation.step(state, travel)

        assertEquals(1, after.movingItems.size) // клетка есть, но пустая — предмет ждёт
        assertEquals(1f, after.movingItems.single().progress, 0.001f)
    }

    @Test // занятость клетки видна через состояние
    fun stateReportsCellOccupancy() {
        val state = stateWith(
            beltRow(3),
            MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0)),
        )

        assertNotNull(state.itemMovingTo(GridPosition(1, 0)))
        assertNull(state.itemMovingTo(GridPosition(2, 0)))
        assertTrue(state.isCellFree(GridPosition(2, 0)))
    }

    @Test // тип ячейки отражает роль машины
    fun cellTypeMatchesMachineRole() {
        val spawner = Machine(id = 1, type = com.example.assemblylinetycoon.domain.model.MachineType.SPAWNER, position = GridPosition(0, 0))
        val smelter = Machine(id = 2, type = com.example.assemblylinetycoon.domain.model.MachineType.SMELTER, position = GridPosition(1, 0))
        val exporter = Machine(id = 3, type = com.example.assemblylinetycoon.domain.model.MachineType.EXPORTER, position = GridPosition(2, 0))

        val grid = FactoryGrid.EMPTY.withMachine(spawner).withMachine(smelter).withMachine(exporter)

        assertEquals(CellType.SPAWNER, grid[GridPosition(0, 0)]!!.type)
        assertEquals(CellType.MACHINE, grid[GridPosition(1, 0)]!!.type)
        assertEquals(CellType.EXPORTER, grid[GridPosition(2, 0)]!!.type)
        assertEquals(Cell.EMPTY_CELL, grid[GridPosition(5, 5)])
    }
}
