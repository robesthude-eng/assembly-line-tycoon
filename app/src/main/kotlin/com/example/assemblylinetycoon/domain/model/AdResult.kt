package com.example.assemblylinetycoon.domain.model

/** Итог показа объявления. */
sealed interface AdResult {
    /** Пользователь досмотрел ролик — награду можно выдать. */
    data object Rewarded : AdResult

    /** Пользователь закрыл ролик досрочно. Кулдаун при этом не тратится. */
    data object Dismissed : AdResult

    /** Объявление не загрузилось или SDK недоступен. */
    data class Failed(val reason: String) : AdResult

    /** Показ пропущен: активна покупка «Без рекламы» либо не истёк кулдаун. */
    data object Skipped : AdResult
}
