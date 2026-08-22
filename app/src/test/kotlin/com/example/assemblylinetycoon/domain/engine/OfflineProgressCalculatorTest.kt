package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.domain.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Начисление за время отсутствия: потолок, отметка времени, ставка. */
class OfflineProgressCalculatorTest {

    private val calculator = OfflineProgressCalculator()

    private val hour = 60 * 60 * 1000L

    /**
     * Отметка «игра уже сохранялась». Ноль означает обратное — первый запуск,
     * поэтому в тестах нужна ненулевая точка отсчёта.
     */
    private val savedAt = 1_000_000L

    /** Состояние с известной ставкой и отметкой последнего сохранения. */
    private fun savedState(rate: Double, savedAt: Long) = GameState.EMPTY.copy(
        isInitialized = true,
        lastSavedAtMillis = savedAt,
        baselineProductionRate = rate,
    )

    @Test // прошедшее время считается от сохранённой отметки
    fun elapsedTimeIsMeasuredFromSavedTimestamp() {
        val progress = calculator.calculate(
            state = savedState(rate = 1.0, savedAt = 1_000_000L),
            nowMillis = 1_000_000L + 30 * 60 * 1000L,
        )

        assertEquals(30 * 60 * 1000L, progress.elapsedMillis)
        assertEquals(30 * 60 * 1000L, progress.cappedMillis)
    }

    @Test // доход пропорционален времени и ставке
    fun earningsFollowTimeTimesRate() {
        // 30 минут = 1800 секунд, ставка 2 монеты/сек, эффективность офлайна 0.5
        val progress = calculator.calculate(
            state = savedState(rate = 2.0, savedAt = savedAt),
            nowMillis = savedAt + 30 * 60 * 1000L,
        )

        assertEquals(1_800L, progress.earnedCoins)
        assertTrue(progress.isSignificant)
    }

    @Test // отсутствие дольше двух часов начисляется как ровно два часа
    fun offlineIsCappedAtTwoHours() {
        val threeHours = calculator.calculate(savedState(1.0, savedAt), nowMillis = savedAt + 3 * hour)
        val exactlyTwo = calculator.calculate(savedState(1.0, savedAt), nowMillis = savedAt + 2 * hour)
        val tenHours = calculator.calculate(savedState(1.0, savedAt), nowMillis = savedAt + 10 * hour)

        assertEquals(GameConstants.OFFLINE_CAP_DEFAULT_MS, threeHours.cappedMillis)
        assertEquals(exactlyTwo.earnedCoins, threeHours.earnedCoins)
        assertEquals(exactlyTwo.earnedCoins, tenHours.earnedCoins)
        // Само прошедшее время при этом сохраняется как есть — для интерфейса.
        assertEquals(3 * hour, threeHours.elapsedMillis)
    }

    @Test // потолок можно расширить покупкой «Автоматический управляющий»
    fun managerCapExtendsOfflineWindow() {
        val withManager = calculator.calculate(
            state = savedState(1.0, savedAt),
            nowMillis = savedAt + 10 * hour,
            capMillis = GameConstants.OFFLINE_CAP_MANAGER_MS,
        )
        val withoutManager = calculator.calculate(savedState(1.0, savedAt), nowMillis = savedAt + 10 * hour)

        assertEquals(GameConstants.OFFLINE_CAP_MANAGER_MS, withManager.cappedMillis)
        assertTrue(withManager.earnedCoins > withoutManager.earnedCoins)
    }

    @Test // первый запуск ничего не начисляет
    fun firstLaunchEarnsNothing() {
        val fresh = calculator.calculate(GameState.EMPTY, nowMillis = 5 * hour)
        val noTimestamp = calculator.calculate(
            state = GameState.EMPTY.copy(isInitialized = true, baselineProductionRate = 10.0),
            nowMillis = 5 * hour,
        )

        assertEquals(0L, fresh.earnedCoins)
        assertEquals(0L, noTimestamp.earnedCoins)
        assertFalse(fresh.isSignificant)
    }

    @Test // перевод часов назад не даёт ни дохода, ни отрицательного баланса
    fun clockGoingBackwardsEarnsNothing() {
        val progress = calculator.calculate(
            state = savedState(rate = 5.0, savedAt = 10 * hour),
            nowMillis = 9 * hour,
        )

        assertEquals(0L, progress.elapsedMillis)
        assertEquals(0L, progress.earnedCoins)
    }

    @Test // без работающего производства офлайн ничего не приносит
    fun zeroProductionRateEarnsNothing() {
        val progress = calculator.calculate(savedState(rate = 0.0, savedAt = savedAt), nowMillis = savedAt + 2 * hour)

        assertEquals(0L, progress.earnedCoins)
    }

    @Test // расчёт не зависит от частоты тиков: важно только время
    fun calculationIsIndependentOfTickRate() {
        val state = savedState(rate = 3.0, savedAt = savedAt)

        val once = calculator.calculate(state, nowMillis = savedAt + hour)
        val alsoOnce = calculator.calculate(state.copy(), nowMillis = savedAt + hour)

        assertEquals(once.earnedCoins, alsoOnce.earnedCoins)
        // 3600 сек × 3 монеты × 0.5 = 5400
        assertEquals(5_400L, once.earnedCoins)
    }

    @Test // начисление применяется к балансу и сдвигает отметку времени
    fun applyingProgressUpdatesStateOnce() {
        val state = savedState(rate = 2.0, savedAt = savedAt).copy(coins = 100L)
        val now = savedAt + hour
        val progress = calculator.calculate(state, nowMillis = now)

        val applied = calculator.apply(state, progress, nowMillis = now)

        assertEquals(100L + progress.earnedCoins, applied.coins)
        assertEquals(now, applied.lastSavedAtMillis)
        assertEquals(progress.earnedCoins, applied.stats.coinsEarned)

        // Повторный расчёт сразу после применения уже ничего не даёт.
        val again = calculator.calculate(applied, nowMillis = now)
        assertEquals(0L, again.earnedCoins)
    }

    @Test // ставка берётся из реальной статистики производства
    fun productionRateComesFromStats() {
        val state = GameState.EMPTY.copy(
            isInitialized = true,
            lastSavedAtMillis = savedAt,
            stats = com.example.assemblylinetycoon.domain.model.ProductionStats(
                coinsEarned = 600L,
                simulatedMillis = 60_000L,   // 600 монет за минуту = 10 монет/сек
            ),
        )

        val refreshed = FactorySimulation.withRefreshedProductionRate(state)
        val progress = calculator.calculate(refreshed, nowMillis = savedAt + hour)

        assertEquals(10.0, refreshed.baselineProductionRate, 0.001)
        // 3600 сек × 10 × 0.5 = 18000
        assertEquals(18_000L, progress.earnedCoins)
    }
}
