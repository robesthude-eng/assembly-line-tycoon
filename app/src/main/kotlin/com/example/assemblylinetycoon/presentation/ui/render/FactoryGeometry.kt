package com.example.assemblylinetycoon.presentation.ui.render

import com.example.assemblylinetycoon.domain.model.GridPosition
import kotlin.math.floor
import kotlin.math.min

/**
 * Перевод между координатами поля и пикселями холста.
 *
 * Вынесено из Compose намеренно: это чистая арифметика без единого
 * Android-типа, поэтому попадание касания в нужную клетку проверяется обычным
 * JVM-тестом. Обратное — считать координаты внутри `Canvas` — означало бы, что
 * единственный способ проверить попадание пальца это запустить эмулятор.
 *
 * Поле вписывается в холст целиком и центрируется: квадратная клетка при любом
 * соотношении сторон экрана, никакой прокрутки на этом этапе.
 */
data class FactoryGeometry(
    val gridWidth: Int,
    val gridHeight: Int,
    val canvasWidth: Float,
    val canvasHeight: Float,
) {
    /** Сторона клетки в пикселях. */
    val cellSize: Float = if (gridWidth <= 0 || gridHeight <= 0) {
        0f
    } else {
        min(canvasWidth / gridWidth, canvasHeight / gridHeight)
    }

    /** Отступ слева, чтобы поле стояло по центру. */
    val originX: Float = (canvasWidth - cellSize * gridWidth) / 2f

    /** Отступ сверху. */
    val originY: Float = (canvasHeight - cellSize * gridHeight) / 2f

    /** Левый край клетки. */
    fun left(x: Int): Float = originX + x * cellSize

    /** Верхний край клетки. */
    fun top(y: Int): Float = originY + y * cellSize

    /** Центр клетки по горизонтали. */
    fun centerX(x: Int): Float = left(x) + cellSize / 2f

    /** Центр клетки по вертикали. */
    fun centerY(y: Int): Float = top(y) + cellSize / 2f

    /**
     * Клетка под пальцем; `null`, если касание пришлось на поля вокруг сетки.
     *
     * Возврат `null` вместо ближайшей клетки — сознательный выбор: случайное
     * касание рамки не должно открывать диалог постройки.
     */
    fun cellAt(pixelX: Float, pixelY: Float): GridPosition? {
        if (cellSize <= 0f) return null
        val x = floor((pixelX - originX) / cellSize).toInt()
        val y = floor((pixelY - originY) / cellSize).toInt()
        return if (x in 0 until gridWidth && y in 0 until gridHeight) GridPosition(x, y) else null
    }

    /**
     * Положение едущего предмета: линейная интерполяция между центрами клеток
     * «откуда» и «куда» по доле пройденного пути.
     *
     * Позиция считается из состояния, а не накапливается в рендерере: иначе
     * картинка «уезжала» бы от симуляции при пропущенных кадрах.
     */
    fun interpolateX(from: GridPosition, to: GridPosition, progress: Float): Float =
        centerX(from.x) + (centerX(to.x) - centerX(from.x)) * progress.coerceIn(0f, 1f)

    /** См. [interpolateX]. */
    fun interpolateY(from: GridPosition, to: GridPosition, progress: Float): Float =
        centerY(from.y) + (centerY(to.y) - centerY(from.y)) * progress.coerceIn(0f, 1f)
}
