package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.domain.catalog.BeltCatalog
import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.Cell
import com.example.assemblylinetycoon.domain.model.CellType
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

    // ── Конвейер ────────────────────────────────────────────────────────────

    /** Сколько отрезков ленты уже проложено — вход для расчёта цены. */
    fun beltCount(state: GameState): Int = state.grid.cells.count { it.isBelt }

    /** Цена следующего отрезка конвейера. */
    fun beltCost(state: GameState): Long = BeltCatalog.buildCost(beltCount(state))

    /** Можно ли проложить ленту: место свободно и денег хватает. */
    fun canPlaceBelt(state: GameState, position: GridPosition): Boolean =
        isBuildable(state, position) && state.coins >= beltCost(state)

    /** Прокладка отрезка конвейера. */
    fun placeBelt(state: GameState, position: GridPosition, direction: Direction): GameState {
        if (!canPlaceBelt(state, position)) return state

        return state.copy(
            coins = state.coins - beltCost(state),
            grid = state.grid.withBelt(position, direction),
        )
    }

    /**
     * Поворот уже проложенной ленты — бесплатно (см. [BeltCatalog.ROTATE_COST]).
     *
     * Поворачивается только лента: у машины направление задаёт, куда она
     * выкладывает результат, и смена его на ходу выбросила бы готовый предмет
     * в стену. Разворот станков появится вместе с их переносом.
     */
    fun rotateBelt(state: GameState, position: GridPosition, direction: Direction): GameState {
        val cell = state.grid[position] ?: return state
        if (!cell.isBelt || cell.direction == direction) return state

        return state.copy(grid = state.grid.withBelt(position, direction))
    }

    // ── Снос ────────────────────────────────────────────────────────────────

    /** Есть ли в ячейке что сносить. */
    fun canDemolish(state: GameState, position: GridPosition): Boolean {
        val cell = state.grid[position] ?: return false
        return !cell.isEmpty
    }

    /**
     * Снос содержимого ячейки. **Деньги не возвращаются.**
     *
     * Возврат части стоимости — это множитель, которого нет ни в GDD, ни в
     * балансовой модели; вводить его заодно со сносом значило бы протащить
     * непроверенное экономическое решение. Без возврата снос честно остаётся
     * исправлением ошибки, а не способом заработать на перестройке.
     *
     * Предметы, ехавшие в снесённую клетку или из неё, исчезают вместе с ней:
     * иначе они зависли бы на несуществующей ленте и заблокировали соседей.
     */
    fun demolish(state: GameState, position: GridPosition): GameState {
        if (!canDemolish(state, position)) return state

        val machine = state.machineAt(position)
        return state.copy(
            grid = state.grid.cleared(position),
            machines = if (machine == null) state.machines else state.machines - machine.id,
            movingItems = state.movingItems.filterNot { it.from == position || it.to == position },
        )
    }

    /** Что стоит в ячейке — для выбора нужного диалога. */
    fun cellTypeAt(state: GameState, position: GridPosition): CellType =
        (state.grid[position] ?: Cell.EMPTY_CELL).type
}
