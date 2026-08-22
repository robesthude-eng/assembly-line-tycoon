package com.example.assemblylinetycoon.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** Компактная запись чисел для HUD. */
class NumberFormatterTest {

    @Test // до тысячи число печатается целиком
    fun smallNumbersStayIntact() {
        assertEquals("0", NumberFormatter.format(0L))
        assertEquals("999", NumberFormatter.format(999L))
    }

    @Test // тысячи, миллионы и миллиарды получают суффикс
    fun largeNumbersGetSuffix() {
        assertEquals("1K", NumberFormatter.format(1_000L))
        assertEquals("1.2K", NumberFormatter.format(1_234L))
        assertEquals("3.4M", NumberFormatter.format(3_456_789L))
        assertEquals("25M", NumberFormatter.format(25_000_000L))
        assertEquals("1B", NumberFormatter.format(1_000_000_000L))
    }

    @Test // отрицательные значения сохраняют знак
    fun negativeNumbersKeepSign() {
        assertEquals("-1.5K", NumberFormatter.format(-1_500L))
    }

    @Test // предельное значение Long не ломает форматирование
    fun extremeValuesAreHandled() {
        assertEquals("9.2Qi", NumberFormatter.format(Long.MAX_VALUE))
    }

    @Test // длительность читается по-русски
    fun durationIsHumanReadable() {
        assertEquals("0 сек", NumberFormatter.formatDuration(0L))
        assertEquals("45 сек", NumberFormatter.formatDuration(45_000L))
        assertEquals("5 мин 0 сек", NumberFormatter.formatDuration(5 * 60 * 1000L))
        assertEquals("2 ч 15 мин", NumberFormatter.formatDuration((2 * 60 + 15) * 60 * 1000L))
    }

    @Test // скорость показывается с одним знаком
    fun rateHasSingleDecimal() {
        assertEquals("12.5/сек", NumberFormatter.formatRate(12.56))
        assertEquals("0.0/сек", NumberFormatter.formatRate(0.0))
    }
}
