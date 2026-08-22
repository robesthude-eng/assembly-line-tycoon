package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.repository.GameRepository

/** Сброс прогресса к новой игре (отладка и будущая кнопка «начать заново»). */
class ClearSaveDataUseCase(
    private val gameRepository: GameRepository,
) : NoParamsUseCase<Unit> {
    override suspend fun invoke() = gameRepository.clearSaveData()
}
