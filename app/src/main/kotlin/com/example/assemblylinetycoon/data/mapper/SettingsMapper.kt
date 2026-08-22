package com.example.assemblylinetycoon.data.mapper

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.example.assemblylinetycoon.data.local.datastore.SettingsKeys
import com.example.assemblylinetycoon.domain.model.GameSettings

/**
 * Преобразование Preferences ↔ доменная модель.
 * Маппер существует, чтобы домен не знал про типы DataStore.
 */
object SettingsMapper {

    fun toDomain(preferences: Preferences): GameSettings = GameSettings(
        soundEnabled = preferences[SettingsKeys.SOUND_ENABLED] ?: true,
        hapticsEnabled = preferences[SettingsKeys.HAPTICS_ENABLED] ?: true,
        noAdsPurchased = preferences[SettingsKeys.NO_ADS_PURCHASED] ?: false,
        automatedManagerPurchased = preferences[SettingsKeys.AUTOMATED_MANAGER_PURCHASED] ?: false,
        privacyConsentGranted = preferences[SettingsKeys.PRIVACY_CONSENT_GRANTED] ?: false,
    )

    fun applyTo(preferences: MutablePreferences, settings: GameSettings) {
        preferences[SettingsKeys.SOUND_ENABLED] = settings.soundEnabled
        preferences[SettingsKeys.HAPTICS_ENABLED] = settings.hapticsEnabled
        preferences[SettingsKeys.NO_ADS_PURCHASED] = settings.noAdsPurchased
        preferences[SettingsKeys.AUTOMATED_MANAGER_PURCHASED] = settings.automatedManagerPurchased
        preferences[SettingsKeys.PRIVACY_CONSENT_GRANTED] = settings.privacyConsentGranted
    }
}
