package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.core.constants.GameConstants

/**
 * Что занимает ячейку поля.
 *
 * Карьер и экспортёр вынесены в отдельные типы, хотя оба являются машинами:
 * симуляции и рендереру постоянно нужно отвечать на вопросы «куда можно
 * сбросить предмет» и «где кончается цепочка», и делать это по типу ячейки
 * дешевле, чем каждый раз искать машину по идентификатору.
 */
enum class CellType {
    /** Свободное место, доступное для постройки. */
    EMPTY,

    /** Отрезок конвейера. */
    BELT,

    /** Ячейка, занятая перерабатывающей машиной. */
    MACHINE,

    /** Карьер: источник сырья, входов не принимает. */
    SPAWNER,

    /** Экспортёр: сток, превращает предметы в монеты. */
    EXPORTER;

    /** Может ли предмет въехать в такую ячейку. */
    val acceptsItems: Boolean get() = this == BELT || this == MACHINE || this == EXPORTER

    /** Стоит ли в ячейке машина любого рода. */
    val holdsMachine: Boolean get() = this == MACHINE || this == SPAWNER || this == EXPORTER

    companion object {
        /** Тип ячейки, соответствующий типу машины. */
        fun forMachine(type: MachineType): CellType = when (type) {
            MachineType.SPAWNER -> SPAWNER
            MachineType.EXPORTER -> EXPORTER
            else -> MACHINE
        }
    }
}

/**
 * Ячейка завода.
 *
 * Предметы в ячейке не хранятся: они живут в `GameState.movingItems` как
 * [MovingItem]. Иначе одно и то же состояние описывалось бы в двух местах,
 * и рассинхронизация была бы вопросом времени.
 *
 * @param direction для ленты — куда она толкает предмет.
 * @param machineId ссылка на машину, если в ячейке стоит оборудование.
 */
data class Cell(
    val type: CellType = CellType.EMPTY,
    val direction: Direction = Direction.RIGHT,
    val machineId: Int? = null,
) {
    val isEmpty: Boolean get() = type == CellType.EMPTY
    val isBelt: Boolean get() = type == CellType.BELT

    companion object {
        val EMPTY_CELL = Cell()

        fun belt(direction: Direction): Cell = Cell(type = CellType.BELT, direction = direction)

        fun machine(machine: Machine): Cell = Cell(
            type = CellType.forMachine(machine.type),
            direction = machine.facing,
            machineId = machine.id,
        )
    }
}

/**
 * Поле завода: плоский список ячеек фиксированного размера.
 *
 * Список вместо карты выбран сознательно — обход в тике идёт по всем ячейкам,
 * а плоский массив даёт предсказуемую скорость и компактный JSON сохранения.
 */
data class FactoryGrid(
    val width: Int = GameConstants.GRID_WIDTH,
    val height: Int = GameConstants.GRID_HEIGHT,
    val cells: List<Cell> = List(GameConstants.GRID_WIDTH * GameConstants.GRID_HEIGHT) { Cell.EMPTY_CELL },
) {
    init {
        require(cells.size == width * height) {
            "Размер списка ячеек ${cells.size} не совпадает с полем ${width}x$height"
        }
    }

    operator fun get(position: GridPosition): Cell? =
        if (position.isInside(width, height)) cells[position.toIndex(width)] else null

    fun contains(position: GridPosition): Boolean = position.isInside(width, height)

    /** Возвращает копию поля с заменённой ячейкой; вне границ — исходное поле. */
    fun withCell(position: GridPosition, cell: Cell): FactoryGrid {
        if (!contains(position)) return this
        val updated = cells.toMutableList()
        updated[position.toIndex(width)] = cell
        return copy(cells = updated)
    }

    /** Поставить отрезок конвейера. */
    fun withBelt(position: GridPosition, direction: Direction): FactoryGrid =
        withCell(position, Cell.belt(direction))

    /** Поставить машину: ячейка получает тип, соответствующий её роли. */
    fun withMachine(machine: Machine): FactoryGrid =
        withCell(machine.position, Cell.machine(machine))

    /** Снести содержимое ячейки. */
    fun cleared(position: GridPosition): FactoryGrid = withCell(position, Cell.EMPTY_CELL)

    /** Все непустые ячейки с координатами — вход для Canvas-рендерера. */
    fun occupiedCells(): List<Pair<GridPosition, Cell>> =
        cells.mapIndexedNotNull { index, cell ->
            if (cell.isEmpty) null else GridPosition.fromIndex(index, width) to cell
        }

    companion object {
        val EMPTY = FactoryGrid()
    }
}
