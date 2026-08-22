package com.example.assemblylinetycoon.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.domain.engine.FactoryBuilder
import com.example.assemblylinetycoon.domain.engine.GameCommand
import com.example.assemblylinetycoon.domain.engine.GameEngine
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.usecase.CalculateOfflineProgressUseCase
import com.example.assemblylinetycoon.domain.usecase.LoadGameStateUseCase
import com.example.assemblylinetycoon.domain.usecase.ObserveSettingsUseCase
import com.example.assemblylinetycoon.domain.usecase.SaveGameStateUseCase
import com.example.assemblylinetycoon.domain.usecase.StartAutoSaveUseCase
import com.example.assemblylinetycoon.domain.usecase.StopAutoSaveUseCase
import com.example.assemblylinetycoon.presentation.mapper.FactoryUiStateMapper
import com.example.assemblylinetycoon.presentation.state.FactoryDialog
import com.example.assemblylinetycoon.presentation.state.FactoryEffect
import com.example.assemblylinetycoon.presentation.state.FactoryIntent
import com.example.assemblylinetycoon.presentation.state.FactoryUiState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel экрана завода.
 *
 * Обязанности ровно три: подписаться на движок, спроецировать его состояние
 * в [FactoryUiState] и перевести действия игрока в команды домена.
 *
 * Чего здесь нет и быть не должно: расчёта производства, движения предметов,
 * обработки рецептов. Всё это живёт в `domain/engine` и проверено отдельными
 * тестами — дублировать логику в presentation означало бы завести второй,
 * неизбежно расходящийся источник правды.
 */
class FactoryViewModel(
    private val gameEngine: GameEngine,
    private val loadGameState: LoadGameStateUseCase,
    private val saveGameState: SaveGameStateUseCase,
    private val startAutoSave: StartAutoSaveUseCase,
    private val stopAutoSave: StopAutoSaveUseCase,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val timeProvider: TimeProvider,
) : MviViewModel<FactoryUiState, FactoryIntent, FactoryEffect>(FactoryUiState()) {

    init {
        observeEngine()
        observeUserSettings()
    }

    private fun observeEngine() {
        gameEngine.state
            .onEach { domainState ->
                setState {
                    FactoryUiStateMapper.map(
                        domain = domainState,
                        previous = this,
                        nowMillis = timeProvider.nowMillis(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeUserSettings() {
        observeSettings()
            .onEach { /* настройки влияют на монетизацию, этап 5 */ }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: FactoryIntent) {
        when (intent) {
            FactoryIntent.ScreenStarted -> viewModelScope.launch { startGame() }

            FactoryIntent.ScreenStopped -> viewModelScope.launch {
                stopAutoSave()
                gameEngine.stop()
                // Сохранение при уходе с экрана обязательно: следующим шагом
                // система может убить процесс без всякого предупреждения.
                saveGameState(gameEngine.state.value)
            }

            FactoryIntent.OfflineEarningsClaimed -> setState { copy(dialog = FactoryDialog.None) }

            is FactoryIntent.SelectCell -> selectCell(intent)

            is FactoryIntent.OpenMachineDialog -> openMachineDialog(intent.machineId)

            is FactoryIntent.PlaceMachine -> placeMachine(intent)

            is FactoryIntent.UpgradeMachine -> upgradeMachine(intent.machineId)

            is FactoryIntent.PlaceBelt -> placeBelt(intent)

            is FactoryIntent.RotateBelt -> gameEngine.dispatch(
                // Поворот бесплатен, проверять нечего: движок сам откажет,
                // если ленту успели снести.
                GameCommand.RotateBelt(intent.position, intent.direction),
            )

            is FactoryIntent.Demolish -> demolish(intent.position)

            FactoryIntent.CloseDialog -> setState { copy(dialog = FactoryDialog.None) }

            FactoryIntent.ErrorDismissed -> setState { copy(errorMessage = null) }
        }
    }

    /**
     * Холодный старт: загрузка, начисление за отсутствие, запуск симуляции.
     *
     * Порядок важен. Офлайн-доход считается по загруженному снапшоту **до**
     * старта движка: стоит движку сделать первый тик, и `lastSavedAtMillis`
     * перестанет описывать момент, когда игрок ушёл.
     */
    private suspend fun startGame() {
        val saved = loadGameState()
        val now = timeProvider.nowMillis()

        val progress = calculateOfflineProgress(
            state = saved,
            nowMillis = now,
            capMillis = GameConstants.OFFLINE_CAP_DEFAULT_MS,
        )

        gameEngine.start(saved)
        if (progress.isSignificant) {
            // Начисление идёт командой, как любое изменение состояния:
            // ViewModel не имеет права трогать баланс напрямую.
            gameEngine.dispatch(GameCommand.ApplyOfflineEarnings(progress.earnedCoins))
            setState {
                copy(
                    dialog = FactoryDialog.OfflineEarnings(
                        coins = progress.earnedCoins,
                        awayMillis = progress.elapsedMillis,
                        cappedByLimit = progress.elapsedMillis > progress.cappedMillis,
                    ),
                )
            }
        }

        // Периодическую запись ведёт менеджер сохранений в скоупе приложения:
        // автосейв обязан пережить и поворот экрана, и уход ViewModel.
        startAutoSave { gameEngine.state.value }
    }

    override fun onCleared() {
        stopAutoSave()
        super.onCleared()
    }

    /**
     * Одно касание делает две вещи: выделяет ячейку и открывает нужный диалог.
     * Разделение «тап выделяет, долгий тап открывает» на сенсорном экране
     * плохо обнаруживается, поэтому карточка показывается сразу.
     */
    private fun selectCell(intent: FactoryIntent.SelectCell) {
        val domain = gameEngine.state.value
        if (!domain.grid.contains(intent.position)) return

        val machine = domain.machineAt(intent.position)
        val cell = domain.grid[intent.position]
        setState {
            FactoryUiStateMapper.withSelectedCell(this, intent.position, domain).copy(
                dialog = when {
                    machine != null -> FactoryDialog.MachineInfo(
                        FactoryUiStateMapper.machineInfo(machine, domain),
                    )

                    cell?.isBelt == true -> FactoryDialog.BeltCell(
                        position = intent.position,
                        direction = cell.direction,
                    )

                    else -> FactoryUiStateMapper.emptyCellDialog(domain, intent.position)
                },
            )
        }
    }

    /**
     * Постройка: проверка «можно ли» и команда движку.
     *
     * Деньги списывает движок, он же решает, состоится покупка или нет.
     * Проверка здесь нужна лишь для внятного сообщения игроку: молча
     * проглоченное нажатие выглядит как поломка.
     */
    private fun placeMachine(intent: FactoryIntent.PlaceMachine) {
        val domain = gameEngine.state.value
        if (!FactoryBuilder.canPlace(domain, intent.position, intent.type)) {
            sendEffect(FactoryEffect.ShowMessage("Не хватает монет"))
            return
        }

        gameEngine.dispatch(GameCommand.PlaceMachine(intent.position, intent.type))
        // Диалог закрывается сам: ячейка перестала быть пустой, и держать
        // открытым магазин для занятого места незачем.
        setState { copy(dialog = FactoryDialog.None) }
    }

    private fun placeBelt(intent: FactoryIntent.PlaceBelt) {
        if (!FactoryBuilder.canPlaceBelt(gameEngine.state.value, intent.position)) {
            sendEffect(FactoryEffect.ShowMessage("Не хватает монет"))
            return
        }

        gameEngine.dispatch(GameCommand.PlaceBelt(intent.position, intent.direction))
        setState { copy(dialog = FactoryDialog.None) }
    }

    private fun demolish(position: GridPosition) {
        if (!FactoryBuilder.canDemolish(gameEngine.state.value, position)) return

        gameEngine.dispatch(GameCommand.Demolish(position))
        // Диалог показывает то, чего больше нет, — закрываем его сами.
        setState { copy(dialog = FactoryDialog.None) }
    }

    private fun upgradeMachine(machineId: Int) {
        if (!FactoryBuilder.canUpgrade(gameEngine.state.value, machineId)) {
            sendEffect(FactoryEffect.ShowMessage("Не хватает монет"))
            return
        }

        gameEngine.dispatch(GameCommand.UpgradeMachine(machineId))
    }

    private fun openMachineDialog(machineId: Int) {
        val domain = gameEngine.state.value
        val machine = domain.machines[machineId] ?: return
        setState {
            FactoryUiStateMapper.withSelectedCell(this, machine.position, domain).copy(
                dialog = FactoryDialog.MachineInfo(
                    FactoryUiStateMapper.machineInfo(machine, domain),
                ),
            )
        }
    }
}
