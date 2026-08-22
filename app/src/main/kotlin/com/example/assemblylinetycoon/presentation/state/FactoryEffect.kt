package com.example.assemblylinetycoon.presentation.state

/**
 * Разовые эффекты экрана завода: то, что нельзя выразить состоянием.
 *
 * Сообщение о недоступной функции — именно эффект: если положить его в
 * [FactoryUiState], оно всплывёт заново при каждом повороте экрана.
 */
sealed interface FactoryEffect : UiEffect {

    /** Короткое сообщение игроку (снекбар). */
    data class ShowMessage(val text: String) : FactoryEffect

    /** Функция появится на следующем этапе разработки. */
    data class NotImplementedYet(val feature: String) : FactoryEffect
}
