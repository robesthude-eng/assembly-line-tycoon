package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.core.constants.GameConstants
import kotlinx.serialization.Serializable

/**
 * Тип оборудования. Экономические параметры (цена и темп её роста) заданы
 * в документе «Economy & Balance Model» и держатся рядом с типом, чтобы
 * баланс читался одним взглядом.
 *
 * @param baseCost цена первой постройки, монеты.
 * @param costGrowth множитель цены за каждый уже построенный экземпляр —
 *   именно он не даёт заставить поле одними ассемблерами.
 * @param inputSlots сколько разных предметов помещается во входной буфер.
 */
@Serializable
enum class MachineType(
    val baseCost: Long,
    val costGrowth: Double,
    val inputSlots: Int,
) {
    /** Карьер: производит сырьё без входов. */
    SPAWNER(baseCost = 50L, costGrowth = 1.15, inputSlots = 0),

    /** Плавильня: руда → слиток. */
    SMELTER(baseCost = 150L, costGrowth = 1.15, inputSlots = 1),

    /** Пресс: слиток → корпус, шестерня. */
    PRESS(baseCost = 300L, costGrowth = 1.15, inputSlots = 1),

    /** Волочильный стан: медь → провод. */
    WIRE_DRAWER(baseCost = 250L, costGrowth = 1.15, inputSlots = 1),

    /** Сборщик: несколько деталей → узел. */
    ASSEMBLER(baseCost = 800L, costGrowth = 1.18, inputSlots = 3),

    /** Контроль качества: повышает ценность изделия. */
    QUALITY_CONTROL(baseCost = 1_500L, costGrowth = 1.20, inputSlots = 1),

    /** Экспортёр: продаёт то, что до него доехало. */
    EXPORTER(baseCost = 100L, costGrowth = 1.12, inputSlots = 1);

    /** Может ли машина работать без входящих предметов. */
    val isSource: Boolean get() = this == SPAWNER

    /** Является ли машина точкой сбыта, а не производства. */
    val isSink: Boolean get() = this == EXPORTER
}

/**
 * Фаза такта машины. Отдельная фаза выгрузки нужна для честного
 * противодавления: машина, которой некуда положить результат, не «съедает»
 * следующую порцию сырья, а стоит с готовым предметом на выходе.
 */
@Serializable
enum class MachineStatus {
    /** Ждёт сырья или свободного места. */
    IDLE,

    /** Идёт такт производства. */
    CRAFTING,

    /** Такт завершён, результат ждёт выгрузки на ленту. */
    OUTPUT_EJECT,
}

/**
 * Экземпляр машины на поле. Часть [GameState], поэтому сериализуется и
 * остаётся неизменяемым: движок возвращает копию через `copy`.
 *
 * @param level уровень апгрейда, влияет на длительность такта.
 * @param elapsedMillis сколько текущий такт уже длится.
 * @param inputBuffer накопленное сырьё.
 * @param outputBuffer готовые предметы, ждущие выгрузки.
 */
@Serializable
data class Machine(
    val id: Int,
    val type: MachineType,
    val position: GridPosition,
    val facing: Direction = Direction.RIGHT,
    val level: Int = 0,
    val recipe: ItemId? = null,
    val status: MachineStatus = MachineStatus.IDLE,
    val elapsedMillis: Long = 0L,
    val inputBuffer: Map<ItemId, Int> = emptyMap(),
    val outputBuffer: Map<ItemId, Int> = emptyMap(),
) {
    /** Куда машина выкладывает результат. */
    val outputPosition: GridPosition get() = position.neighbor(facing)

    /** Цена следующего уровня апгрейда. */
    fun nextUpgradeCost(): Long = com.example.assemblylinetycoon.core.utils.MathUtils.upgradeCost(
        baseCost = type.baseCost,
        level = level + 1,
        growthFactor = type.costGrowth,
    )

    /** Доля выполнения текущего такта для полосы прогресса. */
    fun progress(durationMillis: Long): Float =
        com.example.assemblylinetycoon.core.utils.MathUtils.progress(elapsedMillis, durationMillis)

    companion object {
        /** Максимум единиц одного предмета во входном буфере. */
        const val BUFFER_CAPACITY: Int = 10

        /** Размер поля берётся из констант, чтобы валидация была одна на всех. */
        fun isPlaceable(position: GridPosition): Boolean =
            position.isInside(GameConstants.GRID_WIDTH, GameConstants.GRID_HEIGHT)
    }
}
