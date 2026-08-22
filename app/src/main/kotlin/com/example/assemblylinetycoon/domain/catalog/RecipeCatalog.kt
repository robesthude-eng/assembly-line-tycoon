package com.example.assemblylinetycoon.domain.catalog

import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.Recipe

/**
 * Производственные цепочки.
 *
 * Правило балансировки: чем глубже передел, тем длиннее такт и тем выше цена
 * результата относительно суммы входов — иначе игроку выгоднее продавать сырьё
 * и вся идея завода теряет смысл. Проверяется тестом `RecipeCatalogTest`.
 */
object RecipeCatalog {

    private val recipes: List<Recipe> = listOf(
        // --- Добыча: карьеры производят сырьё без входов ---
        Recipe(
            output = ItemId.IRON_ORE,
            baseDurationMillis = 2_000L,
            machine = MachineType.SPAWNER,
        ),
        Recipe(
            output = ItemId.COPPER_ORE,
            baseDurationMillis = 2_500L,
            machine = MachineType.SPAWNER,
        ),
        Recipe(
            output = ItemId.PLASTIC_RAW,
            baseDurationMillis = 3_000L,
            machine = MachineType.SPAWNER,
        ),
        Recipe(
            output = ItemId.SILICON,
            baseDurationMillis = 5_000L,
            machine = MachineType.SPAWNER,
        ),

        // --- Первый передел ---
        Recipe(
            output = ItemId.IRON_INGOT,
            inputs = mapOf(ItemId.IRON_ORE to 2),
            baseDurationMillis = 4_000L,
            machine = MachineType.SMELTER,
        ),
        Recipe(
            output = ItemId.COPPER_WIRE,
            outputCount = 2,
            inputs = mapOf(ItemId.COPPER_ORE to 1),
            baseDurationMillis = 4_500L,
            machine = MachineType.WIRE_DRAWER,
        ),
        Recipe(
            output = ItemId.PLASTIC_CASING,
            inputs = mapOf(ItemId.PLASTIC_RAW to 3),
            baseDurationMillis = 6_000L,
            machine = MachineType.PRESS,
        ),

        // --- Детали ---
        Recipe(
            output = ItemId.GEAR,
            inputs = mapOf(ItemId.IRON_INGOT to 3),
            baseDurationMillis = 8_000L,
            machine = MachineType.PRESS,
        ),
        Recipe(
            output = ItemId.MICROCHIP,
            inputs = mapOf(ItemId.SILICON to 4, ItemId.COPPER_WIRE to 2),
            baseDurationMillis = 12_000L,
            machine = MachineType.ASSEMBLER,
        ),

        // --- Узлы ---
        Recipe(
            output = ItemId.ELECTRIC_MOTOR,
            inputs = mapOf(ItemId.GEAR to 2, ItemId.COPPER_WIRE to 4),
            baseDurationMillis = 15_000L,
            machine = MachineType.ASSEMBLER,
        ),
        Recipe(
            output = ItemId.SMART_CONTROLLER,
            inputs = mapOf(ItemId.MICROCHIP to 2, ItemId.PLASTIC_CASING to 2),
            baseDurationMillis = 18_000L,
            machine = MachineType.ASSEMBLER,
        ),

        // --- Готовые изделия ---
        Recipe(
            output = ItemId.INDUSTRIAL_DRONE,
            inputs = mapOf(
                ItemId.ELECTRIC_MOTOR to 1,
                ItemId.SMART_CONTROLLER to 1,
                ItemId.PLASTIC_CASING to 2,
            ),
            baseDurationMillis = 22_000L,
            machine = MachineType.ASSEMBLER,
        ),
        Recipe(
            output = ItemId.AI_ROBOT_UNIT,
            inputs = mapOf(
                ItemId.INDUSTRIAL_DRONE to 1,
                ItemId.GEAR to 2,
            ),
            baseDurationMillis = 25_000L,
            machine = MachineType.QUALITY_CONTROL,
        ),
    )

    private val byOutput: Map<ItemId, Recipe> = recipes.associateBy(Recipe::output)

    private val byMachine: Map<MachineType, List<Recipe>> = recipes.groupBy(Recipe::machine)

    fun all(): List<Recipe> = recipes

    /** Рецепт, дающий предмет [output]; для сырья без источника вернёт null. */
    fun forOutput(output: ItemId): Recipe? = byOutput[output]

    /** Что можно поставить на производство на машине [type]. */
    fun forMachine(type: MachineType): List<Recipe> = byMachine[type].orEmpty()

    /** Стоимость входов рецепта по каталожным ценам — метрика маржи для баланса. */
    fun inputValue(recipe: Recipe): Long =
        recipe.inputs.entries.sumOf { (item, count) -> ItemCatalog.priceOf(item) * count }

    /** Прибавка стоимости за один такт: цена выхода минус цена входов. */
    fun valueAdded(recipe: Recipe): Long =
        ItemCatalog.priceOf(recipe.output) * recipe.outputCount - inputValue(recipe)
}
