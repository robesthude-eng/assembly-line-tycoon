package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.repository.GameRepository

/** Загрузка сохранения при холодном старте. */
class LoadGameStateUseCase(
    private val gameRepository: GameRepository,
) : NoParamsUseCase<GameState> {
    override suspend fun invoke(): GameState = gameRepository.loadGameState()
}
