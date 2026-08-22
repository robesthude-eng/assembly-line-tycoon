package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.core.constants.GameConstants
import kotlinx.serialization.Serializable

/** Что занимает ячейку поля. */
@Serializable
enum class CellType {
    /** Свободное место, доступное для постройки. */
    EMPTY,

    /** Отрезок конвейера. */
    BELT,

    /** Ячейка, занятая машиной. */
    MACHINE,
}

/**
 * Ячейка завода.
 *
 * Конвейер хранит не «предмет в координате», а предмет с долей пройденного
 * пути: так рендерер плавно двигает спрайт между центрами клеток, а симуляция
 * остаётся дискретной и детерминированной.
 *
 * @param machineId ссылка на машину, если [type] = [CellType.MACHINE].
 * @param item предмет, находящийся на ленте.
 * @param itemProgress доля пути от 0 до 1. Значение 1 означает, что предмет
 *   упёрся в занятую следующую ячейку — это и есть противодавление.
 */
@Serializable
data class Cell(
    val type: CellType = CellType.EMPTY,
    val direction: Direction = Direction.RIGHT,
    val machineId: Int? = null,
    val item: ItemId? = null,
    val itemProgress: Float = 0f,
) {
    val isEmpty: Boolean get() = type == CellType.EMPTY
    val isBelt: Boolean get() = type == CellType.BELT

    /** Занята ли лента предметом — ключевая проверка для противодавления. */
    val isOccupied: Boolean get() = item != null

    /** Предмет доехал до конца ячейки и ждёт передачи дальше. */
    val isBlocked: Boolean get() = item != null && itemProgress >= 1f

    companion object {
        val EMPTY_CELL = Cell()
    }
}

/**
 * Поле завода: плоский список ячеек фиксированного размера.
 *
 * Список вместо карты выбран сознательно — обход в тике идёт по всем ячейкам,
 * а плоский массив даёт предсказуемую скорость и компактный JSON сохранения.
 */
@Serializable
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

    /** Возвращает копию поля с заменённой ячейкой; вне границ — исходное поле. */
    fun withCell(position: GridPosition, cell: Cell): FactoryGrid {
        if (!position.isInside(width, height)) return this
        val updated = cells.toMutableList()
        updated[position.toIndex(width)] = cell
        return copy(cells = updated)
    }

    /** Все занятые ячейки с координатами — вход для Canvas-рендерера. */
    fun occupiedCells(): List<Pair<GridPosition, Cell>> =
        cells.mapIndexedNotNull { index, cell ->
            if (cell.isEmpty && cell.item == null) null
            else GridPosition.fromIndex(index, width) to cell
        }

    companion object {
        val EMPTY = FactoryGrid()
    }
}
