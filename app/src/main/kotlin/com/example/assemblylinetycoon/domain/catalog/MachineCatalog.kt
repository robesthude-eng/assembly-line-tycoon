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

/**
 * Цены конвейера.
 *
 * В документах «GDD» и «Economy & Balance Model» цены ленты нет: там описаны
 * только машины. Значения ниже — **осознанное дополнение баланса**, а не
 * найденное в ТЗ число, поэтому они собраны в одном месте и прокомментированы.
 *
 * Логика подбора:
 *  * лента должна быть дешевле самой дешёвой машины (карьер, 50 монет), иначе
 *    соединить два станка станет дороже, чем поставить третий;
 *  * цена всё же обязана расти, иначе оптимальной стратегией окажется
 *    «замостить поле лентами» — это не решение, а отсутствие выбора;
 *  * рост мягкий: поле 10×10, лент на нём помещается меньше сотни, и при
 *    множителе машин (1.15) сотая лента стоила бы миллионы.
 *
 * При 1.03 первая лента стоит 10 монет, тридцатая — 24, девяностая — 140:
 * заметно, но не запретительно.
 *
 * Важное следствие усечения (правило проекта: цена всегда округляется вниз):
 * при базе 10 надбавка в 3 % меньше монеты, поэтому цена растёт **ступенями**,
 * а не с каждым отрезком — 10, 10, 10, 10, 11, 11… Это не ошибка расчёта:
 * поднять базу ради красивой монотонности значило бы сделать ленту дороже
 * ради самой дорогой ленты, а игрок платит целыми монетами.
 */
object BeltCatalog {

    /** Цена первого отрезка конвейера. */
    const val BASE_COST: Long = 10L

    /** Множитель цены за каждый уже проложенный отрезок. */
    const val COST_GROWTH: Double = 1.03

    /** Сколько стоит следующая лента, когда проложено [ownedCount] отрезков. */
    fun buildCost(ownedCount: Int): Long = MathUtility.upgradeCost(
        baseCost = BASE_COST,
        level = ownedCount,
        growthFactor = COST_GROWTH,
    )

    /**
     * Поворот уже проложенной ленты бесплатен.
     *
     * Плата за поворот наказывала бы за исправление собственной ошибки и
     * подталкивала бы сносить и класть заново — то же действие, только дороже
     * и на два нажатия длиннее.
     */
    const val ROTATE_COST: Long = 0L
}
