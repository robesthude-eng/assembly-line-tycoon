package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.core.constants.GameConstants
import kotlinx.serialization.Serializable

/**
 * Координата ячейки завода. Начало координат — левый верхний угол,
 * ось Y растёт вниз, как в системе координат Canvas.
 *
 * Это доменный тип, а не `Offset` из Compose: слой домена не знает об Android,
 * а перевод в пиксели — задача рендерера.
 */
@Serializable
data class GridPosition(val x: Int, val y: Int) {

    /** Соседняя ячейка в направлении [direction]. Границы не проверяются. */
    fun neighbor(direction: Direction): GridPosition =
        GridPosition(x + direction.dx, y + direction.dy)

    /** Лежит ли координата внутри поля заданного размера. */
    fun isInside(
        width: Int = GameConstants.GRID_WIDTH,
        height: Int = GameConstants.GRID_HEIGHT,
    ): Boolean = x in 0 until width && y in 0 until height

    /**
     * Плоский индекс для хранения сетки одним списком.
     * Список дешевле карты по памяти и стабильнее при сериализации.
     */
    fun toIndex(width: Int = GameConstants.GRID_WIDTH): Int = y * width + x

    companion object {
        val ORIGIN = GridPosition(0, 0)

        fun fromIndex(index: Int, width: Int = GameConstants.GRID_WIDTH): GridPosition =
            GridPosition(x = index % width, y = index / width)
    }
}
