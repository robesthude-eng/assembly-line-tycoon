package com.example.assemblylinetycoon.data

import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.data.save.SaveManager
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.repository.GameRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты автосохранения.
 *
 * Проверяется поведение, а не реализация: срабатывает ли запись по интервалу,
 * не идут ли две записи одновременно, снимается ли цикл при уходе в фон.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveManagerTest {

    private val interval = 10_000L

    private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val unconfined: CoroutineDispatcher = dispatcher
    }

    private class FixedTime(private val value: Long) : TimeProvider {
        override fun nowMillis(): Long = value
        override fun elapsedRealtimeMillis(): Long = value
    }

    /** Репозиторий, который умеет «писать долго» — так ловятся гонки. */
    private class RecordingRepository(private val writeDelayMillis: Long = 0L) : GameRepository {
        val saved = mutableListOf<GameState>()
        var concurrentWrites = 0
        private var writersNow = 0

        override fun observeGameState(): Flow<GameState> = flowOf(GameState.NEW_GAME)
        override suspend fun loadGameState(): GameState = GameState.NEW_GAME

        override suspend fun saveGameState(state: GameState) {
            writersNow++
            if (writersNow > 1) concurrentWrites++
            if (writeDelayMillis > 0) delay(writeDelayMillis)
            saved += state
            writersNow--
        }

        override suspend fun clearSaveData() = Unit
    }

    private fun manager(
        repository: GameRepository,
        scope: TestScope,
        nowMillis: Long = 1_000L,
    ) = SaveManager(
        repository = repository,
        dispatchers = TestDispatchers(StandardTestDispatcher(scope.testScheduler)),
        timeProvider = FixedTime(nowMillis),
        scope = scope,
        intervalMillis = interval,
    )

    @Test // запись идёт по интервалу, а не при каждом такте симуляции
    fun autosaveFiresOnInterval() = runTest {
        val repository = RecordingRepository()
        val manager = manager(repository, this)

        manager.start { GameState.NEW_GAME.copy(coins = 50L) }
        runCurrent()
        assertEquals("До первого интервала писать нечего", 0, repository.saved.size)

        advanceTimeBy(interval * 3 + 1)
        runCurrent()
        manager.stop()

        assertEquals(3, repository.saved.size)
    }

    @Test // сохраняется актуальное состояние, а не снимок момента запуска
    fun autosaveWritesFreshState() = runTest {
        val repository = RecordingRepository()
        val manager = manager(repository, this)
        var coins = 10L

        manager.start { GameState.NEW_GAME.copy(coins = coins) }
        advanceTimeBy(interval + 1)
        runCurrent()
        coins = 999L
        advanceTimeBy(interval)
        runCurrent()
        manager.stop()

        assertEquals(listOf(10L, 999L), repository.saved.map(GameState::coins))
    }

    @Test // отметка времени ставится менеджером: от неё считается офлайн-доход
    fun saveStampsTimestamp() = runTest {
        val repository = RecordingRepository()
        val manager = manager(repository, this, nowMillis = 1_700_000_000_000L)

        manager.saveNow(GameState.NEW_GAME)
        advanceUntilIdle()

        assertEquals(1_700_000_000_000L, repository.saved.single().lastSavedAtMillis)
        assertTrue(repository.saved.single().isInitialized)
    }

    @Test // две записи одновременно невозможны: иначе старый снапшот затрёт новый
    fun concurrentSavesAreSerialized() = runTest {
        val repository = RecordingRepository(writeDelayMillis = 500L)
        val manager = manager(repository, this)

        // Совпавшие по времени автосейв и сохранение при уходе в фон.
        repeat(5) { index ->
            launch { manager.saveNow(GameState.NEW_GAME.copy(coins = index.toLong())) }
        }
        advanceUntilIdle()

        assertEquals(0, repository.concurrentWrites)
        assertEquals(5, repository.saved.size)
    }

    @Test // остановка снимает цикл: свёрнутое приложение не пишет файл
    fun stopCancelsAutosave() = runTest {
        val repository = RecordingRepository()
        val manager = manager(repository, this)
        manager.start { GameState.NEW_GAME }
        advanceTimeBy(interval + 1)
        runCurrent()
        val afterFirst = repository.saved.size

        manager.stop()
        advanceTimeBy(interval * 10)
        runCurrent()

        assertEquals(afterFirst, repository.saved.size)
        assertTrue(!manager.isRunning)
    }

    @Test // повторный запуск не плодит параллельные циклы
    fun repeatedStartDoesNotDuplicateLoop() = runTest {
        val repository = RecordingRepository()
        val manager = manager(repository, this)

        manager.start { GameState.NEW_GAME }
        manager.start { GameState.NEW_GAME }
        manager.start { GameState.NEW_GAME }
        advanceTimeBy(interval + 1)
        runCurrent()
        manager.stop()

        assertEquals("Три запуска дали бы три записи за один интервал", 1, repository.saved.size)
    }

    @Test // сохранение не блокирует того, кто его запросил
    fun savingDoesNotBlockSimulation() = runTest {
        val repository = RecordingRepository(writeDelayMillis = 5_000L)
        val manager = manager(repository, this)
        var simulationTicks = 0

        manager.start { GameState.NEW_GAME }
        // «Симуляция»: тикает каждые 50 мс независимо от записи на диск.
        val ticker = launch {
            repeat(400) {
                delay(50L)
                simulationTicks++
            }
        }
        advanceTimeBy(20_000L)
        runCurrent()
        manager.stop()
        ticker.cancel()

        // За 20 секунд ровно 400 тиков по 50 мс — долгая запись не съела ни одного.
        assertEquals(400, simulationTicks)
        assertTrue("Запись всё-таки должна была случиться", repository.saved.isNotEmpty())
    }
}
