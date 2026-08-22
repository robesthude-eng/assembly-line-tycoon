package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.save.AutoSave

/**
 * Немедленное сохранение снапшота: уход с экрана, сворачивание, важное событие.
 *
 * Идёт через [AutoSave], а не напрямую в репозиторий, по двум причинам:
 * отметку времени и защиту от одновременных записей должен ставить кто-то
 * один, и этот кто-то — менеджер сохранений.
 */
class SaveGameStateUseCase(
    private val autoSave: AutoSave,
) : SuspendUseCase<GameState, Unit> {
    override suspend fun invoke(params: GameState) = autoSave.saveNow(params)
}
