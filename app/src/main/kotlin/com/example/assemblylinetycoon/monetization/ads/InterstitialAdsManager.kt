package com.example.assemblylinetycoon.monetization.ads

import android.app.Activity
import com.example.assemblylinetycoon.domain.model.AdResult
import kotlinx.coroutines.flow.Flow

/**
 * Межстраничная реклама.
 *
 * Показ ограничен кулдауном (см. `MonetizationConstants.INTERSTITIAL_COOLDOWN_MS`)
 * и полностью отключается покупкой «Без рекламы».
 */
interface InterstitialAdsManager {
    fun observeAvailability(): Flow<Boolean>
    suspend fun preload(unitId: String)
    suspend fun showIfAllowed(activity: Activity): AdResult
    fun release()
}

/** Обёртка над `InterstitialAdLoader` из Yandex Mobile Ads SDK. Реализация — этап 5. */
class YandexInterstitialAdsManager : InterstitialAdsManager {

    override fun observeAvailability(): Flow<Boolean> =
        kotlinx.coroutines.flow.flowOf(false)

    override suspend fun preload(unitId: String) {
        // TODO(этап 5): InterstitialAdLoader.loadAd(...)
    }

    override suspend fun showIfAllowed(activity: Activity): AdResult = AdResult.Skipped

    override fun release() {
        // TODO(этап 5): освобождение слушателей
    }
}
