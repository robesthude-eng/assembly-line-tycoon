package com.example.assemblylinetycoon.domain

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.core.utils.MathUtility
import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.engine.FactoryBuilder
import com.example.assemblylinetycoon.domain.engine.GameCommand
import com.example.assemblylinetycoon.domain.engine.GameLoop
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Игровой цикл: тикер на 50 мс, дельта времени и передача такта в симуляцию.
 * Реальное время не используется — виртуальный планировщик `runTest`
 * прокручивает часы мгновенно.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameLoopTest {

    private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main = dispatcher
        override val default = dispatcher
        override val io = dispatcher
        override val unconfined = dispatcher
    }

    /** Часы, которыми управляет тест: тикер видит ровно то, что мы задали. */
    private class FakeTimeProvider(var now: Long = 0L) : TimeProvider {
        override fun nowMillis(): Long = now
        override fun elapsedRealtimeMillis(): Long = now
    }

    /**
     * Создаёт движок и гарантированно останавливает его.
     *
     * Без finally провалившийся ассерт оставлял бы тикер живым, и `runTest`
     * бесконечно прокручивал бы виртуальное время вместо падения теста.
     */
    private inline fun withLoop(
        dispatcher: CoroutineDispatcher,
        time: TimeProvider,
        tickIntervalMillis: Long = GameConstants.TICK_INTERVAL_MS,
        block: (GameLoop) -> Unit,
    ) {
        val loop = GameLoop(TestDispatchers(dispatcher), time, tickIntervalMillis)
        try {
            block(loop)
        } finally {
            loop.dispose()
        }
    }

    /** Завод из карьера, ленты и экспортёра — минимальная работающая цепочка. */
    private fun workingFactory(): GameState {
        val spawner = Machine(
            id = 1,
            type = MachineType.SPAWNER,
            position = GridPosition(0, 0),
            facing = Direction.RIGHT,
            recipeOutputId = ItemId.IRON_ORE.key,
        )
        val exporter = Machine(id = 2, type = MachineType.EXPORTER, position = GridPosition(2, 0))
        return GameState.EMPTY.copy(
            isInitialized = true,
            grid = FactoryGrid.EMPTY
                .withMachine(spawner)
                .withBelt(GridPosition(1, 0), Direction.RIGHT)
                .withMachine(exporter),
            machines = mapOf(1 to spawner, 2 to exporter),
        )
    }

    @Test // состояние доступно сразу после старта
    fun stateIsAvailableImmediatelyAfterStart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        withLoop(dispatcher, FakeTimeProvider()) { loop ->
            loop.start(GameState.EMPTY.copy(coins = 100L, isInitialized = true))

            assertEquals(100L, loop.state.value.coins)
            assertTrue(loop.state.value.isInitialized)
        }
    }

    @Test // сброс прогресса возвращает пустое состояние
    fun resetProgressReturnsEmptyState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        withLoop(dispatcher, FakeTimeProvider()) { loop ->
            loop.start(GameState.EMPTY.copy(coins = 5_000L))
            loop.dispatch(GameCommand.ResetProgress)

            // Сброс возвращает не пустоту, а новую игру со стартовым капиталом.
            assertEquals(GameState.NEW_GAME, loop.state.value)
        }
    }

    @Test // тикер срабатывает каждые 50 мс и двигает симуляцию
    fun tickerAdvancesSimulationEvery50Millis() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val time = FakeTimeProvider()

        withLoop(dispatcher, time) { loop ->
            loop.start(workingFactory())

            // Прокручиваем секунду: часы теста и часы движка идут вместе.
            repeat(20) {
                time.now += GameConstants.TICK_INTERVAL_MS
                advanceTimeBy(GameConstants.TICK_INTERVAL_MS)
            }
            runCurrent()

            val state = loop.state.value
            // Планировщик виртуального времени может «придержать» последний
            // тик, поэтому проверяем шаг и порядок величины, а не точное число.
            assertEquals(0L, state.stats.simulatedMillis % GameConstants.TICK_INTERVAL_MS)
            assertTrue(
                "Ожидали около 20 тиков по 50 мс, получили ${state.stats.simulatedMillis} мс",
                state.stats.simulatedMillis >= 900L && state.stats.simulatedMillis <= 1_050L,
            )
            assertEquals(MachineStatus.CRAFTING, state.machines.getValue(1).status)
        }
    }

    @Test // без движения времени состояние не меняется
    fun zeroDeltaLeavesStateUntouched() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        withLoop(dispatcher, FakeTimeProvider()) { loop ->
            val initial = workingFactory()

            val unchanged = loop.reduce(initial, GameCommand.Tick(deltaMillis = 0L))

            assertEquals(initial, unchanged)
        }
    }

    @Test // дельта складывается: два такта по 100 мс равны одному по 200 мс
    fun deltaTimeAccumulatesConsistently() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        withLoop(dispatcher, FakeTimeProvider()) { loop ->
            val initial = workingFactory()

            val twoSteps = loop.reduce(loop.reduce(initial, GameCommand.Tick(100L)), GameCommand.Tick(100L))
            val oneStep = loop.reduce(initial, GameCommand.Tick(200L))

            assertEquals(oneStep.stats.simulatedMillis, twoSteps.stats.simulatedMillis)
            assertEquals(
                oneStep.machines.getValue(1).elapsedMillis,
                twoSteps.machines.getValue(1).elapsedMillis,
            )
        }
    }

    @Test // за полный такт карьера появляется предмет, за цепочку — монеты
    fun fullChainProducesCoins() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val time = FakeTimeProvider()
        val oreDuration = MathUtility.craftDuration(
            RecipeCatalog.forOutput(ItemId.IRON_ORE)!!.baseDurationMillis,
            level = 0,
        )

        withLoop(dispatcher, time) { loop ->
            loop.start(workingFactory())

            // Такт карьера, выгрузка и проезд одной клетки ленты — с запасом.
            val ticks = ((oreDuration + GameConstants.BELT_TRAVEL_TIME_MS) / GameConstants.TICK_INTERVAL_MS + 10).toInt()
            repeat(ticks) {
                time.now += GameConstants.TICK_INTERVAL_MS
                advanceTimeBy(GameConstants.TICK_INTERVAL_MS)
            }
            runCurrent()

            val state = loop.state.value
            assertTrue("Карьер должен произвести руду", state.stats.itemsProduced > 0)
            assertTrue("Экспортёр должен принести монеты", state.coins > 0)
        }
    }

    @Test // остановленный тикер больше не двигает симуляцию
    fun stoppedLoopDoesNotAdvance() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val time = FakeTimeProvider()

        withLoop(dispatcher, time) { loop ->
            loop.start(workingFactory())
            repeat(5) {
                time.now += GameConstants.TICK_INTERVAL_MS
                advanceTimeBy(GameConstants.TICK_INTERVAL_MS)
            }
            runCurrent()
            val beforeStop = loop.state.value.stats.simulatedMillis

            loop.stop()
            repeat(20) {
                time.now += GameConstants.TICK_INTERVAL_MS
                advanceTimeBy(GameConstants.TICK_INTERVAL_MS)
            }
            runCurrent()

            assertEquals(beforeStop, loop.state.value.stats.simulatedMillis)
        }
    }

    @Test // команда постройки доходит до состояния и списывает деньги
    fun placeMachineCommandBuildsAndCharges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        withLoop(dispatcher, FakeTimeProvider()) { loop ->
            loop.start(GameState.EMPTY.copy(coins = 10_000L, isInitialized = true))
            val position = GridPosition(4, 4)
            val cost = FactoryBuilder.buildCost(loop.state.value, MachineType.SMELTER)

            loop.dispatch(GameCommand.PlaceMachine(position, MachineType.SMELTER))

            val state = loop.state.value
            assertEquals(10_000L - cost, state.coins)
            assertEquals(MachineType.SMELTER, state.machineAt(position)?.type)
        }
    }

    @Test // команда улучшения поднимает уровень построенной машины
    fun upgradeMachineCommandRaisesLevel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        withLoop(dispatcher, FakeTimeProvider()) { loop ->
            loop.start(GameState.EMPTY.copy(coins = 100_000L, isInitialized = true))
            val position = GridPosition(4, 4)
            loop.dispatch(GameCommand.PlaceMachine(position, MachineType.SMELTER))
            val machineId = loop.state.value.machineAt(position)!!.id

            loop.dispatch(GameCommand.UpgradeMachine(machineId))

            assertEquals(1, loop.state.value.machines.getValue(machineId).level)
        }
    }

    @Test // офлайн-доход начисляется командой и попадает в статистику
    fun offlineEarningsAreApplied() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        withLoop(dispatcher, FakeTimeProvider()) { loop ->
            loop.start(GameState.EMPTY.copy(coins = 50L, isInitialized = true))
            loop.dispatch(GameCommand.ApplyOfflineEarnings(coins = 1_000L))

            assertEquals(1_050L, loop.state.value.coins)
            assertEquals(1_000L, loop.state.value.stats.coinsEarned)
        }
    }
}
