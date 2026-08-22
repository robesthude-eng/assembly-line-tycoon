package com.example.assemblylinetycoon.monetization.ads

import android.app.Activity
import com.example.assemblylinetycoon.domain.model.AdPlacement
import com.example.assemblylinetycoon.domain.model.AdResult
import kotlinx.coroutines.flow.Flow

/**
 * Реклама за награду.
 *
 * Контракт возвращает [AdResult] как значение, а не колбэк: вызывающая сторона
 * пишет линейный код, а не цепочку слушателей.
 */
interface RewardedAdsManager {
    fun observeAvailability(): Flow<Boolean>
    suspend fun preload(unitId: String)
    suspend fun show(activity: Activity, placement: AdPlacement): AdResult
    fun release()
}

/**
 * Обёртка над `RewardedAdLoader` / `RewardedAd` из Yandex Mobile Ads SDK.
 * Реализация — этап 5, здесь только контракт и безопасные заглушки.
 */
class YandexRewardedAdsManager : RewardedAdsManager {

    override fun observeAvailability(): Flow<Boolean> =
        kotlinx.coroutines.flow.flowOf(false)

    override suspend fun preload(unitId: String) {
        // TODO(этап 5): RewardedAdLoader.loadAd(AdRequestConfiguration.Builder(unitId).build())
    }

    override suspend fun show(activity: Activity, placement: AdPlacement): AdResult =
        AdResult.Failed("Yandex Mobile Ads SDK ещё не подключён")

    override fun release() {
        // TODO(этап 5): rewardedAd?.setAdEventListener(null); loader.setAdLoadListener(null)
    }
}
