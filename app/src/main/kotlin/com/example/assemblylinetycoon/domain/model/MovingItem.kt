package com.example.assemblylinetycoon.domain.model

import kotlinx.serialization.Serializable

/**
 * Предмет, едущий по конвейеру.
 *
 * Позиция хранится парой «откуда — куда» плюс доля пути [progress]:
 * симуляция остаётся дискретной (предмет всегда принадлежит одной паре
 * клеток), а рендерер может плавно интерполировать спрайт между их центрами.
 *
 * Ячейка [to] считается занятой этим предметом с момента начала движения —
 * так реализовано «не больше одного предмета на клетку ленты»: пока предмет
 * едет, никто другой в целевую клетку не въедет.
 *
 * @param progress доля пути от 0 до 1. Значение 1 означает, что предмет
 *   доехал и ждёт: следующая клетка занята. Это и есть противодавление.
 */
@Serializable
data class MovingItem(
    val itemId: String,
    val amount: Int = 1,
    val from: GridPosition,
    val to: GridPosition,
    val progress: Float = 0f,
) {
    init {
        require(amount > 0) { "Количество должно быть положительным: $itemId" }
    }

    /** Предмет доехал до конца клетки. */
    val hasArrived: Boolean get() = progress >= 1f

    /** Направление движения — для поворота спрайта на ленте. */
    val direction: Direction?
        get() = Direction.entries.firstOrNull { from.neighbor(it) == to }

    companion object {
        /** Предмет, только что выложенный машиной в клетку [to]. */
        fun ejected(itemId: String, amount: Int, from: GridPosition, to: GridPosition): MovingItem =
            MovingItem(itemId = itemId, amount = amount, from = from, to = to, progress = 0f)
    }
}
