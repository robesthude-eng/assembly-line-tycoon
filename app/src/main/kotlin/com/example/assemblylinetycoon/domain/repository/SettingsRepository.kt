package com.example.assemblylinetycoon.domain.repository

import com.example.assemblylinetycoon.domain.model.GameSettings
import kotlinx.coroutines.flow.Flow

/** Контракт хранения настроек и монетизационных флагов. */
interface SettingsRepository {
    fun observeSettings(): Flow<GameSettings>
    suspend fun updateSettings(transform: (GameSettings) -> GameSettings)
}
