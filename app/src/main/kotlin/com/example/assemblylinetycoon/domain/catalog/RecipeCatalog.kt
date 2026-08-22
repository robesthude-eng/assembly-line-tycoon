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
        Recipe.of(
            output = ItemId.IRON_ORE,
            baseDurationMillis = 2_000L,
            machineType = MachineType.SPAWNER,
        ),
        Recipe.of(
            output = ItemId.COPPER_ORE,
            baseDurationMillis = 2_500L,
            machineType = MachineType.SPAWNER,
        ),
        Recipe.of(
            output = ItemId.PLASTIC_RAW,
            baseDurationMillis = 3_000L,
            machineType = MachineType.SPAWNER,
        ),
        Recipe.of(
            output = ItemId.SILICON,
            baseDurationMillis = 5_000L,
            machineType = MachineType.SPAWNER,
        ),

        // --- Первый передел ---
        Recipe.of(
            output = ItemId.IRON_INGOT,
            inputs = mapOf(ItemId.IRON_ORE to 2),
            baseDurationMillis = 4_000L,
            machineType = MachineType.SMELTER,
        ),
        Recipe.of(
            output = ItemId.COPPER_WIRE,
            outputAmount = 2,
            inputs = mapOf(ItemId.COPPER_ORE to 1),
            baseDurationMillis = 4_500L,
            machineType = MachineType.WIRE_DRAWER,
        ),
        Recipe.of(
            output = ItemId.PLASTIC_CASING,
            inputs = mapOf(ItemId.PLASTIC_RAW to 3),
            baseDurationMillis = 6_000L,
            machineType = MachineType.PRESS,
        ),

        // --- Детали ---
        Recipe.of(
            output = ItemId.GEAR,
            inputs = mapOf(ItemId.IRON_INGOT to 3),
            baseDurationMillis = 8_000L,
            machineType = MachineType.PRESS,
        ),
        Recipe.of(
            output = ItemId.MICROCHIP,
            inputs = mapOf(ItemId.SILICON to 4, ItemId.COPPER_WIRE to 2),
            baseDurationMillis = 12_000L,
            machineType = MachineType.ASSEMBLER,
        ),

        // --- Узлы ---
        Recipe.of(
            output = ItemId.ELECTRIC_MOTOR,
            inputs = mapOf(ItemId.GEAR to 2, ItemId.COPPER_WIRE to 4),
            baseDurationMillis = 15_000L,
            machineType = MachineType.ASSEMBLER,
        ),
        Recipe.of(
            output = ItemId.SMART_CONTROLLER,
            inputs = mapOf(ItemId.MICROCHIP to 2, ItemId.PLASTIC_CASING to 2),
            baseDurationMillis = 18_000L,
            machineType = MachineType.ASSEMBLER,
        ),

        // --- Готовые изделия ---
        Recipe.of(
            output = ItemId.INDUSTRIAL_DRONE,
            inputs = mapOf(
                ItemId.ELECTRIC_MOTOR to 1,
                ItemId.SMART_CONTROLLER to 1,
                ItemId.PLASTIC_CASING to 2,
            ),
            baseDurationMillis = 22_000L,
            machineType = MachineType.ASSEMBLER,
        ),
        Recipe.of(
            output = ItemId.AI_ROBOT_UNIT,
            inputs = mapOf(
                ItemId.INDUSTRIAL_DRONE to 1,
                ItemId.GEAR to 2,
            ),
            baseDurationMillis = 25_000L,
            machineType = MachineType.QUALITY_GATE,
        ),
    )

    private val byOutput: Map<String, Recipe> = recipes.associateBy(Recipe::outputItemId)

    private val byMachine: Map<MachineType, List<Recipe>> = recipes.groupBy(Recipe::machineType)

    fun all(): List<Recipe> = recipes

    /** Рецепт, дающий предмет [outputItemId]; null, если предмет ничем не производится. */
    fun forOutput(outputItemId: String): Recipe? = byOutput[outputItemId]

    fun forOutput(output: ItemId): Recipe? = byOutput[output.key]

    /** Что можно поставить на производство на машине [type]. */
    fun forMachine(type: MachineType): List<Recipe> = byMachine[type].orEmpty()

    /** Стоимость входов рецепта по каталожным ценам — метрика маржи для баланса. */
    fun inputValue(recipe: Recipe): Long =
        recipe.inputs.entries.sumOf { (itemId, count) -> ItemCatalog.priceOf(itemId) * count }

    /** Прибавка стоимости за один такт: цена выхода минус цена входов. */
    fun valueAdded(recipe: Recipe): Long =
        ItemCatalog.priceOf(recipe.outputItemId) * recipe.outputAmount - inputValue(recipe)
}
