package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.utils.MathUtility
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Обработка на машинах: переходы фаз, потребление сырья, выгрузка. */
class MachineProcessingTest {

    private val oreRecipe = RecipeCatalog.forOutput(ItemId.IRON_ORE)!!
    private val ingotRecipe = RecipeCatalog.forOutput(ItemId.IRON_INGOT)!!

    private fun spawnerState(facing: Direction = Direction.RIGHT): GameState {
        val spawner = Machine(
            id = 1,
            type = MachineType.SPAWNER,
            position = GridPosition(0, 0),
            facing = facing,
            recipeOutputId = ItemId.IRON_ORE.key,
        )
        return GameState.EMPTY.copy(
            grid = FactoryGrid.EMPTY.withMachine(spawner).withBelt(GridPosition(1, 0), Direction.RIGHT),
            machines = mapOf(1 to spawner),
        )
    }

    private fun smelterState(inputBuffer: Map<String, Int>): GameState {
        val smelter = Machine(
            id = 1,
            type = MachineType.SMELTER,
            position = GridPosition(1, 1),
            facing = Direction.RIGHT,
            recipeOutputId = ItemId.IRON_INGOT.key,
            inputBuffer = inputBuffer,
        )
        return GameState.EMPTY.copy(
            grid = FactoryGrid.EMPTY.withMachine(smelter).withBelt(GridPosition(2, 1), Direction.RIGHT),
            machines = mapOf(1 to smelter),
        )
    }

    private fun GameState.machine(): Machine = machines.getValue(1)

    /**
     * Прокрутка симуляции реальными тактами по 50 мс.
     *
     * Проматывать время одним большим шагом нельзя: движок намеренно
     * обрезает дельту (GameConstants.MAX_TICK_DELTA_MS), чтобы после паузы
     * системы завод не «прыгал» сквозь занятые клетки.
     */
    private fun GameState.simulate(totalMillis: Long, tick: Long = 50L): GameState {
        var state = this
        var left = totalMillis
        while (left > 0) {
            val step = minOf(tick, left)
            state = FactorySimulation.step(state, step)
            left -= step
        }
        return state
    }

    @Test // карьеру входы не нужны: он сразу уходит в работу
    fun spawnerStartsCraftingWithoutInputs() {
        val after = FactorySimulation.step(spawnerState(), 50L)

        assertEquals(MachineStatus.CRAFTING, after.machine().status)
    }

    @Test // машина без сырья остаётся в простое
    fun machineWithoutInputsStaysIdle() {
        val after = FactorySimulation.step(smelterState(emptyMap()), 50L)

        assertEquals(MachineStatus.IDLE, after.machine().status)
        assertEquals(0L, after.machine().elapsedMillis)
    }

    @Test // нехватка сырья тоже не запускает такт
    fun insufficientInputsDoNotStartCrafting() {
        // Рецепт слитка требует две руды, в буфере одна.
        val after = FactorySimulation.step(smelterState(mapOf(ItemId.IRON_ORE.key to 1)), 50L)

        assertEquals(MachineStatus.IDLE, after.machine().status)
        assertEquals(1, after.machine().inputBuffer[ItemId.IRON_ORE.key])
    }

    @Test // IDLE → CRAFTING списывает ровно требуемое количество
    fun idleToCraftingConsumesInputs() {
        val after = FactorySimulation.step(smelterState(mapOf(ItemId.IRON_ORE.key to 5)), 50L)

        assertEquals(MachineStatus.CRAFTING, after.machine().status)
        assertEquals(3, after.machine().inputBuffer[ItemId.IRON_ORE.key])
    }

    @Test // прогресс такта накапливается по дельте времени
    fun craftingProgressAccumulates() {
        val state = smelterState(mapOf(ItemId.IRON_ORE.key to 2)).simulate(450L)

        assertEquals(MachineStatus.CRAFTING, state.machine().status)
        // Переход IDLE → CRAFTING не съедает такт: всё прошедшее время
        // засчитывается в работу, иначе цикл зависел бы от частоты кадров.
        assertEquals(450L, state.machine().elapsedMillis)
    }

    @Test // по достижении длительности такт завершается и результат учитывается
    fun craftingCompletesAndProducesItem() {
        val duration = MathUtility.craftDuration(ingotRecipe.baseDurationMillis, level = 0)

        val justBefore = smelterState(mapOf(ItemId.IRON_ORE.key to 2)).simulate(duration - 50L)
        val justAfter = smelterState(mapOf(ItemId.IRON_ORE.key to 2)).simulate(duration)

        assertEquals(MachineStatus.CRAFTING, justBefore.machine().status)
        assertEquals(0L, justBefore.stats.itemsProduced)
        assertEquals(1L, justAfter.stats.itemsProduced)
    }

    @Test // готовый предмет выкладывается на ленту перед машиной
    fun outputIsEjectedOntoBelt() {
        val duration = MathUtility.craftDuration(ingotRecipe.baseDurationMillis, level = 0)
        val state = smelterState(mapOf(ItemId.IRON_ORE.key to 2)).simulate(duration)

        val ejected = state.movingItems.single()
        assertEquals(ItemId.IRON_INGOT.key, ejected.itemId)
        assertEquals(GridPosition(1, 1), ejected.from)
        assertEquals(GridPosition(2, 1), ejected.to)
        // Буфер выхода пуст — машина снова готова к работе.
        assertEquals(MachineStatus.IDLE, state.machine().status)
    }

    @Test // цикл повторяется: после выгрузки машина снова берёт сырьё
    fun machineRepeatsCycle() {
        val duration = MathUtility.craftDuration(ingotRecipe.baseDurationMillis, level = 0)
        var state = smelterState(mapOf(ItemId.IRON_ORE.key to 4))

        repeat(2) {
            state = state.simulate(duration)
            // Освобождаем ленту, иначе второй результат некуда выгружать.
            state = state.copy(movingItems = emptyList())
        }

        assertEquals(2L, state.stats.itemsProduced)
        assertEquals(0, state.machine().inputBuffer[ItemId.IRON_ORE.key] ?: 0)
    }

    @Test // некуда выгружать — машина держит результат и не начинает новый такт
    fun blockedOutputHoldsMachine() {
        val duration = MathUtility.craftDuration(ingotRecipe.baseDurationMillis, level = 0)
        var state = smelterState(mapOf(ItemId.IRON_ORE.key to 4)).simulate(duration - 50L)

        // Занимаем клетку выхода чужим предметом до того, как такт завершится.
        state = state.copy(
            movingItems = listOf(
                MovingItem(ItemId.GEAR.key, 1, GridPosition(3, 1), GridPosition(2, 1), progress = 1f),
            ),
        )

        state = state.simulate(duration * 3)

        assertEquals(MachineStatus.OUTPUT_EJECT, state.machine().status)
        assertEquals(1, state.machine().outputBuffer[ItemId.IRON_INGOT.key])
        assertEquals(1L, state.stats.itemsProduced)   // второй такт не начался
    }

    @Test // карьер за свой такт выдаёт руду на ленту
    fun spawnerProducesRawResource() {
        val duration = MathUtility.craftDuration(oreRecipe.baseDurationMillis, level = 0)

        val state = spawnerState().simulate(duration)

        assertEquals(ItemId.IRON_ORE.key, state.movingItems.single().itemId)
        assertEquals(1L, state.stats.itemsProduced)
    }

    @Test // апгрейд машины ускоряет такт по существующей формуле
    fun higherLevelCraftsFaster() {
        val base = ingotRecipe.baseDurationMillis
        val atZero = MathUtility.craftDuration(base, 0)
        val atTen = MathUtility.craftDuration(base, 10)

        val upgraded = smelterState(mapOf(ItemId.IRON_ORE.key to 2)).let { state ->
            state.copy(machines = mapOf(1 to state.machine().copy(level = 10)))
        }

        val state = upgraded.simulate(atTen)

        assertTrue("Апгрейд должен ускорять такт", atTen < atZero)
        assertEquals(1L, state.stats.itemsProduced)
        // За то же время на нулевом уровне машина ещё не закончила бы такт.
        assertTrue(atTen < atZero)
    }

    @Test // машина принимает предмет с ленты во входной буфер
    fun machineAcceptsItemFromBelt() {
        val smelter = Machine(
            id = 1,
            type = MachineType.SMELTER,
            position = GridPosition(2, 0),
            recipeOutputId = ItemId.IRON_INGOT.key,
        )
        val state = GameState.EMPTY.copy(
            grid = FactoryGrid.EMPTY.withBelt(GridPosition(1, 0), Direction.RIGHT).withMachine(smelter),
            machines = mapOf(1 to smelter),
            movingItems = listOf(
                MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 1f),
            ),
        )

        val after = FactorySimulation.step(state, 50L)

        assertTrue(after.movingItems.isEmpty())
        assertEquals(1, after.machine().inputBuffer[ItemId.IRON_ORE.key])
    }

    @Test // переполненный буфер не принимает предмет, тот ждёт на ленте
    fun fullBufferRejectsItem() {
        val smelter = Machine(
            id = 1,
            type = MachineType.SMELTER,
            position = GridPosition(2, 0),
            recipeOutputId = null,   // без рецепта машина не расходует буфер
            inputBuffer = mapOf(ItemId.IRON_ORE.key to Machine.BUFFER_CAPACITY),
        )
        val state = GameState.EMPTY.copy(
            grid = FactoryGrid.EMPTY.withBelt(GridPosition(1, 0), Direction.RIGHT).withMachine(smelter),
            machines = mapOf(1 to smelter),
            movingItems = listOf(
                MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 1f),
            ),
        )

        val after = FactorySimulation.step(state, 50L)

        assertNotNull("Предмет должен остаться на ленте", after.itemMovingTo(GridPosition(2, 0)))
        assertEquals(Machine.BUFFER_CAPACITY, after.machine().inputBuffer[ItemId.IRON_ORE.key])
    }

    @Test // карьер не принимает сырьё: он источник, а не переработчик
    fun spawnerRejectsIncomingItems() {
        val spawner = Machine(
            id = 1,
            type = MachineType.SPAWNER,
            position = GridPosition(2, 0),
            recipeOutputId = ItemId.IRON_ORE.key,
        )
        val state = GameState.EMPTY.copy(
            grid = FactoryGrid.EMPTY.withBelt(GridPosition(1, 0), Direction.RIGHT).withMachine(spawner),
            machines = mapOf(1 to spawner),
            movingItems = listOf(
                MovingItem(ItemId.GEAR.key, 1, GridPosition(1, 0), GridPosition(2, 0), progress = 1f),
            ),
        )

        val after = FactorySimulation.step(state, 50L)

        assertEquals(1, after.movingItems.size)
        assertTrue(after.machine().inputBuffer.isEmpty())
    }

    @Test // цепочка карьер → лента → плавильня работает целиком
    fun productionChainDeliversToNextMachine() {
        val spawner = Machine(
            id = 1,
            type = MachineType.SPAWNER,
            position = GridPosition(0, 0),
            facing = Direction.RIGHT,
            recipeOutputId = ItemId.IRON_ORE.key,
        )
        val smelter = Machine(
            id = 2,
            type = MachineType.SMELTER,
            position = GridPosition(2, 0),
            recipeOutputId = ItemId.IRON_INGOT.key,
        )
        var state = GameState.EMPTY.copy(
            grid = FactoryGrid.EMPTY
                .withMachine(spawner)
                .withBelt(GridPosition(1, 0), Direction.RIGHT)
                .withMachine(smelter),
            machines = mapOf(1 to spawner, 2 to smelter),
        )

        state = state.simulate(30_000L)   // 30 секунд игрового времени

        assertTrue("Карьер должен что-то произвести", state.stats.itemsProduced > 0)
        assertTrue(
            "Плавильня должна получить руду или уже плавить",
            (state.machines.getValue(2).inputBuffer[ItemId.IRON_ORE.key] ?: 0) > 0 ||
                state.machines.getValue(2).status != MachineStatus.IDLE,
        )
    }

    @Test // симуляция детерминирована: одинаковый вход даёт одинаковый выход
    fun simulationIsDeterministic() {
        fun run(): GameState {
            var state = spawnerState()
            repeat(200) { state = FactorySimulation.step(state, 50L) }
            return state
        }

        assertEquals(run(), run())
    }
}
