package com.example.assemblylinetycoon.core.constants

/**
 * Идентификаторы рекламных блоков и товаров.
 *
 * Боевые значения подставляются на этапе сборки из GitHub Secrets и в репозитории
 * не хранятся. Здесь — только демо-блоки Яндекса и стабильные ключи товаров.
 */
object MonetizationConstants {

    // ── Yandex Mobile Ads: демо-блоки для отладки ───────────────────────────
    const val DEMO_REWARDED_UNIT_ID = "demo-rewarded-yandex"
    const val DEMO_INTERSTITIAL_UNIT_ID = "demo-interstitial-yandex"

    /** Минимальный интервал между межстраничными показами (мс). */
    const val INTERSTITIAL_COOLDOWN_MS: Long = 3 * 60 * 1000L

    /** «Тихий период» после старта новой игры, когда реклама не запрашивается (мс). */
    const val AD_FREE_ONBOARDING_MS: Long = 3 * 60 * 1000L

    /** Пауза перед повторной попыткой загрузки объявления (мс). */
    const val AD_RELOAD_BACKOFF_MS: Long = 30_000L

    // ── RuStore Billing: идентификаторы товаров ─────────────────────────────
    const val PRODUCT_NO_ADS = "no_ads_lifetime"
    const val PRODUCT_AUTOMATED_MANAGER = "automated_manager_lifetime"
    const val PRODUCT_STARTER_PACK = "starter_pack"
    const val PRODUCT_PRO_PACK = "pro_pack"
    const val PRODUCT_TYCOON_PACK = "tycoon_pack"

    /** Идентификатор приложения в консоли RuStore (заполняется перед релизом). */
    const val RUSTORE_CONSOLE_APP_ID = ""

    /** Схема deeplink для возврата из оплаты RuStore. */
    const val RUSTORE_DEEPLINK_SCHEME = "assemblylinetycoon"
}
