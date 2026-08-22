package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow

/** Подписка на состояние игры для presentation-слоя. */
class ObserveGameStateUseCase(
    private val gameRepository: GameRepository,
) {
    operator fun invoke(): Flow<GameState> = gameRepository.observeGameState()
}
