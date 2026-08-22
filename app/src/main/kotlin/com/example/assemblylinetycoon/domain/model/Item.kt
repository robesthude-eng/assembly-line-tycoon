package com.example.assemblylinetycoon.domain.model

import kotlinx.serialization.Serializable

/**
 * Идентификатор предмета. Перечисление, а не строка: опечатка в рецепте
 * становится ошибкой компиляции, а сохранение остаётся компактным.
 *
 * Порядок значений — порядок открытия в игре, от сырья к финальному изделию.
 * Новые предметы добавляются только в конец: имя константы участвует в
 * сериализации сохранения.
 */
@Serializable
enum class ItemId {
    // Сырьё
    IRON_ORE,
    COPPER_ORE,
    PLASTIC_RAW,
    SILICON,

    // Первый передел
    IRON_INGOT,
    COPPER_WIRE,
    PLASTIC_CASING,

    // Детали
    GEAR,
    MICROCHIP,

    // Узлы
    ELECTRIC_MOTOR,
    SMART_CONTROLLER,

    // Готовые изделия
    INDUSTRIAL_DRONE,
    AI_ROBOT_UNIT,
}

/**
 * Описание предмета: неизменяемые справочные данные, одинаковые для всех
 * сохранений. В [GameState] попадают только [ItemId] и количества — справочник
 * живёт в коде, а не в сейве, чтобы баланс можно было править обновлением.
 *
 * @param tier глубина в производственной цепочке, 0 — сырьё. Используется
 *   для сортировки в интерфейсе и подбора цвета на Canvas.
 * @param basePrice цена продажи через экспортёр, в монетах.
 */
data class Item(
    val id: ItemId,
    val tier: Int,
    val basePrice: Long,
    val isRaw: Boolean = tier == 0,
)
