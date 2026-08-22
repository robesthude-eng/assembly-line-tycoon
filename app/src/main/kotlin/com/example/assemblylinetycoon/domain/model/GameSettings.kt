package com.example.assemblylinetycoon.domain.model


/**
 * Пользовательские настройки и монетизационные флаги.
 * Хранятся отдельно от [GameState]: их не нужно пересчитывать каждый тик.
 */
data class GameSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** Куплен ли «Без рекламы» — отключает межстраничные показы. */
    val noAdsPurchased: Boolean = false,
    /** Куплен ли «Автоматический управляющий» — увеличивает потолок офлайна. */
    val automatedManagerPurchased: Boolean = false,
    /** Дано ли согласие на обработку данных (152-ФЗ) до инициализации SDK рекламы. */
    val privacyConsentGranted: Boolean = false,
)
