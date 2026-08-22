package com.example.assemblylinetycoon.presentation.state

import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.MachineType

/**
 * Намерения игрока на экране завода.
 *
 * Интент описывает **смысл** действия, а не способ ввода: никаких пикселей,
 * `Offset` и прочих Compose-типов здесь нет. Благодаря этому логику экрана
 * можно проверить обычным JVM-тестом, без эмулятора.
 */
sealed interface FactoryIntent : UiIntent {

    /** Экран стал виден: запустить симуляцию. */
    data object ScreenStarted : FactoryIntent

    /** Экран ушёл в фон: остановить симуляцию и сохранить прогресс. */
    data object ScreenStopped : FactoryIntent

    /** Касание ячейки поля. Координаты уже переведены в сеточные. */
    data class SelectCell(val position: GridPosition) : FactoryIntent

    /** Открыть карточку машины (долгое нажатие или кнопка «Подробнее»). */
    data class OpenMachineDialog(val machineId: Int) : FactoryIntent

    /** Построить машину в ячейке. */
    data class PlaceMachine(
        val position: GridPosition,
        val type: MachineType,
    ) : FactoryIntent

    /** Улучшить машину. */
    data class UpgradeMachine(val machineId: Int) : FactoryIntent

    /** Проложить отрезок конвейера в выбранном направлении. */
    data class PlaceBelt(
        val position: GridPosition,
        val direction: Direction,
    ) : FactoryIntent

    /** Развернуть уже проложенную ленту. */
    data class RotateBelt(
        val position: GridPosition,
        val direction: Direction,
    ) : FactoryIntent

    /** Снести содержимое ячейки. */
    data class Demolish(val position: GridPosition) : FactoryIntent

    /** Закрыть открытый диалог. */
    data object CloseDialog : FactoryIntent

    /** Скрыть сообщение об ошибке. */
    data object ErrorDismissed : FactoryIntent
}
