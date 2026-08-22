package com.example.assemblylinetycoon.app

import android.content.Context
import com.example.assemblylinetycoon.core.dispatcher.DefaultDispatcherProvider
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.core.utils.SystemTimeProvider
import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.data.local.datastore.DataStoreProvider
import com.example.assemblylinetycoon.data.repository.GameRepositoryImpl
import com.example.assemblylinetycoon.data.repository.SettingsRepositoryImpl
import com.example.assemblylinetycoon.domain.engine.GameEngine
import com.example.assemblylinetycoon.domain.engine.GameLoop
import com.example.assemblylinetycoon.domain.repository.AdsRepository
import com.example.assemblylinetycoon.domain.repository.BillingRepository
import com.example.assemblylinetycoon.domain.repository.GameRepository
import com.example.assemblylinetycoon.domain.repository.SettingsRepository
import com.example.assemblylinetycoon.domain.usecase.CalculateOfflineProgressUseCase
import com.example.assemblylinetycoon.domain.usecase.LoadGameStateUseCase
import com.example.assemblylinetycoon.domain.usecase.ObserveGameStateUseCase
import com.example.assemblylinetycoon.domain.usecase.ObserveSettingsUseCase
import com.example.assemblylinetycoon.domain.usecase.SaveGameStateUseCase
import com.example.assemblylinetycoon.domain.usecase.ShowRewardedAdUseCase
import com.example.assemblylinetycoon.monetization.ads.AdsInitializer
import com.example.assemblylinetycoon.monetization.ads.AdsRepositoryImpl
import com.example.assemblylinetycoon.monetization.ads.YandexAdsInitializer
import com.example.assemblylinetycoon.monetization.ads.YandexInterstitialAdsManager
import com.example.assemblylinetycoon.monetization.ads.YandexRewardedAdsManager
import com.example.assemblylinetycoon.monetization.billing.BillingManager
import com.example.assemblylinetycoon.monetization.billing.BillingRepositoryImpl
import com.example.assemblylinetycoon.monetization.billing.RuStoreBillingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Ручной граф зависимостей уровня приложения.
 *
 * Всё создаётся лениво и живёт столько же, сколько процесс. DI-библиотека не
 * подключена сознательно: граф маленький, а лишняя кодогенерация замедляет
 * сборку игрового проекта, где важна скорость итераций.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
    val timeProvider: TimeProvider = SystemTimeProvider()

    /** Скоуп для DataStore и фоновых задач уровня приложения. */
    private val appScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    // ── data ────────────────────────────────────────────────────────────────
    private val gameStateStore by lazy {
        DataStoreProvider.createGameStateStore(appContext, appScope)
    }
    private val preferencesStore by lazy {
        DataStoreProvider.createPreferencesStore(appContext, appScope)
    }

    val gameRepository: GameRepository by lazy {
        GameRepositoryImpl(gameStateStore, dispatchers)
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(preferencesStore, dispatchers)
    }

    // ── monetization ────────────────────────────────────────────────────────
    val adsInitializer: AdsInitializer by lazy { YandexAdsInitializer() }
    val billingManager: BillingManager by lazy { RuStoreBillingManager() }

    val adsRepository: AdsRepository by lazy {
        AdsRepositoryImpl(
            rewarded = YandexRewardedAdsManager(),
            interstitial = YandexInterstitialAdsManager(),
        )
    }
    val billingRepository: BillingRepository by lazy {
        BillingRepositoryImpl(billingManager)
    }

    // ── domain ──────────────────────────────────────────────────────────────
    val gameEngine: GameEngine by lazy { GameLoop(dispatchers, timeProvider) }

    val observeGameStateUseCase by lazy { ObserveGameStateUseCase(gameRepository) }
    val loadGameStateUseCase by lazy { LoadGameStateUseCase(gameRepository) }
    val saveGameStateUseCase by lazy { SaveGameStateUseCase(gameRepository) }
    val observeSettingsUseCase by lazy { ObserveSettingsUseCase(settingsRepository) }
    val calculateOfflineProgressUseCase by lazy { CalculateOfflineProgressUseCase() }
    val showRewardedAdUseCase by lazy { ShowRewardedAdUseCase(adsRepository) }
}
