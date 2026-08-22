package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.core.constants.GameConstants

/**
 * Единственный источник правды для симуляции.
 *
 * Правила слоя:
 *  * никакой зависимости от Android SDK и даже от библиотеки сериализации:
 *    формат файла описан отдельными моделями в слое data;
 *  * класс неизменяемый: движок возвращает новое состояние, а не мутирует текущее;
 *  * валюта хранится в [Long] (см. GDD: без чисел с плавающей точкой в деньгах).
 *
 * Сетка, машины и счётчик идентификаторов описаны здесь целиком; сам шаг
 * симуляции (движение предметов, такты машин) появится в `GameLoop.reduce`.
 */
data class GameState(
    /** Версия схемы сохранения для миграций. */
    val schemaVersion: Int = GameConstants.SAVE_SCHEMA_VERSION,

    /** Баланс игрока в «монетах». */
    val coins: Long = 0L,

    /** Момент последнего сохранения, epoch millis. Основа расчёта офлайн-дохода. */
    val lastSavedAtMillis: Long = 0L,

    /** Битовая маска открытых технологий. */
    val unlockedTechMask: Long = 0L,

    /** Снимок базовой производительности для офлайн-расчёта, монет в секунду. */
    val baselineProductionRate: Double = 0.0,

    /** Признак того, что состояние уже инициализировано (не первый запуск). */
    val isInitialized: Boolean = false,

    /** Поле завода активной линии. */
    val grid: FactoryGrid = FactoryGrid.EMPTY,

    /** Машины на поле, ключ — идентификатор из [nextMachineId]. */
    val machines: Map<Int, Machine> = emptyMap(),

    /** Счётчик выдачи идентификаторов машин; монотонно растёт. */
    val nextMachineId: Int = 1,

    /** Сколько производственных линий открыто, см. SlotCatalog. */
    val unlockedSlots: Int = 1,

    /** Момент окончания «Ускорения» за просмотр ролика, epoch millis. */
    val overdriveUntilMillis: Long = 0L,

    /** Предметы, едущие по лентам прямо сейчас. */
    val movingItems: List<MovingItem> = emptyList(),

    /** Накопительная статистика производства. */
    val stats: ProductionStats = ProductionStats.EMPTY,

    /** Момент последнего шага симуляции, epoch millis. */
    val lastTickAtMillis: Long = 0L,
) {

    /** Предмет, едущий в ячейку [position]; ячейка считается занятой им. */
    fun itemMovingTo(position: GridPosition): MovingItem? =
        movingItems.firstOrNull { it.to == position }

    /** Свободна ли ячейка для въезда предмета. */
    fun isCellFree(position: GridPosition): Boolean = itemMovingTo(position) == null

    /** Сколько машин типа [type] уже построено — вход для расчёта цены следующей. */
    fun machineCount(type: MachineType): Int = machines.values.count { it.type == type }

    /** Машина в ячейке [position], если она там есть. */
    fun machineAt(position: GridPosition): Machine? =
        machines.values.firstOrNull { it.position == position }

    /** Активно ли «Ускорение» в момент [nowMillis]. */
    fun isOverdriveActive(nowMillis: Long): Boolean = overdriveUntilMillis > nowMillis

    companion object {
        /** Пустое состояние: ноль во всех полях. Используется в тестах и как база. */
        val EMPTY: GameState = GameState()

        /**
         * Состояние новой игры.
         *
         * Отличается от [EMPTY] стартовым капиталом: без него первый экран
         * игры — тупик, потому что строить не на что, а зарабатывать нечем.
         */
        val NEW_GAME: GameState = GameState(
            coins = GameConstants.STARTING_COINS,
            isInitialized = true,
        )
    }
}
