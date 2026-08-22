package com.example.assemblylinetycoon.domain.catalog

import com.example.assemblylinetycoon.core.utils.MathUtility
import com.example.assemblylinetycoon.domain.model.MachineType

/**
 * Цены на оборудование и слоты производственных линий.
 *
 * Стоимость машины зависит от того, сколько таких уже построено: множитель
 * [MachineType.costGrowth] превращает «поставить ещё один ассемблер» из
 * очевидного решения в выбор.
 */
object MachineCatalog {

    /**
     * Стоимость постройки машины, когда у игрока уже есть [ownedCount] таких.
     * Первая копия стоит ровно [MachineType.baseCost].
     */
    fun buildCost(type: MachineType, ownedCount: Int): Long =
        MathUtility.upgradeCost(
            baseCost = type.baseCost,
            level = ownedCount,
            growthFactor = type.costGrowth,
        )

    /** Цена перехода машины с уровня [level] на следующий. */
    fun upgradeCost(type: MachineType, level: Int): Long =
        MathUtility.upgradeCost(
            baseCost = type.baseCost,
            level = level + 1,
            growthFactor = type.costGrowth,
        )

    /** Длительность такта машины уровня [level] для рецепта с базой [baseDurationMillis]. */
    fun craftDuration(baseDurationMillis: Long, level: Int): Long =
        MathUtility.craftDuration(baseDurationMillis, level)

    /** Что доступно к постройке в магазине, в порядке усложнения. */
    fun purchasable(): List<MachineType> = MachineType.entries.sortedBy(MachineType::baseCost)
}

/**
 * Слоты производственных линий: параллельные заводы, которые игрок открывает
 * по мере роста. Цены из документа «Economy & Balance Model» — шаг ×5 после
 * бесплатного первого слота.
 */
object SlotCatalog {

    /** Цена открытия слота по его порядковому номеру, отсчёт с 1. */
    private val prices: List<Long> = listOf(
        0L,
        100L,
        500L,
        2_500L,
        10_000L,
        50_000L,
        250_000L,
        1_000_000L,
        5_000_000L,
        25_000_000L,
    )

    /** Максимальное число линий. */
    val maxSlots: Int = prices.size

    /** Цена открытия слота [slotNumber] (1..[maxSlots]). */
    fun unlockCost(slotNumber: Int): Long {
        require(slotNumber in 1..maxSlots) {
            "Слот $slotNumber вне диапазона 1..$maxSlots"
        }
        return prices[slotNumber - 1]
    }

    /** Открыт ли слот при [unlockedSlots] купленных линиях. */
    fun isUnlocked(slotNumber: Int, unlockedSlots: Int): Boolean = slotNumber <= unlockedSlots

    /** Сколько стоит открыть следующий слот; null — все линии уже открыты. */
    fun nextUnlockCost(unlockedSlots: Int): Long? =
        if (unlockedSlots >= maxSlots) null else unlockCost(unlockedSlots + 1)
}
