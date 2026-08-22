package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.CellType
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.MachineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Постройка и улучшение оборудования.
 *
 * Проверяется именно то, что делает игрока беднее или богаче: списание денег,
 * рост цены и отказы. Цены нигде не задаются числами вручную — тест спрашивает
 * их у того же каталога, что и код, иначе он превратился бы в дубликат баланса
 * и падал бы при каждой правке экономики.
 */
class FactoryBuilderTest {

    private val richState = GameState.EMPTY.copy(coins = 1_000_000L, isInitialized = true)
    private val cell = GridPosition(3, 3)

    @Test // постройка списывает ровно каталожную цену
    fun placingDeductsCatalogCost() {
        val cost = MachineCatalog.buildCost(MachineType.SMELTER, ownedCount = 0)

        val after = FactoryBuilder.place(richState, cell, MachineType.SMELTER)

        assertEquals(richState.coins - cost, after.coins)
        assertEquals(1, after.machines.size)
    }

    @Test // машина появляется и на поле, и в списке машин
    fun placedMachineAppearsOnGrid() {
        val after = FactoryBuilder.place(richState, cell, MachineType.SMELTER)

        val machine = after.machineAt(cell)
        assertNotNull(machine)
        assertEquals(MachineType.SMELTER, machine!!.type)
        assertEquals(CellType.MACHINE, after.grid[cell]!!.type)
        assertEquals(machine.id, after.grid[cell]!!.machineId)
    }

    @Test // роль машины определяет тип ячейки: карьер и экспортёр особые
    fun cellTypeFollowsMachineRole() {
        val spawner = FactoryBuilder.place(richState, cell, MachineType.SPAWNER)
        val exporter = FactoryBuilder.place(richState, cell, MachineType.EXPORTER)

        assertEquals(CellType.SPAWNER, spawner.grid[cell]!!.type)
        assertEquals(CellType.EXPORTER, exporter.grid[cell]!!.type)
    }

    @Test // построенная машина сразу знает, что производить
    fun placedMachineGetsDefaultRecipe() {
        val after = FactoryBuilder.place(richState, cell, MachineType.SMELTER)

        val expected = RecipeCatalog.forMachine(MachineType.SMELTER).first().outputItemId
        assertEquals(expected, after.machineAt(cell)!!.recipeOutputId)
    }

    @Test // каждая следующая машина того же типа дороже предыдущей
    fun costGrowsWithEachCopy() {
        val first = FactoryBuilder.buildCost(richState, MachineType.SMELTER)
        val afterFirst = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val second = FactoryBuilder.buildCost(afterFirst, MachineType.SMELTER)

        assertTrue("Вторая плавильня должна быть дороже первой", second > first)
        assertEquals(MachineCatalog.buildCost(MachineType.SMELTER, ownedCount = 1), second)
    }

    @Test // цена растёт по типам независимо
    fun costGrowthIsPerMachineType() {
        val afterSmelter = FactoryBuilder.place(richState, cell, MachineType.SMELTER)

        assertEquals(
            MachineCatalog.buildCost(MachineType.PRESS, ownedCount = 0),
            FactoryBuilder.buildCost(afterSmelter, MachineType.PRESS),
        )
    }

    @Test // идентификаторы машин только растут
    fun machineIdsAreUnique() {
        val first = FactoryBuilder.place(richState, GridPosition(1, 1), MachineType.SMELTER)
        val second = FactoryBuilder.place(first, GridPosition(2, 1), MachineType.SMELTER)

        assertEquals(setOf(1, 2), second.machines.keys)
        assertEquals(3, second.nextMachineId)
    }

    @Test // без денег постройки не происходит
    fun placingWithoutCoinsIsRejected() {
        val poor = GameState.EMPTY.copy(coins = 0L)

        val after = FactoryBuilder.place(poor, cell, MachineType.SMELTER)

        // Тождество: отказ не должен порождать новое состояние.
        assertSame(poor, after)
    }

    @Test // денег ровно впритык хватает
    fun exactCoinsAreEnough() {
        val cost = MachineCatalog.buildCost(MachineType.SPAWNER, ownedCount = 0)
        val exact = GameState.EMPTY.copy(coins = cost)

        val after = FactoryBuilder.place(exact, cell, MachineType.SPAWNER)

        assertEquals(0L, after.coins)
        assertEquals(1, after.machines.size)
    }

    @Test // в занятую ячейку строить нельзя
    fun occupiedCellIsRejected() {
        val withMachine = FactoryBuilder.place(richState, cell, MachineType.SMELTER)

        val again = FactoryBuilder.place(withMachine, cell, MachineType.PRESS)

        assertSame(withMachine, again)
    }

    @Test // лента тоже занимает ячейку
    fun beltCellIsNotBuildable() {
        val withBelt = richState.copy(grid = richState.grid.withBelt(cell, Direction.RIGHT))

        assertTrue(!FactoryBuilder.isBuildable(withBelt, cell))
        assertSame(withBelt, FactoryBuilder.place(withBelt, cell, MachineType.SMELTER))
    }

    @Test // за пределами поля строить нечего
    fun outsideGridIsRejected() {
        val outside = GridPosition(99, 99)

        assertTrue(!FactoryBuilder.isBuildable(richState, outside))
        assertSame(richState, FactoryBuilder.place(richState, outside, MachineType.SMELTER))
    }

    @Test // улучшение поднимает уровень и списывает цену каталога
    fun upgradeRaisesLevelAndCosts() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val machine = built.machineAt(cell)!!
        val cost = machine.nextUpgradeCost()

        val upgraded = FactoryBuilder.upgrade(built, machine.id)

        assertEquals(1, upgraded.machines.getValue(machine.id).level)
        assertEquals(built.coins - cost, upgraded.coins)
    }

    @Test // улучшение ускоряет такт машины
    fun upgradeShortensCraftDuration() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val machine = built.machineAt(cell)!!
        val recipe = RecipeCatalog.forOutput(machine.recipeOutputId!!)!!

        val upgraded = FactoryBuilder.upgrade(built, machine.id)

        val before = MachineCatalog.craftDuration(recipe.baseDurationMillis, level = 0)
        val after = MachineCatalog.craftDuration(recipe.baseDurationMillis, level = 1)
        assertTrue("Уровень должен сокращать такт", after < before)
        assertEquals(1, upgraded.machines.getValue(machine.id).level)
    }

    @Test // прогресс текущего такта не сбрасывается покупкой
    fun upgradeKeepsCraftingProgress() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val id = built.machineAt(cell)!!.id
        val working = built.copy(
            machines = built.machines + (id to built.machines.getValue(id).copy(elapsedMillis = 700L)),
        )

        val upgraded = FactoryBuilder.upgrade(working, id)

        assertEquals(700L, upgraded.machines.getValue(id).elapsedMillis)
    }

    @Test // каждый следующий уровень дороже предыдущего
    fun upgradeCostGrowsWithLevel() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val id = built.machineAt(cell)!!.id
        val firstCost = FactoryBuilder.upgradeCost(built, id)!!

        val upgraded = FactoryBuilder.upgrade(built, id)

        assertTrue(FactoryBuilder.upgradeCost(upgraded, id)!! > firstCost)
    }

    @Test // без денег уровень не растёт
    fun upgradeWithoutCoinsIsRejected() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val id = built.machineAt(cell)!!.id
        val broke = built.copy(coins = 0L)

        assertTrue(!FactoryBuilder.canUpgrade(broke, id))
        assertSame(broke, FactoryBuilder.upgrade(broke, id))
    }

    @Test // несуществующая машина не ломает состояние
    fun upgradingUnknownMachineIsIgnored() {
        assertNull(FactoryBuilder.upgradeCost(richState, machineId = 42))
        assertSame(richState, FactoryBuilder.upgrade(richState, machineId = 42))
    }

    @Test // магазин предлагает все типы машин, от дешёвых к дорогим
    fun purchasableTypesAreSortedByPrice() {
        val types = FactoryBuilder.purchasableTypes()

        assertEquals(MachineType.entries.size, types.size)
        assertEquals(types.sortedBy { it.baseCost }, types)
    }
}
