package com.example.assemblylinetycoon.domain.catalog

import com.example.assemblylinetycoon.domain.model.Item
import com.example.assemblylinetycoon.domain.model.ItemCategory
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.ItemShape
import com.example.assemblylinetycoon.domain.model.ItemVisual

/**
 * Справочник предметов: названия, категории, цены и подсказки для отрисовки.
 * Значения — из документа «Economy & Balance Model».
 *
 * Справочник намеренно не попадает в сохранение: правка баланса обновлением
 * приложения не должна ломать чужие сейвы.
 */
object ItemCatalog {

    private val items: Map<String, Item> = listOf(
        // ── Сырьё ───────────────────────────────────────────────────────────
        Item(
            id = ItemId.IRON_ORE.key,
            displayName = "Железная руда",
            category = ItemCategory.RAW,
            tier = 0,
            basePrice = 1L,
            maxStack = 20,
            visual = ItemVisual(colorHex = "#8D6E63", shape = ItemShape.HEXAGON),
        ),
        Item(
            id = ItemId.COPPER_ORE.key,
            displayName = "Медная руда",
            category = ItemCategory.RAW,
            tier = 0,
            basePrice = 2L,
            maxStack = 20,
            visual = ItemVisual(colorHex = "#BF6A3A", shape = ItemShape.HEXAGON),
        ),
        Item(
            id = ItemId.PLASTIC_RAW.key,
            displayName = "Пластик-сырец",
            category = ItemCategory.RAW,
            tier = 0,
            basePrice = 3L,
            maxStack = 20,
            visual = ItemVisual(colorHex = "#7E8B99", shape = ItemShape.HEXAGON),
        ),
        Item(
            id = ItemId.SILICON.key,
            displayName = "Кремний",
            category = ItemCategory.RAW,
            tier = 0,
            basePrice = 8L,
            maxStack = 20,
            visual = ItemVisual(colorHex = "#546E7A", shape = ItemShape.HEXAGON),
        ),

        // ── Полуфабрикаты ───────────────────────────────────────────────────
        Item(
            id = ItemId.IRON_INGOT.key,
            displayName = "Слиток железа",
            category = ItemCategory.PROCESSED,
            tier = 1,
            basePrice = 6L,
            visual = ItemVisual(colorHex = "#9E9E9E", shape = ItemShape.SQUARE),
        ),
        Item(
            id = ItemId.COPPER_WIRE.key,
            displayName = "Медный провод",
            category = ItemCategory.PROCESSED,
            tier = 1,
            basePrice = 10L,
            visual = ItemVisual(colorHex = "#D2691E", shape = ItemShape.CIRCLE),
        ),
        Item(
            id = ItemId.PLASTIC_CASING.key,
            displayName = "Пластиковый корпус",
            category = ItemCategory.PROCESSED,
            tier = 1,
            basePrice = 14L,
            visual = ItemVisual(colorHex = "#455A64", shape = ItemShape.SQUARE),
        ),

        // ── Детали ──────────────────────────────────────────────────────────
        Item(
            id = ItemId.GEAR.key,
            displayName = "Шестерня",
            category = ItemCategory.COMPONENT,
            tier = 2,
            basePrice = 30L,
            visual = ItemVisual(colorHex = "#B0BEC5", shape = ItemShape.CIRCLE),
        ),
        Item(
            id = ItemId.MICROCHIP.key,
            displayName = "Микрочип",
            category = ItemCategory.COMPONENT,
            tier = 2,
            basePrice = 90L,
            visual = ItemVisual(colorHex = "#2E7D32", shape = ItemShape.SQUARE),
        ),

        // ── Узлы ────────────────────────────────────────────────────────────
        Item(
            id = ItemId.ELECTRIC_MOTOR.key,
            displayName = "Электромотор",
            category = ItemCategory.COMPONENT,
            tier = 3,
            basePrice = 180L,
            maxStack = 5,
            visual = ItemVisual(colorHex = "#1565C0", shape = ItemShape.CIRCLE),
        ),
        Item(
            id = ItemId.SMART_CONTROLLER.key,
            displayName = "Умный контроллер",
            category = ItemCategory.COMPONENT,
            tier = 3,
            basePrice = 420L,
            maxStack = 5,
            visual = ItemVisual(colorHex = "#00897B", shape = ItemShape.SQUARE),
        ),

        // ── Готовые изделия ─────────────────────────────────────────────────
        Item(
            id = ItemId.INDUSTRIAL_DRONE.key,
            displayName = "Промышленный дрон",
            category = ItemCategory.FINAL,
            tier = 4,
            basePrice = 900L,
            maxStack = 3,
            visual = ItemVisual(colorHex = "#F9A825", shape = ItemShape.TRIANGLE),
        ),
        Item(
            id = ItemId.AI_ROBOT_UNIT.key,
            displayName = "ИИ-робот",
            category = ItemCategory.FINAL,
            tier = 5,
            basePrice = 1_500L,
            maxStack = 3,
            visual = ItemVisual(colorHex = "#C62828", shape = ItemShape.TRIANGLE),
        ),
    ).associateBy(Item::id)

    /** Описание предмета. Отсутствие записи — ошибка данных, а не игровая ситуация. */
    operator fun get(id: String): Item =
        items[id] ?: error("В каталоге нет предмета $id")

    operator fun get(id: ItemId): Item = get(id.key)

    /** Мягкий поиск: null вместо исключения, если ключ пришёл из сохранения. */
    fun find(id: String): Item? = items[id]

    fun all(): List<Item> = items.values.sortedWith(compareBy(Item::tier, Item::id))

    fun raw(): List<Item> = all().filter(Item::isRaw)

    fun byCategory(category: ItemCategory): List<Item> = all().filter { it.category == category }

    /** Цена продажи, которую использует экспортёр. */
    fun priceOf(id: String): Long = get(id).basePrice

    fun priceOf(id: ItemId): Long = get(id.key).basePrice
}
