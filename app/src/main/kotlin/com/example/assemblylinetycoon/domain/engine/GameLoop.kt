package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.core.utils.MathUtility
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
 * Игровой цикл: тикер на корутинах и единственная точка мутации состояния.
 *
 * Разделение обязанностей:
 *  * [GameLoop] отвечает за время — когда случается такт и какой длины;
 *  * [FactorySimulation] отвечает за содержание такта — что при этом
 *    происходит с заводом.
 *
 * Такая пара позволяет проверять симуляцию без корутин вовсе, а тикер —
 * на виртуальном времени `runTest`, не ожидая реальных миллисекунд.
 *
 * Цикл работает на [DispatcherProvider.default]: главный поток не занимается
 * симуляцией, Compose только читает готовое состояние из [state].
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
            // Монотонное время, а не настенное: перевод часов игроком не
            // должен превращаться в гигантскую дельту симуляции.
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

    /**
     * Чистая функция перехода состояния — единственное место мутации данных.
     *
     * Открыта для тестов: шаг симуляции можно проверить, не запуская тикер.
     */
    internal fun reduce(current: GameState, command: GameCommand): GameState = when (command) {
        is GameCommand.Tick -> FactorySimulation.step(current, command.deltaMillis)
            .copy(lastTickAtMillis = timeProvider.nowMillis())

        is GameCommand.ApplyOfflineEarnings -> current.copy(
            coins = MathUtility.addCoins(current.coins, command.coins),
            stats = current.stats.copy(
                coinsEarned = current.stats.coinsEarned + command.coins,
            ),
        )

        // Награды за рекламу реализуются на этапе монетизации: движок уже
        // принимает команду, чтобы поток данных не пришлось перестраивать.
        is GameCommand.ApplyAdReward -> current

        GameCommand.ResetProgress -> GameState.EMPTY
    }

    /** Освобождение ресурсов при уничтожении владельца движка. */
    fun dispose() {
        stop()
        scope.cancel()
    }
}
