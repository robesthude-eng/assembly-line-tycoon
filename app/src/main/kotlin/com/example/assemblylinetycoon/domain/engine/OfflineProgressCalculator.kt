package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.core.utils.MathUtility
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.OfflineProgress

/**
 * Расчёт дохода за время отсутствия игрока.
 *
 * Считает по сохранённой отметке времени, а не по количеству пропущенных
 * тиков: скорость визуального тикера не должна влиять на экономику. Игрок,
 * у которого телефон усыплял приложение, обязан получить столько же, сколько
 * получил бы с непрерывно работающим экраном.
 *
 * Формула: `доход = min(прошло, потолок) × ставка × эффективность`,
 * где ставка — средний доход в секунду из статистики, а эффективность
 * (существующая константа проекта) делает офлайн менее выгодным, чем
 * активную игру. Все вычисления идут через [MathUtility], собственных
 * экономических формул здесь нет.
 */
class OfflineProgressCalculator(
    private val defaultCapMillis: Long = GameConstants.OFFLINE_CAP_DEFAULT_MS,
) {

    /**
     * @param state сохранённое состояние с [GameState.lastSavedAtMillis]
     *   и [GameState.baselineProductionRate].
     * @param nowMillis текущее настенное время, epoch millis.
     * @param capMillis потолок начисления; по умолчанию два часа, восемь —
     *   с покупкой «Автоматический управляющий».
     */
    fun calculate(
        state: GameState,
        nowMillis: Long,
        capMillis: Long = defaultCapMillis,
    ): OfflineProgress {
        // Первый запуск: отметки времени ещё нет, начислять не за что.
        if (!state.isInitialized || state.lastSavedAtMillis <= 0L) return OfflineProgress()

        val elapsed = nowMillis - state.lastSavedAtMillis
        // Часы игрока могли уйти назад (перевод времени, часовой пояс).
        // Отрицательная разница — это не повод начислять или отнимать деньги.
        if (elapsed <= 0L) return OfflineProgress()

        val capped = elapsed.coerceAtMost(capMillis)
        val rate = state.baselineProductionRate

        val earned = MathUtility.offlineEarnings(
            elapsedMillis = elapsed,
            ratePerSecond = rate,
            capMillis = capMillis,
        )

        return OfflineProgress(
            elapsedMillis = elapsed,
            cappedMillis = capped,
            earnedCoins = earned,
        )
    }

    /** Применение начисленного дохода к состоянию. */
    fun apply(state: GameState, progress: OfflineProgress, nowMillis: Long): GameState =
        state.copy(
            coins = MathUtility.addCoins(state.coins, progress.earnedCoins),
            lastSavedAtMillis = nowMillis,
            lastTickAtMillis = nowMillis,
            stats = state.stats.copy(
                coinsEarned = state.stats.coinsEarned + progress.earnedCoins,
            ),
        )
}
