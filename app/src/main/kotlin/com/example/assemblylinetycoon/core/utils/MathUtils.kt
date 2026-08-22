package com.example.assemblylinetycoon.core.utils

import com.example.assemblylinetycoon.core.constants.GameConstants
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Формулы прогрессии из документа «Economy & Balance Model».
 *
 * Единственное место, где живут экономические зависимости: движок, ViewModel и
 * тесты обращаются сюда, а не считают степени по месту. Чистые функции без
 * состояния — их поведение полностью задаётся аргументами.
 *
 * Деньги везде [Long]: накопления тайкуна быстро выходят за пределы точности
 * [Double], поэтому округление делается ровно один раз, на выходе.
 */
object MathUtils {

    /**
     * Стоимость перехода с уровня [level] на [level] + 1.
     *
     * `Cost = BaseCost × growth^level`, где level отсчитывается с нуля:
     * первый апгрейд стоит ровно [baseCost].
     */
    fun upgradeCost(
        baseCost: Long,
        level: Int,
        growthFactor: Double = GameConstants.COST_GROWTH_FACTOR,
    ): Long {
        require(level >= 0) { "Уровень не может быть отрицательным: $level" }
        require(baseCost >= 0L) { "Базовая цена не может быть отрицательной: $baseCost" }
        if (level == 0) return baseCost
        val raw = baseCost.toDouble() * growthFactor.pow(level)
        return if (raw >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else raw.roundToLong()
    }

    /**
     * Суммарная стоимость покупки [count] уровней подряд, начиная с [fromLevel].
     * Нужна для кнопок «×10» и «максимум» в магазине.
     */
    fun bulkUpgradeCost(
        baseCost: Long,
        fromLevel: Int,
        count: Int,
        growthFactor: Double = GameConstants.COST_GROWTH_FACTOR,
    ): Long {
        require(count >= 0) { "Количество уровней не может быть отрицательным: $count" }
        var total = 0L
        for (offset in 0 until count) {
            val next = upgradeCost(baseCost, fromLevel + offset, growthFactor)
            if (Long.MAX_VALUE - total < next) return Long.MAX_VALUE
            total += next
        }
        return total
    }

    /**
     * Сколько уровней можно купить на [budget], начиная с [fromLevel].
     * Ограничено [limit], чтобы цикл не разрастался при огромном балансе.
     */
    fun affordableLevels(
        baseCost: Long,
        fromLevel: Int,
        budget: Long,
        growthFactor: Double = GameConstants.COST_GROWTH_FACTOR,
        limit: Int = 1_000,
    ): Int {
        var remaining = budget
        var bought = 0
        while (bought < limit) {
            val next = upgradeCost(baseCost, fromLevel + bought, growthFactor)
            if (next > remaining) break
            remaining -= next
            bought++
        }
        return bought
    }

    /**
     * Длительность крафта на уровне [level].
     *
     * `Duration = max(MIN, Base × speed^level)`. Нижняя граница обязательна:
     * без неё апгрейды в позднем этапе игры дали бы нулевое время такта и
     * бесконечную производительность за один тик.
     */
    fun craftDuration(
        baseDurationMillis: Long,
        level: Int,
        speedFactor: Double = GameConstants.CRAFT_SPEED_FACTOR,
        minDurationMillis: Long = GameConstants.MIN_CRAFT_DURATION_MS,
    ): Long {
        require(level >= 0) { "Уровень не может быть отрицательным: $level" }
        require(baseDurationMillis > 0L) { "Базовая длительность должна быть положительной" }
        val raw = baseDurationMillis.toDouble() * speedFactor.pow(level)
        return max(minDurationMillis, raw.roundToLong())
    }

    /**
     * Доход за время отсутствия.
     *
     * Отсутствие обрезается потолком [capMillis] (2 часа по умолчанию, 8 часов
     * с покупкой «Автоматический управляющий»), затем умножается на
     * [efficiency] — офлайн намеренно менее выгоден, чем активная игра.
     * Отрицательная разница времени (игрок перевёл часы назад) даёт ноль.
     */
    fun offlineEarnings(
        elapsedMillis: Long,
        ratePerSecond: Double,
        capMillis: Long = GameConstants.OFFLINE_CAP_DEFAULT_MS,
        efficiency: Double = GameConstants.OFFLINE_EFFICIENCY,
    ): Long {
        if (elapsedMillis <= 0L || ratePerSecond <= 0.0) return 0L
        val effective = min(elapsedMillis, capMillis)
        val earned = effective / 1000.0 * ratePerSecond * efficiency
        return if (earned >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else earned.roundToLong()
    }

    /** Безопасное сложение баланса: тайкун не должен переполнять счётчик. */
    fun addCoins(current: Long, delta: Long): Long = when {
        delta > 0L && Long.MAX_VALUE - current < delta -> Long.MAX_VALUE
        delta < 0L -> max(0L, current + delta)
        else -> current + delta
    }

    /** Прогресс от 0 до 1 для отрисовки полосы выполнения. */
    fun progress(elapsedMillis: Long, durationMillis: Long): Float {
        if (durationMillis <= 0L) return 1f
        return (elapsedMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0).toFloat()
    }
}
