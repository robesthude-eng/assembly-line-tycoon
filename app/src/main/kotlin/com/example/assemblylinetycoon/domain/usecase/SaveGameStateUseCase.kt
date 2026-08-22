package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.repository.GameRepository

/** Сохранение снапшота (автосейв, уход в фон, важные события). */
class SaveGameStateUseCase(
    private val gameRepository: GameRepository,
) : SuspendUseCase<GameState, Unit> {
    override suspend fun invoke(params: GameState) = gameRepository.saveGameState(params)
}
