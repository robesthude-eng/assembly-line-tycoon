package com.example.assemblylinetycoon.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.data.mapper.SettingsMapper
import com.example.assemblylinetycoon.domain.model.GameSettings
import com.example.assemblylinetycoon.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Реализация [SettingsRepository] поверх Preferences DataStore. */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : SettingsRepository {

    override fun observeSettings(): Flow<GameSettings> =
        dataStore.data.map(SettingsMapper::toDomain)

    override suspend fun updateSettings(transform: (GameSettings) -> GameSettings) {
        withContext(dispatchers.io) {
            dataStore.edit { preferences ->
                val updated = transform(SettingsMapper.toDomain(preferences))
                SettingsMapper.applyTo(preferences, updated)
            }
        }
    }
}
