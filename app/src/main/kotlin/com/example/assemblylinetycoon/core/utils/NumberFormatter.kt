package com.example.assemblylinetycoon.core.utils

import kotlin.math.abs
import kotlin.math.floor

/**
 * Форматирование игровых чисел: 1 200 → «1.2K», 3 400 000 → «3.4M».
 *
 * Своя реализация вместо `NumberFormat` по двум причинам: во-первых, суффиксы
 * тайкуна выходят далеко за пределы стандартных локалей, во-вторых, функция
 * вызывается на каждом кадре HUD, а создание `NumberFormat` не бесплатно.
 * Никаких зависимостей от Android — это чистый Kotlin, пригодный для тестов.
 */
object NumberFormatter {

    private val SUFFIXES = arrayOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc")

    private const val STEP = 1_000.0

    /**
     * Компактная запись значения.
     *
     * До тысячи число печатается целиком, дальше — с одним знаком после точки,
     * причём незначащий ноль отбрасывается: «1K», а не «1.0K».
     */
    fun format(value: Long): String {
        if (value == Long.MIN_VALUE) return "-" + format(Long.MAX_VALUE)
        val sign = if (value < 0) "-" else ""
        var amount = abs(value).toDouble()
        var index = 0
        while (amount >= STEP && index < SUFFIXES.lastIndex) {
            amount /= STEP
            index++
        }
        if (index == 0) return sign + amount.toLong().toString()
        // Десятые считаются в целых числах: 1.2 в double даёт 0.19999... при
        // вычитании целой части, и «1.2K» превращалось бы в «1.1K».
        val scaled = floor(amount * 10).toLong()
        val whole = scaled / 10
        val tenth = scaled % 10
        val body = if (tenth == 0L) whole.toString() else "$whole.$tenth"
        return sign + body + SUFFIXES[index]
    }

    /** Скорость производства для HUD: «12.5/сек». */
    fun formatRate(perSecond: Double): String {
        val sign = if (perSecond < 0) "-" else ""
        val scaled = floor(abs(perSecond) * 10).toLong()
        return if (scaled >= STEP * 10) {
            sign + format(abs(perSecond).toLong()) + "/сек"
        } else {
            "$sign${scaled / 10}.${scaled % 10}/сек"
        }
    }

    /**
     * Длительность в формате «2 ч 15 мин» / «45 сек».
     * Используется для таймеров офлайна и «Ускорения».
     */
    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0 сек"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "$hours ч $minutes мин"
            minutes > 0 -> "$minutes мин $seconds сек"
            else -> "$seconds сек"
        }
    }
}
