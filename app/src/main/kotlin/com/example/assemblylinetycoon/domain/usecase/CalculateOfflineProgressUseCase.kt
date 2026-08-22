package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.OfflineProgress

/**
 * Расчёт офлайн-дохода: (сейчас − lastSavedAt), ограниченный потолком,
 * умноженный на baselineProductionRate.
 *
 * Формулы появятся на этапе игрового движка. Контракт зафиксирован сейчас,
 * чтобы экран приветствия можно было собрать заранее.
 */
class CalculateOfflineProgressUseCase {
    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(state: GameState, nowMillis: Long, capMillis: Long): OfflineProgress {
        // TODO(этап 3): реализовать расчёт по GDD (Offline Progress).
        return OfflineProgress()
    }
}
