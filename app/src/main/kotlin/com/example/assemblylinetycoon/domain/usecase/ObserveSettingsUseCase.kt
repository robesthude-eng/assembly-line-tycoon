package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.model.GameSettings
import com.example.assemblylinetycoon.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** Подписка на настройки и монетизационные флаги. */
class ObserveSettingsUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<GameSettings> = settingsRepository.observeSettings()
}
