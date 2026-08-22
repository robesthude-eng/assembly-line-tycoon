package com.example.assemblylinetycoon.domain.model

import com.example.assemblylinetycoon.domain.catalog.ItemCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Модель предмета: создание, равенство и готовность к сериализации. */
class ItemTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test // предмет создаётся со всеми полями и отвечает на запросы о себе
    fun itemIsCreatedWithAllFields() {
        val item = Item(
            id = "iron_ore",
            displayName = "Железная руда",
            category = ItemCategory.RAW,
            tier = 0,
            basePrice = 1L,
            maxStack = 20,
            visual = ItemVisual(colorHex = "#8D6E63", shape = ItemShape.HEXAGON),
        )

        assertEquals("iron_ore", item.id)
        assertEquals("Железная руда", item.displayName)
        assertEquals(ItemCategory.RAW, item.category)
        assertEquals(20, item.maxStack)
        assertTrue(item.isRaw)
        assertEquals(ItemId.IRON_ORE, item.itemId)
    }

    @Test // без явного размера стопки берётся значение по умолчанию
    fun defaultStackSizeIsApplied() {
        val item = Item("gear", "Шестерня", ItemCategory.COMPONENT, tier = 2, basePrice = 30L)
        assertEquals(Item.DEFAULT_MAX_STACK, item.maxStack)
        assertNull(item.visual)
        assertFalse(item.isRaw)
    }

    @Test // равенство определяется значениями, а не ссылкой
    fun equalityIsStructural() {
        val first = Item("gear", "Шестерня", ItemCategory.COMPONENT, tier = 2, basePrice = 30L)
        val second = Item("gear", "Шестерня", ItemCategory.COMPONENT, tier = 2, basePrice = 30L)
        val other = first.copy(basePrice = 31L)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, other)
    }

    @Test // копия с изменением не трогает исходный объект
    fun copyLeavesOriginalIntact() {
        val original = ItemCatalog[ItemId.GEAR]
        val renamed = original.copy(displayName = "Зубчатое колесо")

        assertEquals("Шестерня", original.displayName)
        assertEquals("Зубчатое колесо", renamed.displayName)
        assertEquals(original.id, renamed.id)
    }

    @Test // некорректные данные отвергаются при создании, а не позже
    fun invalidItemIsRejected() {
        val cases = listOf(
            "пустой идентификатор" to { Item("", "Имя", ItemCategory.RAW, 0, 1L) },
            "отрицательный ярус" to { Item("x", "Имя", ItemCategory.RAW, -1, 1L) },
            "нулевая цена" to { Item("x", "Имя", ItemCategory.RAW, 0, 0L) },
            "нулевая стопка" to { Item("x", "Имя", ItemCategory.RAW, 0, 1L, maxStack = 0) },
        )
        cases.forEach { (name, create) ->
            try {
                create()
                throw AssertionError("Ожидалась ошибка: $name")
            } catch (expected: IllegalArgumentException) {
                // так и должно быть
            }
        }
    }

    @Test // предмет переживает запись в JSON и чтение обратно
    fun itemSurvivesJsonRoundTrip() {
        val original = ItemCatalog[ItemId.AI_ROBOT_UNIT]
        val restored = json.decodeFromString(Item.serializer(), json.encodeToString(Item.serializer(), original))
        assertEquals(original, restored)
    }

    @Test // неизвестные поля из будущих версий не ломают чтение
    fun unknownFieldsAreIgnored() {
        val payload = """
            {"id":"gear","displayName":"Шестерня","category":"COMPONENT","tier":2,
             "basePrice":30,"maxStack":10,"durability":42}
        """.trimIndent()

        val item = json.decodeFromString(Item.serializer(), payload)
        assertEquals("gear", item.id)
        assertEquals(30L, item.basePrice)
    }

    @Test // ключ предмета стабилен и разбирается обратно
    fun itemKeysAreStableAndResolvable() {
        ItemId.entries.forEach { id ->
            assertEquals(id, ItemId.fromKey(id.key))
            assertTrue("Ключ ${id.key} должен быть в нижнем регистре", id.key == id.key.lowercase())
        }
        assertNull(ItemId.fromKey("unobtainium"))
    }

    @Test // все тринадцать предметов из проекта есть в каталоге
    fun catalogContainsEveryDesignItem() {
        val expected = listOf(
            "iron_ore", "copper_ore", "plastic_raw", "silicon",
            "iron_ingot", "copper_wire", "plastic_casing",
            "gear", "microchip",
            "electric_motor", "smart_controller",
            "industrial_drone", "ai_robot_unit",
        )
        expected.forEach { id -> assertNotNull("Нет предмета $id", ItemCatalog.find(id)) }
        assertEquals(expected.size, ItemCatalog.all().size)
    }

    @Test // каждая категория заполнена, сырьё совпадает с ярусом ноль
    fun categoriesAreConsistentWithTiers() {
        ItemCategory.entries.forEach { category ->
            assertTrue("Категория $category пуста", ItemCatalog.byCategory(category).isNotEmpty())
        }
        ItemCatalog.byCategory(ItemCategory.RAW).forEach { assertEquals(0, it.tier) }
        ItemCatalog.byCategory(ItemCategory.FINAL).forEach { assertTrue(it.tier >= 4) }
    }
}
