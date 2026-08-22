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
 * Поля симуляции (сетка, машины, предметы в пути) добавляются на этапе 2 —
 * здесь намеренно только каркас.
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
) {
    companion object {
        /** Состояние новой игры. */
        val EMPTY: GameState = GameState()
    }
}
