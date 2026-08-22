package com.example.assemblylinetycoon.domain

import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.domain.engine.GameCommand
import com.example.assemblylinetycoon.domain.engine.GameLoop
import com.example.assemblylinetycoon.domain.model.GameState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет каркас движка, а не игровую логику (её ещё нет):
 * тикер запускается на подменённом диспетчере, состояние доступно как StateFlow,
 * сброс возвращает пустое состояние.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameLoopTest {

    private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main = dispatcher
        override val default = dispatcher
        override val io = dispatcher
        override val unconfined = dispatcher
    }

    private class FakeTimeProvider(var now: Long = 0L) : TimeProvider {
        override fun nowMillis(): Long = now
        override fun elapsedRealtimeMillis(): Long = now
    }

    @Test // состояние доступно сразу после старта
    fun stateIsAvailableImmediatelyAfterStart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val loop = GameLoop(TestDispatchers(dispatcher), FakeTimeProvider())

        loop.start(GameState.EMPTY.copy(coins = 100L, isInitialized = true))

        assertEquals(100L, loop.state.value.coins)
        assertTrue(loop.state.value.isInitialized)
        loop.dispose()
    }

    @Test // сброс прогресса возвращает пустое состояние
    fun resetProgressReturnsEmptyState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val loop = GameLoop(TestDispatchers(dispatcher), FakeTimeProvider())

        loop.start(GameState.EMPTY.copy(coins = 5_000L))
        loop.dispatch(GameCommand.ResetProgress)

        assertEquals(GameState.EMPTY, loop.state.value)
        loop.dispose()
    }

    @Test // тикер не меняет состояние, пока симуляция не реализована
    fun tickerDoesNotMutateStateUntilSimulationExists() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val time = FakeTimeProvider()
        val loop = GameLoop(TestDispatchers(dispatcher), time, tickIntervalMillis = 50L)

        val initial = GameState.EMPTY.copy(coins = 42L)
        loop.start(initial)
        advanceTimeBy(500L)

        assertEquals(initial, loop.state.value)
        loop.dispose()
    }
}
