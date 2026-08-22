package com.example.assemblylinetycoon.core.utils

import com.example.assemblylinetycoon.core.constants.GameConstants
import kotlin.math.max
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Формулы прогрессии: границы, монотонность и защита от переполнения. */
class MathUtilityTest {

    @Test // первый апгрейд стоит ровно базовую цену
    fun firstUpgradeCostsBasePrice() {
        assertEquals(150L, MathUtility.upgradeCost(baseCost = 150L, level = 0))
    }

    @Test // цена растёт как 1.15 в степени уровня
    fun upgradeCostFollowsGrowthCurve() {
        // 100 * 1.15^3 = 152.0875 -> 152
        assertEquals(152L, MathUtility.upgradeCost(baseCost = 100L, level = 3))
        // 800 * 1.18^5 = 1830.2... (множитель ассемблера)
        assertEquals(1_830L, MathUtility.upgradeCost(baseCost = 800L, level = 5, growthFactor = 1.18))
    }

    @Test // формула совпадает с записью из ТЗ: (BaseCost * 1.15^Level).toLong()
    fun upgradeCostMatchesSpecificationFormula() {
        val base = 250L
        for (level in 0..25) {
            val expected = (base.toDouble() * 1.15.pow(level)).toLong()
            assertEquals("Уровень $level", expected, MathUtility.upgradeCost(base, level))
        }
    }

    @Test // формула такта совпадает с ТЗ: max(100L, (Base * 0.95^Level).toLong())
    fun craftDurationMatchesSpecificationFormula() {
        val base = 12_000L
        for (level in 0..200) {
            val expected = max(100L, (base.toDouble() * 0.95.pow(level)).toLong())
            assertEquals("Уровень $level", expected, MathUtility.craftDuration(base, level))
        }
    }

    @Test // валюта считается в Long: результат формул — целое число монет
    fun currencyStaysInLongDomain() {
        val cost: Long = MathUtility.upgradeCost(baseCost = 1_500L, level = 12)
        val duration: Long = MathUtility.craftDuration(baseDurationMillis = 25_000L, level = 12)
        // 1500 * 1.15^12 = 8025.9... -> усечение до 8025, дробной части не остаётся
        assertEquals(8_025L, cost)
        assertEquals(13_509L, duration)
    }

    @Test // цена строго возрастает с уровнем
    fun upgradeCostIsMonotonic() {
        var previous = 0L
        for (level in 0..40) {
            val cost = MathUtility.upgradeCost(baseCost = 50L, level = level)
            assertTrue("Уровень $level дешевле предыдущего", cost >= previous)
            previous = cost
        }
    }

    @Test // огромные уровни не переполняют Long, а упираются в потолок
    fun upgradeCostSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, MathUtility.upgradeCost(baseCost = 1_000L, level = 1_000))
    }

    @Test // пакетная покупка равна сумме отдельных
    fun bulkCostEqualsSumOfSingleCosts() {
        val manual = (0 until 5).sumOf { MathUtility.upgradeCost(300L, it) }
        assertEquals(manual, MathUtility.bulkUpgradeCost(baseCost = 300L, fromLevel = 0, count = 5))
    }

    @Test // на бюджет покупается ровно столько уровней, сколько он покрывает
    fun affordableLevelsRespectsBudget() {
        val budget = MathUtility.bulkUpgradeCost(baseCost = 100L, fromLevel = 0, count = 4)
        assertEquals(4, MathUtility.affordableLevels(baseCost = 100L, fromLevel = 0, budget = budget))
        assertEquals(3, MathUtility.affordableLevels(baseCost = 100L, fromLevel = 0, budget = budget - 1))
    }

    @Test // на достаточном уровне такт упирается ровно в 100 мс
    fun craftDurationCapsAtHundredMillis() {
        // 2000 * 0.95^60 = 92.9 -> ниже пола, значит возвращается ровно пол
        assertEquals(100L, MathUtility.craftDuration(baseDurationMillis = 2_000L, level = 60))
        assertEquals(100L, MathUtility.craftDuration(baseDurationMillis = 25_000L, level = 1_000))
        // На уровень раньше значение ещё выше пола — граница не срезана заранее
        assertTrue(MathUtility.craftDuration(baseDurationMillis = 2_000L, level = 50) > 100L)
    }

    @Test // длительность падает, но не ниже жёсткого пола
    fun craftDurationNeverDropsBelowFloor() {
        assertEquals(4_000L, MathUtility.craftDuration(baseDurationMillis = 4_000L, level = 0))
        assertTrue(MathUtility.craftDuration(4_000L, level = 10) < 4_000L)
        assertEquals(
            GameConstants.MIN_CRAFT_DURATION_MS,
            MathUtility.craftDuration(baseDurationMillis = 25_000L, level = 500),
        )
    }

    @Test // офлайн обрезается потолком и множителем эффективности
    fun offlineEarningsAreCappedAndScaled() {
        val threeHours = 3 * 60 * 60 * 1000L
        // потолок 2 часа = 7200 сек, ставка 10/сек, эффективность 0.5
        assertEquals(36_000L, MathUtility.offlineEarnings(threeHours, ratePerSecond = 10.0))
    }

    @Test // отрицательное время и нулевая ставка не приносят денег
    fun offlineEarningsIgnoreInvalidInput() {
        assertEquals(0L, MathUtility.offlineEarnings(-5_000L, ratePerSecond = 10.0))
        assertEquals(0L, MathUtility.offlineEarnings(60_000L, ratePerSecond = 0.0))
    }

    @Test // баланс не уходит в минус и не переполняется
    fun addCoinsIsClamped() {
        assertEquals(0L, MathUtility.addCoins(current = 10L, delta = -50L))
        assertEquals(Long.MAX_VALUE, MathUtility.addCoins(current = Long.MAX_VALUE - 5L, delta = 100L))
        assertEquals(150L, MathUtility.addCoins(current = 100L, delta = 50L))
    }

    @Test // прогресс такта лежит в диапазоне от нуля до единицы
    fun progressIsNormalized() {
        assertEquals(0f, MathUtility.progress(0L, 1_000L), 0.0001f)
        assertEquals(0.5f, MathUtility.progress(500L, 1_000L), 0.0001f)
        assertEquals(1f, MathUtility.progress(5_000L, 1_000L), 0.0001f)
        assertEquals(1f, MathUtility.progress(10L, 0L), 0.0001f)
    }
}
