package com.example.assemblylinetycoon.monetization.ads

import android.content.Context

/**
 * Инициализация рекламного SDK.
 *
 * Важное правило (152-ФЗ): SDK инициализируется только после того, как игрок
 * дал согласие на обработку данных. Поэтому это отдельный явный шаг, а не
 * вызов из Application.onCreate().
 */
interface AdsInitializer {
    fun initialize(context: Context, onComplete: () -> Unit = {})
    fun setUserConsent(granted: Boolean)
    fun setChildDirectedTreatment(enabled: Boolean)
}

/**
 * Обёртка над Yandex Mobile Ads SDK.
 *
 * Тело намеренно пустое: подключение SDK — отдельный этап. Класс существует,
 * чтобы остальной код уже сейчас зависел от абстракции, а не от `MobileAds`.
 */
class YandexAdsInitializer : AdsInitializer {

    override fun initialize(context: Context, onComplete: () -> Unit) {
        // TODO(этап 5): MobileAds.initialize(context) { onComplete() }
        onComplete()
    }

    override fun setUserConsent(granted: Boolean) {
        // TODO(этап 5): MobileAds.setUserConsent(granted)
    }

    override fun setChildDirectedTreatment(enabled: Boolean) {
        // TODO(этап 5): MobileAds.setAgeRestrictedUser(enabled)
    }
}
