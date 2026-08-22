package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.core.constants.GameConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Поведение сетки: адресация, границы, неизменяемость, противодавление. */
class FactoryGridTest {

    @Test // индекс и координата переводятся друг в друга без потерь
    fun indexRoundTripsThroughPosition() {
        for (index in 0 until GameConstants.GRID_WIDTH * GameConstants.GRID_HEIGHT) {
            assertEquals(index, GridPosition.fromIndex(index).toIndex())
        }
    }

    @Test // обращение за границей поля возвращает null, а не падает
    fun outOfBoundsAccessReturnsNull() {
        val grid = FactoryGrid.EMPTY
        assertNull(grid[GridPosition(-1, 0)])
        assertNull(grid[GridPosition(0, GameConstants.GRID_HEIGHT)])
        assertFalse(GridPosition(-1, 5).isInside())
    }

    @Test // withCell возвращает копию и не трогает исходное поле
    fun withCellDoesNotMutateSource() {
        val original = FactoryGrid.EMPTY
        val position = GridPosition(3, 4)
        val updated = original.withCell(position, Cell(type = CellType.BELT))

        assertEquals(CellType.EMPTY, original[position]!!.type)
        assertEquals(CellType.BELT, updated[position]!!.type)
    }

    @Test // запись за пределами поля игнорируется
    fun writingOutsideGridIsIgnored() {
        val grid = FactoryGrid.EMPTY
        val updated = grid.withCell(GridPosition(99, 99), Cell(type = CellType.MACHINE))
        assertEquals(grid, updated)
    }

    @Test // предмет на конце ячейки считается заблокированным
    fun cellReportsBackpressure() {
        val moving = Cell(type = CellType.BELT, item = ItemId.IRON_ORE, itemProgress = 0.4f)
        val stuck = Cell(type = CellType.BELT, item = ItemId.IRON_ORE, itemProgress = 1f)

        assertTrue(moving.isOccupied)
        assertFalse(moving.isBlocked)
        assertTrue(stuck.isBlocked)
    }

    @Test // соседняя ячейка вычисляется по направлению
    fun neighborFollowsDirection() {
        val center = GridPosition(5, 5)
        assertEquals(GridPosition(5, 4), center.neighbor(Direction.UP))
        assertEquals(GridPosition(6, 5), center.neighbor(Direction.RIGHT))
        assertEquals(GridPosition(5, 6), center.neighbor(Direction.DOWN))
        assertEquals(GridPosition(4, 5), center.neighbor(Direction.LEFT))
    }

    @Test // четыре поворота по часовой стрелке возвращают исходное направление
    fun fourRotationsReturnToStart() {
        Direction.entries.forEach { start ->
            val result = start.rotateClockwise().rotateClockwise().rotateClockwise().rotateClockwise()
            assertEquals(start, result)
            assertEquals(start, start.rotateClockwise().rotateCounterClockwise())
            assertEquals(start, start.opposite().opposite())
        }
    }

    @Test // рендерер получает только непустые ячейки
    fun occupiedCellsSkipEmptyOnes() {
        val grid = FactoryGrid.EMPTY
            .withCell(GridPosition(0, 0), Cell(type = CellType.BELT))
            .withCell(GridPosition(1, 1), Cell(type = CellType.MACHINE, machineId = 7))

        val occupied = grid.occupiedCells()
        assertEquals(2, occupied.size)
        assertEquals(GridPosition(0, 0), occupied.first().first)
    }

    @Test // состояние знает, сколько машин каждого типа построено
    fun gameStateCountsMachinesByType() {
        val state = GameState.EMPTY.copy(
            machines = mapOf(
                1 to Machine(id = 1, type = MachineType.SMELTER, position = GridPosition(0, 0)),
                2 to Machine(id = 2, type = MachineType.SMELTER, position = GridPosition(1, 0)),
                3 to Machine(id = 3, type = MachineType.EXPORTER, position = GridPosition(2, 0)),
            ),
            nextMachineId = 4,
        )

        assertEquals(2, state.machineCount(MachineType.SMELTER))
        assertEquals(1, state.machineCount(MachineType.EXPORTER))
        assertEquals(0, state.machineCount(MachineType.ASSEMBLER))
        assertEquals(3, state.machineAt(GridPosition(2, 0))?.id)
    }

    @Test // «Ускорение» активно только до отметки времени
    fun overdriveExpiresAtDeadline() {
        val state = GameState.EMPTY.copy(overdriveUntilMillis = 10_000L)
        assertTrue(state.isOverdriveActive(nowMillis = 9_999L))
        assertFalse(state.isOverdriveActive(nowMillis = 10_000L))
    }
}
