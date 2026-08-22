package com.example.assemblylinetycoon.domain.model

import kotlinx.serialization.Serializable

/**
 * Рецепт: что машина потребляет, что выдаёт и сколько это занимает времени
 * на нулевом уровне апгрейда.
 *
 * Входы хранятся как `Map<String, Int>` — ключ предмета и количество.
 * Карта, а не список пар, выбрана специально: сравнение и поиск по ней не
 * зависят от порядка, поэтому «2 железной руды и 1 медной» и «1 медной и
 * 2 железной» — один и тот же набор требований.
 *
 * Фактическая длительность считается через `MathUtility.craftDuration`:
 * здесь лежит только база, чтобы значение не дублировалось по коду.
 *
 * @param outputItemId ключ производимого предмета.
 * @param outputAmount сколько единиц выдаётся за такт — позволяет
 *   балансировать цепочку, не трогая цены.
 * @param inputs требуемые предметы и их количества; пусто у карьера.
 * @param baseDurationMillis длительность такта на нулевом уровне.
 * @param machineType машина, на которой рецепт доступен.
 */
@Serializable
data class Recipe(
    val outputItemId: String,
    val outputAmount: Int = 1,
    val inputs: Map<String, Int> = emptyMap(),
    val baseDurationMillis: Long,
    val machineType: MachineType,
) {
    init {
        require(outputItemId.isNotBlank()) { "Рецепт должен указывать предмет на выходе" }
        require(outputAmount > 0) { "Рецепт должен что-то производить: $outputItemId" }
        require(baseDurationMillis > 0L) {
            "Длительность рецепта должна быть положительной: $outputItemId"
        }
        require(inputs.values.all { it > 0 }) {
            "Количество каждого входа должно быть положительным: $outputItemId"
        }
        require(outputItemId !in inputs) {
            "Рецепт не может требовать сам себя: $outputItemId"
        }
    }

    /** Нужны ли рецепту входные предметы. У карьера их нет. */
    val requiresInputs: Boolean get() = inputs.isNotEmpty()

    /**
     * Хватает ли содержимого буфера на один такт.
     * Проверка идёт по ключам, поэтому порядок в [available] не важен.
     */
    fun canCraftFrom(available: Map<String, Int>): Boolean =
        inputs.all { (itemId, required) -> (available[itemId] ?: 0) >= required }

    /**
     * Чего и сколько не хватает до такта. Пустая карта означает, что можно
     * начинать. Возвращается именно нехватка, а не булево, — так UI сможет
     * показать «нужно ещё 3 шестерни» без повторного расчёта.
     */
    fun missingInputs(available: Map<String, Int>): Map<String, Int> =
        inputs.mapNotNull { (itemId, required) ->
            val missing = required - (available[itemId] ?: 0)
            if (missing > 0) itemId to missing else null
        }.toMap()

    /**
     * Буфер после списания входов. Вызывать только когда [canCraftFrom]
     * вернул true, иначе значения ушли бы в минус.
     */
    fun consumeFrom(available: Map<String, Int>): Map<String, Int> {
        require(canCraftFrom(available)) {
            "Недостаточно входов для $outputItemId: не хватает ${missingInputs(available)}"
        }
        val result = available.toMutableMap()
        inputs.forEach { (itemId, required) ->
            val left = (result[itemId] ?: 0) - required
            if (left > 0) result[itemId] = left else result.remove(itemId)
        }
        return result
    }

    companion object {
        /** Сборка рецепта из типизированных идентификаторов — короче в каталоге. */
        fun of(
            output: ItemId,
            outputAmount: Int = 1,
            inputs: Map<ItemId, Int> = emptyMap(),
            baseDurationMillis: Long,
            machineType: MachineType,
        ): Recipe = Recipe(
            outputItemId = output.key,
            outputAmount = outputAmount,
            inputs = inputs.mapKeys { (item, _) -> item.key },
            baseDurationMillis = baseDurationMillis,
            machineType = machineType,
        )
    }
}
