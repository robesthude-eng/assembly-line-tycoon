package com.example.assemblylinetycoon.presentation.state

/**
 * Состояние главного экрана завода.
 *
 * Это **проекция** [com.example.assemblylinetycoon.domain.model.GameState] для
 * отрисовки, а не сама модель симуляции. UI не должен зависеть от внутренних
 * структур движка: иначе любое изменение симуляции ломает Compose-слой.
 */
data class GameUiState(
    val isLoading: Boolean = true,
    val coins: Long = 0L,
    val coinsPerSecond: Double = 0.0,
    val isBoostActive: Boolean = false,
    val boostRemainingMillis: Long = 0L,
    val isRewardedAdReady: Boolean = false,
    val isNoAdsPurchased: Boolean = false,
    val errorMessage: String? = null,
) : UiState
