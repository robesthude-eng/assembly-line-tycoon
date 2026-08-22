package com.example.assemblylinetycoon.domain.repository

import com.example.assemblylinetycoon.domain.model.GameState
import kotlinx.coroutines.flow.Flow

/**
 * Контракт хранения состояния игры. Реализация — в слое data (DataStore).
 * Домен не знает ни про DataStore, ни про Context.
 */
interface GameRepository {
    /** Поток актуального состояния. Первый элемент — загруженное сохранение. */
    fun observeGameState(): Flow<GameState>

    /** Разовое чтение состояния (например, при холодном старте). */
    suspend fun loadGameState(): GameState

    /** Полное сохранение снапшота. */
    suspend fun saveGameState(state: GameState)

    /** Сброс прогресса к состоянию новой игры. */
    suspend fun clear()
}
