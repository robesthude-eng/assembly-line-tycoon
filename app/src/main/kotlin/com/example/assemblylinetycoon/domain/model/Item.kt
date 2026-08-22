package com.example.assemblylinetycoon.domain.model


/**
 * Категория предмета. Определяет место в производственной цепочке и то,
 * как предмет будет показан игроку.
 */
enum class ItemCategory {
    /** Сырьё: добывается карьером, не требует входов. */
    RAW,

    /** Полуфабрикат: первый передел сырья. */
    PROCESSED,

    /** Деталь: собирается из полуфабрикатов. */
    COMPONENT,

    /** Готовое изделие: конечная цель цепочки, идёт на экспорт. */
    FINAL,
}

/**
 * Идентификатор предмета.
 *
 * Перечисление, а не голая строка: опечатка в рецепте становится ошибкой
 * компиляции. При этом у каждого значения есть стабильный строковый [key] —
 * именно он используется в рецептах и сохранениях, поэтому переименование
 * константы Kotlin не ломает чужие сейвы.
 *
 * Новые предметы добавляются только в конец, ключи менять нельзя.
 */
enum class ItemId(val key: String) {
    // Сырьё
    IRON_ORE("iron_ore"),
    COPPER_ORE("copper_ore"),
    PLASTIC_RAW("plastic_raw"),
    SILICON("silicon"),

    // Полуфабрикаты
    IRON_INGOT("iron_ingot"),
    COPPER_WIRE("copper_wire"),
    PLASTIC_CASING("plastic_casing"),

    // Детали
    GEAR("gear"),
    MICROCHIP("microchip"),

    // Узлы
    ELECTRIC_MOTOR("electric_motor"),
    SMART_CONTROLLER("smart_controller"),

    // Готовые изделия
    INDUSTRIAL_DRONE("industrial_drone"),
    AI_ROBOT_UNIT("ai_robot_unit");

    companion object {
        private val byKey: Map<String, ItemId> = entries.associateBy(ItemId::key)

        /** Разбор ключа из сохранения или рецепта; null, если ключ неизвестен. */
        fun fromKey(key: String): ItemId? = byKey[key]
    }
}

/**
 * Подсказки для будущей отрисовки. Домен не знает про Compose и Android,
 * поэтому цвет хранится как строка `#RRGGBB`, а форма — как перечисление:
 * рендерер сам решит, во что это превратить.
 */
data class ItemVisual(
    val colorHex: String,
    val shape: ItemShape = ItemShape.SQUARE,
)

/** Силуэт предмета на ленте — минимум, нужный, чтобы различать их взглядом. */
enum class ItemShape { SQUARE, CIRCLE, TRIANGLE, HEXAGON }

/**
 * Описание предмета: справочные данные, одинаковые для всех сохранений.
 *
 * Класс неизменяемый. В [GameState] попадают только идентификаторы и
 * количества — сам справочник живёт в коде, чтобы правку баланса можно было
 * выкатить обновлением, не ломая сохранения.
 *
 * @param id стабильный ключ, он же ключ в рецептах и сейвах.
 * @param displayName название для интерфейса.
 * @param category место в цепочке переделов.
 * @param tier глубина цепочки, 0 — сырьё. Нужен для сортировки и проверок.
 * @param basePrice цена продажи через экспортёр, монеты.
 * @param maxStack сколько единиц помещается в один буфер машины.
 * @param visual подсказки для рендерера, домен ими не пользуется.
 */
data class Item(
    val id: String,
    val displayName: String,
    val category: ItemCategory,
    val tier: Int,
    val basePrice: Long,
    val maxStack: Int = DEFAULT_MAX_STACK,
    val visual: ItemVisual? = null,
) {
    init {
        require(id.isNotBlank()) { "Идентификатор предмета не может быть пустым" }
        require(tier >= 0) { "Ярус не может быть отрицательным: $id" }
        require(basePrice > 0L) { "Цена должна быть положительной: $id" }
        require(maxStack > 0) { "Размер стопки должен быть положительным: $id" }
    }

    /** Сырьё производится карьером и не требует входов. */
    val isRaw: Boolean get() = category == ItemCategory.RAW

    /** Типизированный идентификатор; null, если предмета нет в перечислении. */
    val itemId: ItemId? get() = ItemId.fromKey(id)

    companion object {
        const val DEFAULT_MAX_STACK: Int = 10
    }
}
