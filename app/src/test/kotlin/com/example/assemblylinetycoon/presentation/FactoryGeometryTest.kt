package com.example.assemblylinetycoon.presentation

import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.presentation.ui.render.FactoryGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Геометрия холста: перевод пикселей в клетки и обратно.
 *
 * Именно здесь живёт вся арифметика рендерера, поэтому попадание пальца в
 * нужную клетку проверяется без эмулятора и без Compose.
 */
class FactoryGeometryTest {

    /** Поле 10×10 на широком экране: клетка ограничена высотой. */
    private val wide = FactoryGeometry(
        gridWidth = 10,
        gridHeight = 10,
        canvasWidth = 1_000f,
        canvasHeight = 500f,
    )

    private val square = FactoryGeometry(
        gridWidth = 10,
        gridHeight = 10,
        canvasWidth = 500f,
        canvasHeight = 500f,
    )

    @Test // клетка квадратная: поле вписывается по меньшей стороне
    fun cellIsSquareAndFitsCanvas() {
        assertEquals(50f, wide.cellSize, 0.001f)
        assertEquals(50f, square.cellSize, 0.001f)
    }

    @Test // поле центрируется, а не прижимается к углу
    fun gridIsCentered() {
        // По ширине осталось 1000 - 500 = 500 пикселей, поровну с двух сторон.
        assertEquals(250f, wide.originX, 0.001f)
        assertEquals(0f, wide.originY, 0.001f)
    }

    @Test // касание попадает ровно в ту клетку, на которую пришлось
    fun tapResolvesToCell() {
        assertEquals(GridPosition(0, 0), square.cellAt(1f, 1f))
        assertEquals(GridPosition(3, 4), square.cellAt(3 * 50f + 25f, 4 * 50f + 25f))
        assertEquals(GridPosition(9, 9), square.cellAt(499f, 499f))
    }

    @Test // границы клеток не «съезжают» на соседнюю
    fun cellBordersBelongToTheNextCell() {
        assertEquals(GridPosition(0, 0), square.cellAt(49.9f, 49.9f))
        assertEquals(GridPosition(1, 1), square.cellAt(50f, 50f))
    }

    @Test // касание полей вокруг сетки не выбирает ничего
    fun tapOutsideGridIsIgnored() {
        // Слева от центрированного поля — пустое место, а не клетка (0, y).
        assertNull(wide.cellAt(10f, 250f))
        assertNull(square.cellAt(-5f, 10f))
        assertNull(square.cellAt(10f, 501f))
    }

    @Test // вырожденный холст не роняет рендерер делением на ноль
    fun emptyCanvasIsSafe() {
        val zero = FactoryGeometry(gridWidth = 10, gridHeight = 10, canvasWidth = 0f, canvasHeight = 0f)

        assertEquals(0f, zero.cellSize, 0.001f)
        assertNull(zero.cellAt(0f, 0f))
    }

    @Test // предмет едет между центрами клеток пропорционально прогрессу
    fun itemPositionIsInterpolated() {
        val from = GridPosition(1, 1)
        val to = GridPosition(2, 1)

        assertEquals(square.centerX(1), square.interpolateX(from, to, 0f), 0.001f)
        assertEquals(square.centerX(2), square.interpolateX(from, to, 1f), 0.001f)
        assertEquals(
            (square.centerX(1) + square.centerX(2)) / 2f,
            square.interpolateX(from, to, 0.5f),
            0.001f,
        )
        // Движение по горизонтали не меняет вертикаль.
        assertEquals(square.centerY(1), square.interpolateY(from, to, 0.5f), 0.001f)
    }

    @Test // прогресс за пределами 0..1 не выбрасывает спрайт за клетку
    fun interpolationIsClamped() {
        val from = GridPosition(0, 0)
        val to = GridPosition(1, 0)

        assertEquals(square.centerX(0), square.interpolateX(from, to, -3f), 0.001f)
        assertEquals(square.centerX(1), square.interpolateX(from, to, 5f), 0.001f)
    }

    @Test // центр клетки лежит внутри её границ
    fun centersAreInsideCells() {
        val x = 6
        assertTrue(square.centerX(x) > square.left(x))
        assertTrue(square.centerX(x) < square.left(x + 1))
    }
}
