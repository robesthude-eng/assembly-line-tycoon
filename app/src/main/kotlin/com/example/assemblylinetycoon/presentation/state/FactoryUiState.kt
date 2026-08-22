package com.example.assemblylinetycoon.presentation.state

import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem

/**
 * Состояние экрана завода — **проекция** `GameState` для отрисовки.
 *
 * UI не читает движок напрямую: иначе любое изменение внутренностей симуляции
 * ломало бы Compose-слой. Здесь лежит ровно то, что показывается на экране,
 * и ничего больше.
 */
data class FactoryUiState(
    val isLoading: Boolean = true,

    /** Баланс игрока для HUD. */
    val coins: Long = 0L,

    /** Средний доход, монет в секунду. Пересчёт в минуту делает HUD. */
    val coinsPerSecond: Double = 0.0,

    /** Всё, что рисует Canvas. Вынесено отдельно — см. [FactoryRenderModel]. */
    val render: FactoryRenderModel = FactoryRenderModel.EMPTY,

    /** Выделенная игроком ячейка; `null` — ничего не выбрано. */
    val selectedCell: GridPosition? = null,

    /** Машина в выделенной ячейке, если она там есть. */
    val selectedMachine: MachineUiInfo? = null,

    /** Открытый диалог. Часть состояния, а не «событие»: переживает поворот экрана. */
    val dialog: FactoryDialog = FactoryDialog.None,

    /** Плашка активных усилений в HUD. */
    val boost: BoostUiState = BoostUiState.INACTIVE,

    val errorMessage: String? = null,
) : UiState

/**
 * Данные для рендерера.
 *
 * Внутри — **ссылки на неизменяемые доменные объекты**, а не их копии. Такт
 * симуляции идёт 20 раз в секунду, и если бы проекция каждый раз собирала
 * новые списки ячеек, сборщик мусора съел бы кадры. Неизменяемость домена
 * позволяет отдать ссылку и сравнивать её по равенству.
 *
 * @param revision счётчик тиков: меняется каждый кадр и служит явным ключом
 *   перерисовки, чтобы не сравнивать поэлементно большие коллекции.
 */
data class FactoryRenderModel(
    val grid: FactoryGrid,
    val machines: Map<Int, Machine>,
    val movingItems: List<MovingItem>,
    val selectedCell: GridPosition? = null,
    val revision: Long = 0L,
) {
    val width: Int get() = grid.width
    val height: Int get() = grid.height

    companion object {
        val EMPTY = FactoryRenderModel(
            grid = FactoryGrid.EMPTY,
            machines = emptyMap(),
            movingItems = emptyList(),
        )
    }
}

/**
 * Карточка машины для диалога и HUD.
 *
 * Здесь нет ни одной формулы: стоимость апгрейда и длительность такта уже
 * посчитаны доменом и просто перенесены в готовые к показу поля.
 */
data class MachineUiInfo(
    val id: Int,
    val type: MachineType,
    val position: GridPosition,
    val level: Int,
    val status: MachineStatus,
    /** Доля выполнения текущего такта, 0..1 — для полосы прогресса. */
    val progress: Float,
    /** Что машина производит; `null` — рецепт ещё не назначен. */
    val outputItemName: String?,
    /** Длительность такта, мс — посчитана доменом с учётом уровня. */
    val craftDurationMillis: Long,
    /** Цена следующего уровня — посчитана доменом. */
    val upgradeCost: Long,
    /** Хватает ли денег на апгрейд. Сравнение, а не расчёт цены. */
    val canAffordUpgrade: Boolean,
)

/**
 * Строка магазина: что можно поставить в выбранную ячейку и почём.
 *
 * Цена приходит из каталога через `FactoryBuilder`; интерфейс её только
 * показывает и сравнивает с балансом — та же цена спишется движком.
 */
data class BuildOptionUi(
    val type: MachineType,
    val name: String,
    val cost: Long,
    val canAfford: Boolean,
)

/** Какой диалог открыт поверх завода. */
sealed interface FactoryDialog {
    /** Диалогов нет. */
    data object None : FactoryDialog

    /** Карточка машины. */
    data class MachineInfo(val machine: MachineUiInfo) : FactoryDialog

    /**
     * Пустая ячейка: выбор оборудования для постройки.
     *
     * Список вариантов лежит прямо в диалоге, а не в общем состоянии: цены
     * зависят от того, сколько таких машин уже построено, поэтому пересчёт
     * нужен ровно на время, пока диалог открыт.
     */
    data class EmptyCell(
        val position: GridPosition,
        val options: List<BuildOptionUi> = emptyList(),
    ) : FactoryDialog
}

/**
 * Плашка усилений. Пока показывает только «Ускорение» из состояния движка;
 * место под остальные бусты зарезервировано, но пустых заглушек не плодим.
 */
data class BoostUiState(
    val isOverdriveActive: Boolean,
    val remainingMillis: Long,
) {
    companion object {
        val INACTIVE = BoostUiState(isOverdriveActive = false, remainingMillis = 0L)
    }
}
