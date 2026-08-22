package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.domain.catalog.BeltCatalog
import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.CellType
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
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

    // ── Конвейер ────────────────────────────────────────────────────────────

    @Test // первая лента стоит базовую цену каталога
    fun firstBeltCostsBasePrice() {
        assertEquals(BeltCatalog.BASE_COST, FactoryBuilder.beltCost(richState))
    }

    @Test // прокладка списывает деньги и ставит ленту нужным направлением
    fun placingBeltChargesAndSetsDirection() {
        val cost = FactoryBuilder.beltCost(richState)

        val after = FactoryBuilder.placeBelt(richState, cell, Direction.DOWN)

        assertEquals(richState.coins - cost, after.coins)
        assertEquals(CellType.BELT, after.grid[cell]!!.type)
        assertEquals(Direction.DOWN, after.grid[cell]!!.direction)
    }

    @Test // цена ленты растёт ступенями и никогда не падает
    fun beltCostNeverFallsAndGrowsOverTheField() {
        val costs = (0..89).map { BeltCatalog.buildCost(ownedCount = it) }

        // Надбавка в 3 % от 10 монет меньше единицы, а цена усекается вниз,
        // поэтому соседние отрезки часто стоят одинаково — но не дешевле.
        costs.zipWithNext().forEach { (previous, next) ->
            assertTrue("Цена ленты не должна падать: $previous → $next", next >= previous)
        }
        assertTrue("За поле цена обязана заметно вырасти", costs.last() > costs.first() * 5)
    }

    @Test // проложенные ленты учитываются в цене следующей
    fun beltCostCountsPlacedSegments() {
        val afterOne = FactoryBuilder.placeBelt(richState, cell, Direction.RIGHT)

        assertEquals(1, FactoryBuilder.beltCount(afterOne))
        assertEquals(BeltCatalog.buildCost(ownedCount = 1), FactoryBuilder.beltCost(afterOne))
    }

    @Test // лента дешевле самой дешёвой машины: соединять станки не должно быть дороже, чем строить их
    fun beltIsCheaperThanAnyMachine() {
        val cheapestMachine = MachineType.entries.minOf { it.baseCost }

        assertTrue(BeltCatalog.BASE_COST < cheapestMachine)
    }

    @Test // цена ленты растёт мягче машинной: полем 10x10 её не разорить
    fun beltCostStaysReasonableOnFullField() {
        // 90 лент — почти всё поле, свободное от машин.
        val ninetieth = BeltCatalog.buildCost(ownedCount = 89)

        assertTrue("Девяностая лента не должна стоить как завод: $ninetieth", ninetieth < 1_000L)
    }

    @Test // без денег ленту не проложить
    fun placingBeltWithoutCoinsIsRejected() {
        val poor = GameState.EMPTY.copy(coins = 0L)

        assertSame(poor, FactoryBuilder.placeBelt(poor, cell, Direction.RIGHT))
    }

    @Test // в занятую ячейку ленту не проложить
    fun placingBeltOnOccupiedCellIsRejected() {
        val withMachine = FactoryBuilder.place(richState, cell, MachineType.SMELTER)

        assertSame(withMachine, FactoryBuilder.placeBelt(withMachine, cell, Direction.RIGHT))
    }

    @Test // поворот ленты бесплатен
    fun rotatingBeltIsFree() {
        val withBelt = FactoryBuilder.placeBelt(richState, cell, Direction.RIGHT)

        val rotated = FactoryBuilder.rotateBelt(withBelt, cell, Direction.UP)

        assertEquals(withBelt.coins, rotated.coins)
        assertEquals(Direction.UP, rotated.grid[cell]!!.direction)
        assertEquals(0L, BeltCatalog.ROTATE_COST)
    }

    @Test // станок разворачивается теми же кнопками, что и лента
    fun rotatingMachineChangesOutputSide() {
        val withMachine = FactoryBuilder.place(richState, cell, MachineType.SMELTER)

        val rotated = FactoryBuilder.rotate(withMachine, cell, Direction.UP)

        assertEquals(Direction.UP, rotated.machineAt(cell)!!.facing)
        assertEquals(cell.neighbor(Direction.UP), rotated.machineAt(cell)!!.outputPosition)
        assertEquals("Разворот станка бесплатен", withMachine.coins, rotated.coins)
        assertTrue(FactoryBuilder.canRotate(rotated, cell))
    }

    @Test // разворачивать нечего в пустой ячейке
    fun rotatingEmptyCellIsIgnored() {
        assertTrue(!FactoryBuilder.canRotate(richState, cell))
        assertSame(richState, FactoryBuilder.rotate(richState, cell, Direction.UP))
    }

    @Test // станок у края поля выдаёт продукцию внутрь, а не за границу
    fun machineAtTheEdgeFacesInsideTheField() {
        val edge = GridPosition(richState.grid.width - 1, 4)

        val built = FactoryBuilder.place(richState, edge, MachineType.SPAWNER)

        val output = built.machineAt(edge)!!.outputPosition
        assertTrue("Выход $output вне поля", built.grid.contains(output))
    }

    @Test // если рядом уже есть лента, станок сразу смотрит на неё
    fun newMachineFacesNeighbouringBelt() {
        val beltCell = cell.neighbor(Direction.DOWN)
        val withBelt = FactoryBuilder.placeBelt(richState, beltCell, Direction.RIGHT)

        val built = FactoryBuilder.place(withBelt, cell, MachineType.SMELTER)

        assertEquals(Direction.DOWN, built.machineAt(cell)!!.facing)
    }

    // ── Снос ────────────────────────────────────────────────────────────────

    @Test // снос ленты освобождает ячейку и возвращает половину цены
    fun demolishingBeltFreesCellAndRefundsHalf() {
        val withBelt = FactoryBuilder.placeBelt(richState, cell, Direction.RIGHT)
        val refund = FactoryBuilder.refundFor(withBelt, cell)

        val cleared = FactoryBuilder.demolish(withBelt, cell)

        assertTrue(cleared.grid[cell]!!.isEmpty)
        assertEquals(withBelt.coins + refund, cleared.coins)
        assertEquals((BeltCatalog.buildCost(ownedCount = 0) * FactoryBuilder.REFUND_RATE).toLong(), refund)
        assertTrue(FactoryBuilder.isBuildable(cleared, cell))
    }

    @Test // за снос станка возвращается половина его текущей цены
    fun demolishingMachineRefundsHalfOfItsCost() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val expected =
            (MachineCatalog.buildCost(MachineType.SMELTER, ownedCount = 0) * FactoryBuilder.REFUND_RATE).toLong()

        val cleared = FactoryBuilder.demolish(built, cell)

        assertEquals(expected, FactoryBuilder.refundFor(built, cell))
        assertEquals(built.coins + expected, cleared.coins)
        // Половина, а не всё: перестановка станка должна что-то стоить.
        assertTrue(cleared.coins < richState.coins)
    }

    @Test // улучшения при сносе не возвращаются
    fun demolishingDoesNotRefundUpgrades() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val id = built.machineAt(cell)!!.id
        val upgraded = FactoryBuilder.upgrade(built, id)

        assertEquals(FactoryBuilder.refundFor(built, cell), FactoryBuilder.refundFor(upgraded, cell))
    }

    @Test // снос машины убирает её и из списка, и с поля
    fun demolishingMachineRemovesItEverywhere() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val id = built.machineAt(cell)!!.id

        val cleared = FactoryBuilder.demolish(built, cell)

        assertTrue(cleared.machines.isEmpty())
        assertNull(cleared.machines[id])
        assertTrue(cleared.grid[cell]!!.isEmpty)
    }

    @Test // после сноса цена такой же машины снова базовая
    fun demolishingRestoresBuildCost() {
        val built = FactoryBuilder.place(richState, cell, MachineType.SMELTER)
        val cleared = FactoryBuilder.demolish(built, cell)

        assertEquals(
            MachineCatalog.buildCost(MachineType.SMELTER, ownedCount = 0),
            FactoryBuilder.buildCost(cleared, MachineType.SMELTER),
        )
    }

    @Test // предметы на снесённой ленте исчезают вместе с ней
    fun demolishingDropsItemsOnTheCell() {
        val withBelt = FactoryBuilder.placeBelt(richState, cell, Direction.RIGHT)
        val neighbour = GridPosition(cell.x + 1, cell.y)
        val busy = withBelt.copy(
            movingItems = listOf(
                MovingItem(ItemId.IRON_ORE.key, 1, cell, neighbour, 0.3f),
                MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(1, 0), 0.2f),
            ),
        )

        val cleared = FactoryBuilder.demolish(busy, cell)

        // Остался только предмет, не связанный со снесённой клеткой: иначе он
        // завис бы на несуществующей ленте и заблокировал соседей.
        assertEquals(1, cleared.movingItems.size)
        assertEquals(GridPosition(0, 0), cleared.movingItems.single().from)
    }

    @Test // сносить пустую ячейку нечего
    fun demolishingEmptyCellIsIgnored() {
        assertTrue(!FactoryBuilder.canDemolish(richState, cell))
        assertSame(richState, FactoryBuilder.demolish(richState, cell))
    }

    @Test // стартового капитала хватает на минимальную работающую линию
    fun startingCoinsCoverMinimalProductionLine() {
        var state = GameState.NEW_GAME

        // Карьер → четыре ленты → экспортёр: самая короткая цепочка,
        // которая уже приносит деньги.
        state = FactoryBuilder.place(state, GridPosition(1, 1), MachineType.SPAWNER)
        (2..5).forEach { x ->
            state = FactoryBuilder.placeBelt(state, GridPosition(x, 1), Direction.RIGHT)
        }
        state = FactoryBuilder.place(state, GridPosition(6, 1), MachineType.EXPORTER)

        assertEquals("Карьер должен построиться", MachineType.SPAWNER, state.machineAt(GridPosition(1, 1))?.type)
        assertEquals("Экспортёр должен построиться", MachineType.EXPORTER, state.machineAt(GridPosition(6, 1))?.type)
        assertEquals(4, FactoryBuilder.beltCount(state))
        assertTrue("Баланс не может уйти в минус", state.coins >= 0L)
    }

    @Test // новая игра не начинается с нуля: иначе первый экран — тупик
    fun newGameHasStartingCapital() {
        assertTrue(GameState.NEW_GAME.coins > 0L)
        assertTrue(
            "Стартовых денег должно хватать хотя бы на карьер",
            GameState.NEW_GAME.coins >= MachineType.SPAWNER.baseCost,
        )
    }

    @Test // магазин предлагает все типы машин, от дешёвых к дорогим
    fun purchasableTypesAreSortedByPrice() {
        val types = FactoryBuilder.purchasableTypes()

        assertEquals(MachineType.entries.size, types.size)
        assertEquals(types.sortedBy { it.baseCost }, types)
    }
}
