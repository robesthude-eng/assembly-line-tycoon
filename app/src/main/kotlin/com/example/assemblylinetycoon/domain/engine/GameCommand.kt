package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.domain.model.AdPlacement
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.MachineType

/**
 * Команды, меняющие состояние симуляции.
 *
 * Отличие от presentation-интентов: интент — это «пользователь нажал»,
 * команда — «симуляция должна сделать». Экран транслирует первое во второе,
 * а движок не знает о существовании UI.
 */
sealed interface GameCommand {

    /** Служебный тик симуляции. */
    data class Tick(val deltaMillis: Long) : GameCommand

    /** Начисление офлайн-дохода после расчёта на старте. */
    data class ApplyOfflineEarnings(val coins: Long) : GameCommand

    /**
     * Построить машину. Цену считает [FactoryBuilder] по каталогу; если денег
     * не хватает или ячейка занята, состояние остаётся прежним.
     */
    data class PlaceMachine(
        val position: GridPosition,
        val type: MachineType,
    ) : GameCommand

    /** Улучшить машину: минус цена следующего уровня, плюс уровень. */
    data class UpgradeMachine(val machineId: Int) : GameCommand

    /** Проложить отрезок конвейера. */
    data class PlaceBelt(
        val position: GridPosition,
        val direction: Direction,
    ) : GameCommand

    /** Повернуть уже проложенную ленту; бесплатно. */
    data class RotateBelt(
        val position: GridPosition,
        val direction: Direction,
    ) : GameCommand

    /** Снести содержимое ячейки без возврата денег. */
    data class Demolish(val position: GridPosition) : GameCommand

    /** Награда за просмотренный ролик. */
    data class ApplyAdReward(val placement: AdPlacement) : GameCommand

    /** Сброс прогресса. */
    data object ResetProgress : GameCommand
}
