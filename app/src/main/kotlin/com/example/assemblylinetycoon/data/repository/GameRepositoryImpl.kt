package com.example.assemblylinetycoon.data.repository

import androidx.datastore.core.DataStore
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.data.mapper.GameStateMapper
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Реализация [GameRepository] поверх типизированного DataStore. */
class GameRepositoryImpl(
    private val dataStore: DataStore<GameState>,
    private val dispatchers: DispatcherProvider,
) : GameRepository {

    override fun observeGameState(): Flow<GameState> =
        dataStore.data.map(GameStateMapper::migrateIfNeeded)

    override suspend fun loadGameState(): GameState = withContext(dispatchers.io) {
        GameStateMapper.migrateIfNeeded(dataStore.data.first())
    }

    override suspend fun saveGameState(state: GameState) {
        withContext(dispatchers.io) {
            dataStore.updateData { state }
        }
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            dataStore.updateData { GameState.EMPTY }
        }
    }
}
