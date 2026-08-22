package com.example.assemblylinetycoon.core.utils

import com.example.assemblylinetycoon.core.constants.GameConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Формулы прогрессии: границы, монотонность и защита от переполнения. */
class MathUtilsTest {

    @Test // первый апгрейд стоит ровно базовую цену
    fun firstUpgradeCostsBasePrice() {
        assertEquals(150L, MathUtils.upgradeCost(baseCost = 150L, level = 0))
    }

    @Test // цена растёт как 1.15 в степени уровня
    fun upgradeCostFollowsGrowthCurve() {
        // 100 * 1.15^3 = 152.0875 -> 152
        assertEquals(152L, MathUtils.upgradeCost(baseCost = 100L, level = 3))
        // 800 * 1.18^5 = 1830.0... (множитель ассемблера)
        assertEquals(1_830L, MathUtils.upgradeCost(baseCost = 800L, level = 5, growthFactor = 1.18))
    }

    @Test // цена строго возрастает с уровнем
    fun upgradeCostIsMonotonic() {
        var previous = 0L
        for (level in 0..40) {
            val cost = MathUtils.upgradeCost(baseCost = 50L, level = level)
            assertTrue("Уровень $level дешевле предыдущего", cost >= previous)
            previous = cost
        }
    }

    @Test // огромные уровни не переполняют Long, а упираются в потолок
    fun upgradeCostSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, MathUtils.upgradeCost(baseCost = 1_000L, level = 1_000))
    }

    @Test // пакетная покупка равна сумме отдельных
    fun bulkCostEqualsSumOfSingleCosts() {
        val manual = (0 until 5).sumOf { MathUtils.upgradeCost(300L, it) }
        assertEquals(manual, MathUtils.bulkUpgradeCost(baseCost = 300L, fromLevel = 0, count = 5))
    }

    @Test // на бюджет покупается ровно столько уровней, сколько он покрывает
    fun affordableLevelsRespectsBudget() {
        val budget = MathUtils.bulkUpgradeCost(baseCost = 100L, fromLevel = 0, count = 4)
        assertEquals(4, MathUtils.affordableLevels(baseCost = 100L, fromLevel = 0, budget = budget))
        assertEquals(3, MathUtils.affordableLevels(baseCost = 100L, fromLevel = 0, budget = budget - 1))
    }

    @Test // длительность падает, но не ниже жёсткого пола
    fun craftDurationNeverDropsBelowFloor() {
        assertEquals(4_000L, MathUtils.craftDuration(baseDurationMillis = 4_000L, level = 0))
        assertTrue(MathUtils.craftDuration(4_000L, level = 10) < 4_000L)
        assertEquals(
            GameConstants.MIN_CRAFT_DURATION_MS,
            MathUtils.craftDuration(baseDurationMillis = 25_000L, level = 500),
        )
    }

    @Test // офлайн обрезается потолком и множителем эффективности
    fun offlineEarningsAreCappedAndScaled() {
        val threeHours = 3 * 60 * 60 * 1000L
        // потолок 2 часа = 7200 сек, ставка 10/сек, эффективность 0.5
        assertEquals(36_000L, MathUtils.offlineEarnings(threeHours, ratePerSecond = 10.0))
    }

    @Test // отрицательное время и нулевая ставка не приносят денег
    fun offlineEarningsIgnoreInvalidInput() {
        assertEquals(0L, MathUtils.offlineEarnings(-5_000L, ratePerSecond = 10.0))
        assertEquals(0L, MathUtils.offlineEarnings(60_000L, ratePerSecond = 0.0))
    }

    @Test // баланс не уходит в минус и не переполняется
    fun addCoinsIsClamped() {
        assertEquals(0L, MathUtils.addCoins(current = 10L, delta = -50L))
        assertEquals(Long.MAX_VALUE, MathUtils.addCoins(current = Long.MAX_VALUE - 5L, delta = 100L))
        assertEquals(150L, MathUtils.addCoins(current = 100L, delta = 50L))
    }

    @Test // прогресс такта лежит в диапазоне от нуля до единицы
    fun progressIsNormalized() {
        assertEquals(0f, MathUtils.progress(0L, 1_000L), 0.0001f)
        assertEquals(0.5f, MathUtils.progress(500L, 1_000L), 0.0001f)
        assertEquals(1f, MathUtils.progress(5_000L, 1_000L), 0.0001f)
        assertEquals(1f, MathUtils.progress(10L, 0L), 0.0001f)
    }
}
