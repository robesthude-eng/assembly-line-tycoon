package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.engine.OfflineProgressCalculator
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.OfflineProgress

/**
 * Расчёт офлайн-дохода при запуске приложения.
 *
 * Сам расчёт живёт в [OfflineProgressCalculator] — он часть движка и
 * проверяется вместе с ним. Use case остаётся тонкой обёрткой: его задача —
 * дать презентации точку вызова, не раскрывая устройство движка.
 */
class CalculateOfflineProgressUseCase(
    private val calculator: OfflineProgressCalculator = OfflineProgressCalculator(),
) {
    operator fun invoke(state: GameState, nowMillis: Long, capMillis: Long): OfflineProgress =
        calculator.calculate(state = state, nowMillis = nowMillis, capMillis = capMillis)
}
