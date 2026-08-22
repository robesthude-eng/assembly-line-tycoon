package com.example.assemblylinetycoon.data.repository

import androidx.datastore.core.DataStore
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.data.local.datastore.model.SavedGameState
import com.example.assemblylinetycoon.data.mapper.GameStateMapper
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Реализация [GameRepository] поверх типизированного DataStore.
 *
 * Единственное место, которое знает про DataStore. Наружу отдаются только
 * доменные [GameState]: ни use case, ни ViewModel не догадываются, что игра
 * лежит в JSON-файле, и переезд на другое хранилище их не заденет.
 */
class GameRepositoryImpl(
    private val dataStore: DataStore<SavedGameState>,
    private val dispatchers: DispatcherProvider,
) : GameRepository {

    override fun observeGameState(): Flow<GameState> = dataStore.data
        // Файла нет или он недоступен — это нормальный первый запуск,
        // а не повод обрушить поток на весь остаток жизни процесса.
        .catch { error -> if (error is IOException) emit(defaultSnapshot()) else throw error }
        .map(GameStateMapper::toDomain)

    override suspend fun loadGameState(): GameState = withContext(dispatchers.io) {
        GameStateMapper.toDomain(observeSafely())
    }

    override suspend fun saveGameState(state: GameState) {
        withContext(dispatchers.io) {
            dataStore.updateData { GameStateMapper.toData(state) }
        }
    }

    override suspend fun clearSaveData() {
        withContext(dispatchers.io) {
            dataStore.updateData { defaultSnapshot() }
        }
    }

    private suspend fun observeSafely(): SavedGameState =
        try {
            dataStore.data.first()
        } catch (e: IOException) {
            // Читать нечего — отдаём новую игру. Логировать здесь нечего:
            // отсутствие файла при первом запуске не ошибка.
            defaultSnapshot()
        }

    private fun defaultSnapshot(): SavedGameState = GameStateMapper.toData(GameState.NEW_GAME)
}
