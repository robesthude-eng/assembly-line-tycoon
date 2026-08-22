package com.example.assemblylinetycoon.domain.model

import kotlinx.serialization.Serializable

/**
 * Направление на ортогональной сетке. Задаёт, куда конвейер толкает предмет и
 * в какую сторону смотрит выход машины.
 *
 * Хранится как перечисление, а не как угол: поворот на 90° — единственная
 * доступная игроку операция, промежуточных состояний быть не должно.
 */
@Serializable
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0);

    /** Поворот по часовой стрелке — то, что делает одиночный тап по объекту. */
    fun rotateClockwise(): Direction = when (this) {
        UP -> RIGHT
        RIGHT -> DOWN
        DOWN -> LEFT
        LEFT -> UP
    }

    fun rotateCounterClockwise(): Direction = rotateClockwise().opposite()

    fun opposite(): Direction = when (this) {
        UP -> DOWN
        RIGHT -> LEFT
        DOWN -> UP
        LEFT -> RIGHT
    }
}
