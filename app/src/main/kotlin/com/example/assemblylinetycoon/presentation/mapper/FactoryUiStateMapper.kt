package com.example.assemblylinetycoon.presentation.mapper

import com.example.assemblylinetycoon.domain.catalog.ItemCatalog
import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.presentation.state.BoostUiState
import com.example.assemblylinetycoon.presentation.state.FactoryDialog
import com.example.assemblylinetycoon.presentation.state.FactoryRenderModel
import com.example.assemblylinetycoon.presentation.state.FactoryUiState
import com.example.assemblylinetycoon.presentation.state.MachineUiInfo

/**
 * Перевод состояния симуляции в состояние экрана.
 *
 * Вынесено из ViewModel в отдельный чистый объект по двум причинам:
 * проекцию можно проверить обычным JVM-тестом без Android, а ViewModel
 * остаётся тонкой — она только подписывается и раздаёт интенты.
 *
 * Правило слоя: здесь нет ни одной игровой формулы. Всё, что считается
 * (длительность такта, цена апгрейда, доход в секунду), берётся у домена
 * готовым; задача маппера — выбрать нужное и разложить по полям.
 */
object FactoryUiStateMapper {

    /**
     * @param previous предыдущее состояние экрана: выбор ячейки и открытый
     *   диалог принадлежат UI, а не симуляции, поэтому переносятся вручную.
     * @param nowMillis текущее время для плашки «Ускорения».
     */
    fun map(
        domain: GameState,
        previous: FactoryUiState = FactoryUiState(),
        nowMillis: Long = 0L,
    ): FactoryUiState {
        val selectedCell = previous.selectedCell?.takeIf { domain.grid.contains(it) }
        val selectedMachine = selectedCell?.let { domain.machineAt(it) }

        return previous.copy(
            isLoading = false,
            coins = domain.coins,
            coinsPerSecond = domain.stats.coinsPerSecond,
            render = FactoryRenderModel(
                grid = domain.grid,
                machines = domain.machines,
                movingItems = domain.movingItems,
                selectedCell = selectedCell,
                revision = domain.stats.simulatedMillis,
            ),
            selectedCell = selectedCell,
            selectedMachine = selectedMachine?.let { machineInfo(it, domain) },
            dialog = refreshDialog(previous.dialog, domain),
            boost = BoostUiState(
                isOverdriveActive = domain.isOverdriveActive(nowMillis),
                remainingMillis = (domain.overdriveUntilMillis - nowMillis).coerceAtLeast(0L),
            ),
        )
    }

    /** Выбор ячейки — состояние экрана, поэтому меняется здесь, а не в движке. */
    fun withSelectedCell(
        previous: FactoryUiState,
        position: GridPosition?,
        domain: GameState,
    ): FactoryUiState {
        val machine = position?.let { domain.machineAt(it) }
        return previous.copy(
            selectedCell = position,
            selectedMachine = machine?.let { machineInfo(it, domain) },
            render = previous.render.copy(selectedCell = position),
        )
    }

    /**
     * Пересборка карточки открытого диалога на свежих данных.
     *
     * Без этого полоса прогресса в диалоге замерла бы: диалог показывал бы
     * снимок машины на момент открытия, а завод тем временем продолжал бы
     * работать.
     */
    private fun refreshDialog(dialog: FactoryDialog, domain: GameState): FactoryDialog =
        when (dialog) {
            is FactoryDialog.MachineInfo -> domain.machines[dialog.machine.id]
                ?.let { FactoryDialog.MachineInfo(machineInfo(it, domain)) }
                ?: FactoryDialog.None   // машину снесли, пока диалог был открыт
            is FactoryDialog.EmptyCell -> if (domain.machineAt(dialog.position) == null) {
                dialog
            } else {
                FactoryDialog.None      // в ячейке уже что-то построили
            }
            FactoryDialog.None -> FactoryDialog.None
        }

    /** Карточка машины: значения берутся у домена, здесь только раскладка. */
    fun machineInfo(machine: Machine, domain: GameState): MachineUiInfo {
        val recipe = machine.recipeOutputId?.let(RecipeCatalog::forOutput)
        val duration = recipe
            ?.let { MachineCatalog.craftDuration(it.baseDurationMillis, machine.level) }
            ?: 0L
        val upgradeCost = machine.nextUpgradeCost()

        return MachineUiInfo(
            id = machine.id,
            type = machine.type,
            position = machine.position,
            level = machine.level,
            status = machine.status,
            progress = machine.progress(duration),
            outputItemName = machine.recipeOutputId?.let { ItemCatalog.find(it)?.displayName },
            craftDurationMillis = duration,
            upgradeCost = upgradeCost,
            canAffordUpgrade = domain.coins >= upgradeCost,
        )
    }
}
