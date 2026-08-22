package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import com.example.assemblylinetycoon.domain.model.GameState

/**
 * Страховка от мёртвой точки.
 *
 * Экономика игры допускает состояние, из которого нет выхода: завод ничего
 * не приносит, а денег не хватает даже на самую дешёвую постройку. Игрок в
 * этот момент не видит ни ошибки, ни подсказки — для него игра просто
 * сломалась. Один такой случай уже случился на живом устройстве: карьер,
 * поставленный у правого края, выдавал руду за границу поля.
 *
 * Возврат денег при сносе закрывает большинство таких ситуаций, но не все:
 * например, машину можно снести, получив половину, и всё равно не собрать
 * на нужную. Поэтому есть нижняя граница — баланс дотягивается до стартового
 * капитала.
 *
 * Почему это не «бесконечные деньги»: субсидия выдаётся, только когда доход
 * равен нулю **и** денег меньше, чем стоит минимальная постройка. Как только
 * завод заработал, условие перестаёт выполняться навсегда.
 */
object EconomyRescue {

    /** Самая дешёвая покупка в игре — нижняя планка «на что-то хватает». */
    private val cheapestPurchase: Long
        get() = MachineCatalog.purchasable().minOf { MachineCatalog.buildCost(it, ownedCount = 0) }

    /**
     * Заперт ли игрок: денег ни на что не хватает и заработать нечем.
     *
     * Едущие по лентам предметы считаются доходом в пути — пока они не
     * доехали, помогать рано.
     */
    fun isStuck(state: GameState): Boolean =
        state.isInitialized &&
            state.coins < cheapestPurchase &&
            state.baselineProductionRate <= 0.0 &&
            state.movingItems.isEmpty()

    /** Сколько монет нужно выдать, чтобы игра снова стала играбельной. */
    fun grantFor(state: GameState): Long =
        if (isStuck(state)) (GameConstants.STARTING_COINS - state.coins).coerceAtLeast(0L) else 0L
}
