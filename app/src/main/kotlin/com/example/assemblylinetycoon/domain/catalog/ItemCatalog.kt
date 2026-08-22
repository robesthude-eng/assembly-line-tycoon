package com.example.assemblylinetycoon.domain.catalog

import com.example.assemblylinetycoon.domain.model.Item
import com.example.assemblylinetycoon.domain.model.ItemId

/**
 * Справочник предметов: цены и место в цепочке переделов.
 * Значения — из документа «Economy & Balance Model».
 *
 * Справочник намеренно не попадает в сохранение: правка баланса обновлением
 * приложения не должна ломать чужие сейвы.
 */
object ItemCatalog {

    private val items: Map<ItemId, Item> = listOf(
        // Сырьё
        Item(ItemId.IRON_ORE, tier = 0, basePrice = 1L),
        Item(ItemId.COPPER_ORE, tier = 0, basePrice = 2L),
        Item(ItemId.PLASTIC_RAW, tier = 0, basePrice = 3L),
        Item(ItemId.SILICON, tier = 0, basePrice = 8L),

        // Первый передел
        Item(ItemId.IRON_INGOT, tier = 1, basePrice = 6L),
        Item(ItemId.COPPER_WIRE, tier = 1, basePrice = 10L),
        Item(ItemId.PLASTIC_CASING, tier = 1, basePrice = 14L),

        // Детали
        Item(ItemId.GEAR, tier = 2, basePrice = 30L),
        Item(ItemId.MICROCHIP, tier = 2, basePrice = 90L),

        // Узлы
        Item(ItemId.ELECTRIC_MOTOR, tier = 3, basePrice = 180L),
        Item(ItemId.SMART_CONTROLLER, tier = 3, basePrice = 420L),

        // Готовые изделия
        Item(ItemId.INDUSTRIAL_DRONE, tier = 4, basePrice = 900L),
        Item(ItemId.AI_ROBOT_UNIT, tier = 5, basePrice = 1_500L),
    ).associateBy(Item::id)

    /** Описание предмета. Отсутствие записи — ошибка данных, а не игровая ситуация. */
    operator fun get(id: ItemId): Item =
        items[id] ?: error("В каталоге нет предмета $id")

    fun all(): List<Item> = items.values.sortedWith(compareBy(Item::tier, { it.id.ordinal }))

    fun raw(): List<Item> = all().filter(Item::isRaw)

    /** Цена продажи, которую использует экспортёр. */
    fun priceOf(id: ItemId): Long = get(id).basePrice
}
