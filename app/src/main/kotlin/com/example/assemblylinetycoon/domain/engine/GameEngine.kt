package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.domain.model.GameState
import kotlinx.coroutines.flow.StateFlow

/**
 * Контракт игрового движка.
 *
 * Движок — единственное место, где состояние меняется. Ни Compose, ни Canvas,
 * ни ViewModel не мутируют [GameState]: они только читают поток и шлют команды.
 */
interface GameEngine {

    /** Актуальное состояние симуляции. */
    val state: StateFlow<GameState>

    /** Запуск тикера с указанного состояния. */
    fun start(initialState: GameState)

    /** Остановка тикера (уход приложения в фон). */
    fun stop()

    /** Применение внешней команды к состоянию (покупка, апгрейд, награда). */
    fun dispatch(command: GameCommand)
}
