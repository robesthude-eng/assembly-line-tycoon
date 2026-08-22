package com.example.assemblylinetycoon.core.utils

/**
 * Источник времени. Инжектируется, чтобы игровой цикл и расчёт офлайн-дохода
 * можно было тестировать детерминированно, а также чтобы позднее подменить
 * системные часы на защищённые от перевода времени назад.
 */
interface TimeProvider {
    /** Настенные часы, epoch millis. Используются для сохранений и офлайна. */
    fun nowMillis(): Long

    /** Монотонное время, не зависящее от перевода часов. Используется тикером. */
    fun elapsedRealtimeMillis(): Long
}

/** Реализация по умолчанию поверх JVM-часов (без зависимости от Android SDK). */
class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = System.nanoTime() / 1_000_000L
}
