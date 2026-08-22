package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.repository.GameRepository

/**
 * Сохранение снапшота (автосейв, уход в фон, важные события).
 *
 * Отметку времени ставит именно этот use case, а не вызывающий код. Причина
 * простая: `lastSavedAtMillis` — единственная точка отсчёта офлайн-дохода,
 * и если её проставление оставить на совесть каждого места вызова, рано или
 * поздно появится путь сохранения без отметки. Тогда игрок либо не получит
 * ничего за отсутствие, либо получит начисление за всё время с прошлого раза.
 */
class SaveGameStateUseCase(
    private val gameRepository: GameRepository,
    private val timeProvider: TimeProvider,
) : SuspendUseCase<GameState, Unit> {

    override suspend fun invoke(params: GameState) {
        gameRepository.saveGameState(
            params.copy(
                lastSavedAtMillis = timeProvider.nowMillis(),
                // Сохранение состоялось — значит игра точно начата, и офлайн
                // при следующем запуске уже можно считать.
                isInitialized = true,
            ),
        )
    }
}
