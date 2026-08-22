package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.core.constants.GameConstants
import kotlinx.serialization.Serializable

/**
 * Единственный источник правды для симуляции.
 *
 * Правила слоя:
 *  * никакой зависимости от Android SDK — только Kotlin и kotlinx.serialization;
 *  * класс неизменяемый: движок возвращает новое состояние, а не мутирует текущее;
 *  * валюта хранится в [Long] (см. GDD: без чисел с плавающей точкой в деньгах).
 *
 * Сетка, машины и счётчик идентификаторов описаны здесь целиком; сам шаг
 * симуляции (движение предметов, такты машин) появится в `GameLoop.reduce`.
 */
@Serializable
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
) {

    /** Сколько машин типа [type] уже построено — вход для расчёта цены следующей. */
    fun machineCount(type: MachineType): Int = machines.values.count { it.type == type }

    /** Машина в ячейке [position], если она там есть. */
    fun machineAt(position: GridPosition): Machine? =
        machines.values.firstOrNull { it.position == position }

    /** Активно ли «Ускорение» в момент [nowMillis]. */
    fun isOverdriveActive(nowMillis: Long): Boolean = overdriveUntilMillis > nowMillis

    companion object {
        /** Состояние новой игры. */
        val EMPTY: GameState = GameState()
    }
}
