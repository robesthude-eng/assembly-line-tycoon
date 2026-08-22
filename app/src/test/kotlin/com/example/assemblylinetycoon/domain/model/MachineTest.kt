package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Машина: типы, фазы такта, буферы и неизменяемость. */
class MachineTest {

    @Test // все типы машин из проекта объявлены
    fun everyDesignMachineTypeExists() {
        val required = listOf(
            MachineType.SPAWNER,
            MachineType.SMELTER,
            MachineType.PRESS,
            MachineType.ASSEMBLER,
            MachineType.QUALITY_GATE,
            MachineType.EXPORTER,
        )
        assertTrue(MachineType.entries.containsAll(required))
    }

    @Test // источник и сток отмечены явно, а не угадываются по имени
    fun sourceAndSinkAreMarked() {
        assertTrue(MachineType.SPAWNER.isSource)
        assertTrue(MachineType.EXPORTER.isSink)
        assertFalse(MachineType.ASSEMBLER.isSource)
        assertFalse(MachineType.ASSEMBLER.isSink)
    }

    @Test // фазы такта заданы тремя состояниями
    fun machineHasThreeStatuses() {
        assertEquals(3, MachineStatus.entries.size)
        assertTrue(MachineStatus.entries.containsAll(
            listOf(MachineStatus.IDLE, MachineStatus.CRAFTING, MachineStatus.OUTPUT_EJECT),
        ))
    }

    @Test // новая машина простаивает с пустыми буферами
    fun newMachineStartsIdle() {
        val machine = Machine(id = 1, type = MachineType.SMELTER, position = GridPosition(2, 3))

        assertEquals(MachineStatus.IDLE, machine.status)
        assertEquals(0, machine.level)
        assertTrue(machine.inputBuffer.isEmpty())
        assertTrue(machine.outputBuffer.isEmpty())
        assertEquals(0L, machine.elapsedMillis)
    }

    @Test // выход машины лежит в соседней ячейке по направлению взгляда
    fun outputPositionFollowsFacing() {
        val machine = Machine(
            id = 1,
            type = MachineType.PRESS,
            position = GridPosition(4, 4),
            facing = Direction.DOWN,
        )
        assertEquals(GridPosition(4, 5), machine.outputPosition)
        assertEquals(GridPosition(3, 4), machine.copy(facing = Direction.LEFT).outputPosition)
    }

    @Test // машина неизменяемая: copy не трогает исходный объект
    fun machineIsImmutable() {
        val original = Machine(id = 7, type = MachineType.ASSEMBLER, position = GridPosition.ORIGIN)
        val working = original.copy(status = MachineStatus.CRAFTING, elapsedMillis = 500L)

        assertEquals(MachineStatus.IDLE, original.status)
        assertEquals(0L, original.elapsedMillis)
        assertEquals(MachineStatus.CRAFTING, working.status)
        assertEquals(7, working.id)
    }

    @Test // буфер машины совместим с проверкой рецепта по строковым ключам
    fun bufferWorksWithRecipeCheck() {
        val recipe = Recipe.of(
            output = ItemId.IRON_INGOT,
            inputs = mapOf(ItemId.IRON_ORE to 2),
            baseDurationMillis = 4_000L,
            machineType = MachineType.SMELTER,
        )
        val machine = Machine(
            id = 1,
            type = MachineType.SMELTER,
            position = GridPosition.ORIGIN,
            recipeOutputId = recipe.outputItemId,
            inputBuffer = mapOf(ItemId.IRON_ORE.key to 3),
        )

        assertTrue(recipe.canCraftFrom(machine.inputBuffer))
    }

    @Test // цена следующего уровня выше текущей базовой
    fun upgradeCostGrowsWithLevel() {
        val machine = Machine(id = 1, type = MachineType.SMELTER, position = GridPosition.ORIGIN)
        val upgraded = machine.copy(level = 5)

        assertTrue(machine.nextUpgradeCost() > MachineType.SMELTER.baseCost)
        assertTrue(upgraded.nextUpgradeCost() > machine.nextUpgradeCost())
    }

    @Test // длительность такта уменьшается с уровнем машины
    fun craftDurationShrinksWithLevel() {
        val base = 8_000L
        val atZero = MachineCatalog.craftDuration(base, level = 0)
        val atTen = MachineCatalog.craftDuration(base, level = 10)

        assertEquals(base, atZero)
        assertTrue(atTen < atZero)
    }

    @Test // за пределами поля машину не поставить
    fun placementIsBoundsChecked() {
        assertTrue(Machine.isPlaceable(GridPosition(0, 0)))
        assertFalse(Machine.isPlaceable(GridPosition(-1, 0)))
        assertFalse(Machine.isPlaceable(GridPosition(99, 99)))
    }
}
