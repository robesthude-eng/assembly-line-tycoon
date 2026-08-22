package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.domain.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Каркас игрового цикла на корутинах: тик 50 мс, состояние в [StateFlow].
 *
 * Симуляция (конвейеры, машины, экономика) намеренно не реализована — она
 * появится в [reduce] на следующем этапе. Каркас нужен, чтобы зафиксировать
 * однонаправленный поток данных: команда → reduce → новое состояние → UI.
 *
 * Цикл работает на [DispatcherProvider.default]: главный поток не занимается
 * симуляцией, Compose только читает готовое состояние.
 */
class GameLoop(
    private val dispatchers: DispatcherProvider,
    private val timeProvider: TimeProvider,
    private val tickIntervalMillis: Long = GameConstants.TICK_INTERVAL_MS,
) : GameEngine {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _state = MutableStateFlow(GameState.EMPTY)
    override val state: StateFlow<GameState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    override fun start(initialState: GameState) {
        _state.value = initialState
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var previous = timeProvider.elapsedRealtimeMillis()
            while (isActive) {
                delay(tickIntervalMillis)
                val now = timeProvider.elapsedRealtimeMillis()
                dispatch(GameCommand.Tick(deltaMillis = now - previous))
                previous = now
            }
        }
    }

    override fun stop() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun dispatch(command: GameCommand) {
        _state.update { current -> reduce(current, command) }
    }

    /** Чистая функция перехода состояния — единственное место мутации данных игры. */
    private fun reduce(current: GameState, command: GameCommand): GameState = when (command) {
        is GameCommand.Tick -> current                 // TODO(этап 3): шаг симуляции
        is GameCommand.ApplyOfflineEarnings -> current // TODO(этап 3): начисление офлайна
        is GameCommand.ApplyAdReward -> current        // TODO(этап 4): эффекты наград
        GameCommand.ResetProgress -> GameState.EMPTY
    }

    /** Освобождение ресурсов при уничтожении владельца движка. */
    fun dispose() {
        stop()
        scope.cancel()
    }
}
