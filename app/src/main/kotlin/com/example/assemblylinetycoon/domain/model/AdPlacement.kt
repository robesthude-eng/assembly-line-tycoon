package com.example.assemblylinetycoon.domain.model

/**
 * Рекламные плейсменты игры (GDD, раздел Monetization Strategy).
 * Значения используются и как ключ кулдауна, и как метка для аналитики.
 */
enum class AdPlacement(val analyticsId: String, val rewarded: Boolean) {
    /** Удвоение офлайн-начисления. */
    OFFLINE_DOUBLE("reward_offline_double", rewarded = true),

    /** «Овердрайв»: ×2 к скорости завода на 5 минут. */
    OVERDRIVE_BOOST("reward_overdrive", rewarded = true),

    /** Мгновенная выдача дохода за N часов. */
    INSTANT_CASH("reward_instant_cash", rewarded = true),

    /** Мгновенная доставка VIP-заказа. */
    VIP_ORDER("reward_vip_order", rewarded = true),

    /** Межстраничная реклама на повышении уровня / крупном событии. */
    MILESTONE_INTERSTITIAL("interstitial_milestone", rewarded = false),
}
