package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.save.AutoSave

/** Запуск периодического сохранения, пока игрок на экране завода. */
class StartAutoSaveUseCase(
    private val autoSave: AutoSave,
) {
    operator fun invoke(snapshot: () -> GameState) = autoSave.start(snapshot)
}

/** Остановка периодического сохранения при уходе с экрана. */
class StopAutoSaveUseCase(
    private val autoSave: AutoSave,
) {
    operator fun invoke() = autoSave.stop()
}
