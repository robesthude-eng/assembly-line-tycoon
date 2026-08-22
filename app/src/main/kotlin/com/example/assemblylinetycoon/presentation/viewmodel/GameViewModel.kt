package com.example.assemblylinetycoon.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.assemblylinetycoon.domain.engine.GameEngine
import com.example.assemblylinetycoon.domain.usecase.LoadGameStateUseCase
import com.example.assemblylinetycoon.domain.usecase.ObserveSettingsUseCase
import com.example.assemblylinetycoon.domain.usecase.SaveGameStateUseCase
import com.example.assemblylinetycoon.presentation.state.GameEffect
import com.example.assemblylinetycoon.presentation.state.GameIntent
import com.example.assemblylinetycoon.presentation.state.GameUiState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel главного экрана.
 *
 * Обязанности: подписаться на движок, спроецировать [GameState] в [GameUiState],
 * перевести интенты в команды движка. Никаких расчётов экономики здесь нет и
 * быть не должно — этим занимается domain/engine.
 */
class GameViewModel(
    private val gameEngine: GameEngine,
    private val loadGameState: LoadGameStateUseCase,
    private val saveGameState: SaveGameStateUseCase,
    private val observeSettings: ObserveSettingsUseCase,
) : MviViewModel<GameUiState, GameIntent, GameEffect>(GameUiState()) {

    init {
        observeEngine()
        observeUserSettings()
    }

    private fun observeEngine() {
        gameEngine.state
            .onEach { domainState ->
                setState {
                    copy(
                        isLoading = false,
                        coins = domainState.coins,
                        // TODO(этап 3): остальные поля проекции
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeUserSettings() {
        observeSettings()
            .onEach { settings ->
                setState { copy(isNoAdsPurchased = settings.noAdsPurchased) }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: GameIntent) {
        when (intent) {
            GameIntent.ScreenStarted -> viewModelScope.launch {
                gameEngine.start(loadGameState())
            }

            GameIntent.ScreenStopped -> viewModelScope.launch {
                gameEngine.stop()
                saveGameState(gameEngine.state.value)
            }

            is GameIntent.RewardedAdRequested -> sendEffect(
                GameEffect.ShowRewardedAd(intent.placement),
            )

            is GameIntent.CellTapped -> Unit          // TODO(этап 3): команда движку
            GameIntent.OfflineRewardClaimed -> Unit   // TODO(этап 3)
            GameIntent.ErrorDismissed -> setState { copy(errorMessage = null) }
        }
    }
}
