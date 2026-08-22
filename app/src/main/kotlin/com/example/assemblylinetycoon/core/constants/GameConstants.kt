package com.example.assemblylinetycoon.core.constants

/**
 * Числовые константы симуляции. Только значения — никакой логики.
 * Источник: GDD & Technical Specification, разделы Game Loop и Offline Logic.
 */
object GameConstants {

    /** Шаг визуального тика игрового цикла (мс). */
    const val TICK_INTERVAL_MS: Long = 50L

    /** Шаг экономического пересчёта, отвязанный от визуального тика (мс). */
    const val ECONOMY_INTERVAL_MS: Long = 1_000L

    /** Потолок офлайн-начисления по умолчанию (мс) — 2 часа. */
    const val OFFLINE_CAP_DEFAULT_MS: Long = 2 * 60 * 60 * 1000L

    /** Потолок офлайн-начисления с покупкой «Автоматический управляющий» (мс). */
    const val OFFLINE_CAP_MANAGER_MS: Long = 8 * 60 * 60 * 1000L

    /** Размер игровой сетки завода. */
    const val GRID_WIDTH: Int = 10
    const val GRID_HEIGHT: Int = 10

    /** Максимум предметов на одной ячейке конвейера. */
    const val MAX_ITEMS_PER_BELT_CELL: Int = 1

    /** Нижняя граница длительности крафта после апгрейдов (мс). */
    const val MIN_CRAFT_DURATION_MS: Long = 100L

    /** Множители формул апгрейда, см. MathUtils. */
    const val COST_GROWTH_FACTOR: Double = 1.15
    const val CRAFT_SPEED_FACTOR: Double = 0.95

    /** Доля офлайн-дохода от активной игры: отсутствие выгодно, но не выгоднее игры. */
    const val OFFLINE_EFFICIENCY: Double = 0.5

    /** Множитель дохода при активном «Ускорении» за просмотр ролика. */
    const val OVERDRIVE_MULTIPLIER: Double = 2.0

    /** Длительность «Ускорения» (мс) — 5 минут. */
    const val OVERDRIVE_DURATION_MS: Long = 5 * 60 * 1000L

    /** Версия схемы сохранения. Инкрементируется при несовместимых изменениях. */
    const val SAVE_SCHEMA_VERSION: Int = 1

    /** Периодичность автосохранения (мс). */
    const val AUTOSAVE_INTERVAL_MS: Long = 30_000L
}
