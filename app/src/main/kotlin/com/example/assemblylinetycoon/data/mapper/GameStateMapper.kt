package com.example.assemblylinetycoon.data.mapper

import com.example.assemblylinetycoon.domain.model.GameState

/**
 * Место для миграций схемы сохранения.
 *
 * Сейчас доменная модель и модель хранения совпадают, поэтому маппер прозрачный.
 * Как только форматы разойдутся (например, сетка завода станет храниться
 * упакованной), преобразование будет здесь, а не в репозитории.
 */
object GameStateMapper {

    fun migrateIfNeeded(state: GameState): GameState = when (state.schemaVersion) {
        // TODO: миграции при повышении SAVE_SCHEMA_VERSION
        else -> state
    }
}
