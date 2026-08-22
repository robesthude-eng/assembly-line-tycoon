package com.example.assemblylinetycoon.domain.model

/**
 * Результат расчёта офлайн-прогресса при запуске приложения.
 * Модель-контейнер: вычисления живут в use case, не здесь.
 */
data class OfflineProgress(
    val elapsedMillis: Long = 0L,
    val cappedMillis: Long = 0L,
    val earnedCoins: Long = 0L,
    /** Была ли награда удвоена просмотром рекламы. */
    val doubled: Boolean = false,
) {
    val isSignificant: Boolean get() = earnedCoins > 0L
}
