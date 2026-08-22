package com.example.assemblylinetycoon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.assemblylinetycoon.app.AppContainer

/**
 * Ручная фабрика ViewModel'ей.
 *
 * DI-фреймворк намеренно не подключён: у игры один граф зависимостей и один
 * экран-владелец состояния. Hilt добавит время сборки и кодогенерацию без
 * реальной пользы; при росте проекта фабрика заменяется точечно.
 */
class ViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(FactoryViewModel::class.java) -> FactoryViewModel(
            gameEngine = container.gameEngine,
            loadGameState = container.loadGameStateUseCase,
            saveGameState = container.saveGameStateUseCase,
            calculateOfflineProgress = container.calculateOfflineProgressUseCase,
            observeSettings = container.observeSettingsUseCase,
            timeProvider = container.timeProvider,
        ) as T

        else -> error("Неизвестный ViewModel: ${modelClass.name}")
    }
}
