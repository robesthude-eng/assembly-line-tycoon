package com.example.assemblylinetycoon.domain.model

/**
 * Рецепт: что машина потребляет, что выдаёт и сколько это занимает времени
 * на нулевом уровне апгрейда.
 *
 * Фактическая длительность считается через `MathUtils.craftDuration` — здесь
 * лежит только база, чтобы одно и то же значение не дублировалось по коду.
 *
 * @param inputs требуемые предметы и их количества. Пустой список означает
 *   добычу «из ничего» (карьер): такому рецепту нужен [MachineType.SPAWNER].
 * @param outputCount сколько единиц выдаётся за один такт — это позволяет
 *   балансировать цепочку, не трогая цены.
 */
data class Recipe(
    val output: ItemId,
    val outputCount: Int = 1,
    val inputs: Map<ItemId, Int> = emptyMap(),
    val baseDurationMillis: Long,
    val machine: MachineType,
) {
    init {
        require(outputCount > 0) { "Рецепт должен что-то производить: $output" }
        require(baseDurationMillis > 0L) { "Длительность рецепта должна быть положительной: $output" }
        require(inputs.values.all { it > 0 }) { "Количество входов должно быть положительным: $output" }
    }

    /** Хватает ли содержимого буфера [available] на один такт. */
    fun canCraftFrom(available: Map<ItemId, Int>): Boolean =
        inputs.all { (item, need) -> (available[item] ?: 0) >= need }
}
