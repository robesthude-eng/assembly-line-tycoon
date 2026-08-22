package com.example.assemblylinetycoon.presentation.state

import com.example.assemblylinetycoon.domain.model.AdPlacement

/**
 * Разовые эффекты: то, что нельзя выразить состоянием.
 *
 * Показ рекламы — именно эффект, а не состояние: если положить его в
 * [GameUiState], ролик покажется повторно при повороте экрана.
 */
sealed interface GameEffect : UiEffect {
    data class ShowRewardedAd(val placement: AdPlacement) : GameEffect
    data class ShowMessage(val text: String) : GameEffect
    data object NavigateToSettings : GameEffect
}
