package com.example.assemblylinetycoon.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.assemblylinetycoon.domain.model.GameState
import kotlinx.coroutines.CoroutineScope

/**
 * Создание хранилищ DataStore.
 *
 * Два независимых стора:
 *  * [createGameStateStore] — типизированный снапшот симуляции (пишется часто);
 *  * [createPreferencesStore] — настройки и флаги покупок (пишутся редко).
 *
 * Разделение не косметическое: автосейв игры не должен переписывать файл
 * настроек и наоборот.
 */
object DataStoreProvider {

    private const val GAME_STATE_FILE = "game_state.json"
    private const val PREFERENCES_FILE = "settings"

    fun createGameStateStore(
        context: Context,
        scope: CoroutineScope,
    ): DataStore<GameState> = DataStoreFactory.create(
        serializer = GameStateSerializer(),
        corruptionHandler = ReplaceFileCorruptionHandler { GameState.EMPTY },
        scope = scope,
        produceFile = { context.dataStoreFile(GAME_STATE_FILE) },
    )

    fun createPreferencesStore(
        context: Context,
        scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE) },
    )
}
