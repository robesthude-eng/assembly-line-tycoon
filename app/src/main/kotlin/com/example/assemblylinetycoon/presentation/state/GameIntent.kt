package com.example.assemblylinetycoon.presentation.state

import com.example.assemblylinetycoon.domain.model.AdPlacement

/**
 * Намерения пользователя на главном экране.
 *
 * Никаких «сырых» координат касания и Compose-типов: интент описывает смысл
 * действия, а не способ ввода.
 */
sealed interface GameIntent : UiIntent {
    data object ScreenStarted : GameIntent
    data object ScreenStopped : GameIntent
    data class RewardedAdRequested(val placement: AdPlacement) : GameIntent
    data class CellTapped(val x: Int, val y: Int) : GameIntent
    data object OfflineRewardClaimed : GameIntent
    data object ErrorDismissed : GameIntent
}
