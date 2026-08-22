package com.example.assemblylinetycoon.data.local.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey

/** Ключи Preferences DataStore. Держим в одном месте, чтобы не разъезжались строки. */
object SettingsKeys {
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val NO_ADS_PURCHASED = booleanPreferencesKey("no_ads_purchased")
    val AUTOMATED_MANAGER_PURCHASED = booleanPreferencesKey("automated_manager_purchased")
    val PRIVACY_CONSENT_GRANTED = booleanPreferencesKey("privacy_consent_granted")
}
