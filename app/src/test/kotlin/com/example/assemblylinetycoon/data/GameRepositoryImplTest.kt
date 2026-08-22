package com.example.assemblylinetycoon.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.data.local.datastore.GameStateSerializer
import com.example.assemblylinetycoon.data.local.datastore.model.SavedGameState
import com.example.assemblylinetycoon.data.mapper.GameStateMapper
import com.example.assemblylinetycoon.data.repository.GameRepositoryImpl
import com.example.assemblylinetycoon.domain.engine.FactoryBuilder
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.repository.GameRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Репозиторий поверх настоящего DataStore на временном файле.
 *
 * Проверяется то, ради чего слой существует: игра, записанная одним запуском,
 * читается следующим. Подменять DataStore заглушкой здесь бессмысленно —
 * ошибки живут именно в связке «сериализатор + файл + маппер».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val unconfined: CoroutineDispatcher = dispatcher
    }

    private fun repository(scope: CoroutineScope, file: File): GameRepository =
        GameRepositoryImpl(
            dataStore = DataStoreFactory.create(
                serializer = GameStateSerializer(),
                corruptionHandler = ReplaceFileCorruptionHandler {
                    GameStateMapper.toData(GameState.NEW_GAME)
                },
                scope = scope,
                produceFile = { file },
            ),
            dispatchers = TestDispatchers(dispatcher),
        )

    /**
     * Скоуп «одного запуска приложения».
     *
     * Именно скоуп, а не `TestScope`: DataStore держит файл занятым, пока не
     * завершится job владельца, а job у `TestScope` завершается только в конце
     * теста. Собственный Job позволяет честно «закрыть приложение» посреди теста.
     */
    private fun launchScope(): CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    private fun factory(): GameState {
        var state = GameState.NEW_GAME.copy(coins = 4_242L)
        state = FactoryBuilder.place(state, GridPosition(2, 2), MachineType.SPAWNER)
        state = FactoryBuilder.placeBelt(state, GridPosition(3, 2), Direction.RIGHT)
        return state
    }

    @Test // записали — прочитали: завод и баланс на месте
    fun saveAndLoadCycleKeepsState() = runTest(dispatcher) {
        val file = folder.newFile("save.json")
        val repository = repository(launchScope(), file)
        val state = factory()

        repository.saveGameState(state)
        val loaded = repository.loadGameState()

        assertEquals(state.coins, loaded.coins)
        assertEquals(state.grid, loaded.grid)
        assertEquals(MachineType.SPAWNER, loaded.machineAt(GridPosition(2, 2))?.type)
    }

    @Test // перезапуск приложения: новый экземпляр читает тот же файл
    fun stateSurvivesRestart() = runTest(dispatcher) {
        val file = folder.newFile("restart.json")
        val state = factory()

        // Первый «запуск игры»: пишем и полностью гасим его скоуп. DataStore
        // намеренно запрещает два хранилища на один файл — это и моделирует
        // завершение процесса, а не просто создание второго объекта.
        val firstLaunch = launchScope()
        repository(firstLaunch, file).saveGameState(state)
        firstLaunch.cancel()
        // Отмена должна не просто «случиться», а доиграться до конца: DataStore
        // освобождает файл только когда завершились его собственные корутины.
        advanceUntilIdle()

        val afterRestart = repository(launchScope(), file).loadGameState()

        assertEquals(state.coins, afterRestart.coins)
        assertEquals(state.machines.size, afterRestart.machines.size)
        assertEquals(Direction.RIGHT, afterRestart.grid[GridPosition(3, 2)]?.direction)
    }

    @Test // пустой файл сохранения — это первый запуск, а не ошибка
    fun missingSaveGivesNewGame() = runTest(dispatcher) {
        val absent = File(folder.newFolder(), "нет-такого-файла.json")

        val loaded = repository(launchScope(), absent).loadGameState()

        assertEquals(GameState.NEW_GAME.coins, loaded.coins)
        assertTrue(loaded.machines.isEmpty())
    }

    @Test // испорченный файл откатывает к новой игре, а не роняет запуск
    fun corruptedSaveFallsBackToNewGame() = runTest(dispatcher) {
        val file = folder.newFile("corrupted.json")
        file.writeText("{это точно не сохранение")

        val loaded = repository(launchScope(), file).loadGameState()

        assertEquals(GameState.NEW_GAME.coins, loaded.coins)
    }

    @Test // сброс возвращает новую игру и стирает построенное
    fun clearSaveDataResetsProgress() = runTest(dispatcher) {
        val file = folder.newFile("clear.json")
        val repository = repository(launchScope(), file)
        repository.saveGameState(factory())

        repository.clearSaveData()
        val loaded = repository.loadGameState()

        assertTrue(loaded.machines.isEmpty())
        assertEquals(GameState.NEW_GAME.coins, loaded.coins)
    }

    @Test // поток отдаёт доменные модели, а не то, что лежит в файле
    fun observeReturnsDomainState() = runTest(dispatcher) {
        val file = folder.newFile("observe.json")
        val repository = repository(launchScope(), file)
        repository.saveGameState(factory().copy(coins = 111L))

        val observed: GameState = repository.observeGameState().first()

        assertEquals(111L, observed.coins)
        // Тип проверяется компилятором: SavedGameState наружу не выходит.
        assertTrue(observed !is SavedGameState)
    }
}
