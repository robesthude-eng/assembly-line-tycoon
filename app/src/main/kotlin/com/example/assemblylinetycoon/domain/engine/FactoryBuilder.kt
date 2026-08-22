package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineType

/**
 * Строительство и улучшение оборудования.
 *
 * Чистые функции над [GameState], как и вся симуляция: ни корутин, ни времени,
 * ни Android. Отделено от [FactorySimulation] потому, что это разные вещи —
 * там течёт время само по себе, здесь состояние меняется по воле игрока.
 *
 * Правило слоя: цены берутся исключительно из [MachineCatalog]. Ни одна
 * формула стоимости здесь не пишется заново — иначе цена в магазине и цена
 * при списании денег однажды разойдутся.
 *
 * Отказ выражается возвратом **того же самого состояния**: движок не умеет
 * показывать диалоги, а UI и так знает, хватает ли денег, — он спрашивает об
 * этом [canPlace] и [canUpgrade] до отправки команды.
 */
object FactoryBuilder {

    /** Направление, в которое смотрит только что построенная машина. */
    val DEFAULT_FACING: Direction = Direction.RIGHT

    /**
     * Цена постройки машины типа [type] прямо сейчас.
     *
     * Зависит от того, сколько таких уже стоит на поле: множитель
     * `costGrowth` превращает «поставить ещё один ассемблер» из очевидного
     * решения в выбор.
     */
    fun buildCost(state: GameState, type: MachineType): Long =
        MachineCatalog.buildCost(type, ownedCount = state.machineCount(type))

    /** Цена следующего уровня машины; `null`, если такой машины нет. */
    fun upgradeCost(state: GameState, machineId: Int): Long? =
        state.machines[machineId]?.nextUpgradeCost()

    /** Свободна ли ячейка под постройку: внутри поля и ничем не занята. */
    fun isBuildable(state: GameState, position: GridPosition): Boolean {
        val cell = state.grid[position] ?: return false
        return cell.isEmpty && state.machineAt(position) == null
    }

    /** Можно ли построить: место свободно и денег достаточно. */
    fun canPlace(state: GameState, position: GridPosition, type: MachineType): Boolean =
        isBuildable(state, position) && state.coins >= buildCost(state, type)

    /** Можно ли улучшить: машина существует и денег достаточно. */
    fun canUpgrade(state: GameState, machineId: Int): Boolean {
        val cost = upgradeCost(state, machineId) ?: return false
        return state.coins >= cost
    }

    /**
     * Постройка машины.
     *
     * Идентификатор берётся из счётчика [GameState.nextMachineId] и только
     * растёт: переиспользование номеров снесённых машин сломало бы ссылки из
     * ячеек поля и открытых диалогов.
     *
     * @return новое состояние либо исходное, если построить нельзя.
     */
    fun place(state: GameState, position: GridPosition, type: MachineType): GameState {
        if (!canPlace(state, position, type)) return state

        val cost = buildCost(state, type)
        val machine = Machine(
            id = state.nextMachineId,
            type = type,
            position = position,
            facing = DEFAULT_FACING,
            level = 0,
            // Рецепт по умолчанию — первый доступный этой машине. Выбор
            // рецепта игроком появится вместе с экраном настройки машины;
            // до тех пор станок должен работать сразу после постройки.
            recipeOutputId = defaultRecipeFor(type),
        )

        return state.copy(
            coins = state.coins - cost,
            machines = state.machines + (machine.id to machine),
            grid = state.grid.withMachine(machine),
            nextMachineId = state.nextMachineId + 1,
        )
    }

    /**
     * Улучшение машины: минус деньги, плюс уровень.
     *
     * Накопленный прогресс такта не сбрасывается. Игрок платит за ускорение,
     * и отматывать ему почти доделанную деталь было бы наказанием за покупку;
     * длительность такта пересчитывается на лету в [FactorySimulation].
     */
    fun upgrade(state: GameState, machineId: Int): GameState {
        if (!canUpgrade(state, machineId)) return state
        val machine = state.machines[machineId] ?: return state
        val cost = machine.nextUpgradeCost()
        val upgraded = machine.copy(level = machine.level + 1)

        return state.copy(
            coins = state.coins - cost,
            machines = state.machines + (machineId to upgraded),
            // Ячейка хранит ссылку на машину, поэтому переписывать её не
            // нужно: тип и направление при улучшении не меняются.
        )
    }

    /** Что машина этого типа начнёт производить сразу после постройки. */
    fun defaultRecipeFor(type: MachineType): String? =
        RecipeCatalog.forMachine(type).firstOrNull()?.outputItemId

    /** Список доступного к постройке оборудования, от дешёвого к дорогому. */
    fun purchasableTypes(): List<MachineType> = MachineCatalog.purchasable()
}
