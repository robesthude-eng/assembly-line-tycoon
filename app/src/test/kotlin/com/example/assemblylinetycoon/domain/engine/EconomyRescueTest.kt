package com.example.assemblylinetycoon.domain.engine

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.MovingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Страховка от мёртвой точки: игрок не должен оставаться с нулевым доходом
 * и суммой, которой не хватает даже на самую дешёвую постройку.
 */
class EconomyRescueTest {

    private val stuck = GameState.NEW_GAME.copy(coins = 30L, baselineProductionRate = 0.0)

    @Test // ноль дохода и денег ни на что не хватает — это тупик
    fun idleFactoryWithoutCoinsIsStuck() {
        assertTrue(EconomyRescue.isStuck(stuck))
    }

    @Test // субсидия дотягивает баланс ровно до стартового капитала
    fun grantRestoresStartingCoins() {
        val grant = EconomyRescue.grantFor(stuck)

        assertEquals(GameConstants.STARTING_COINS - stuck.coins, grant)
        assertEquals(GameConstants.STARTING_COINS, stuck.coins + grant)
    }

    @Test // работающий завод помощи не получает, даже если денег мало
    fun producingFactoryGetsNothing() {
        val working = stuck.copy(baselineProductionRate = 1.5)

        assertFalse(EconomyRescue.isStuck(working))
        assertEquals(0L, EconomyRescue.grantFor(working))
    }

    @Test // пока предметы едут по лентам, помогать рано
    fun itemsOnBeltsPostponeTheGrant() {
        val inTransit = stuck.copy(
            movingItems = listOf(
                MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(0, 0), GridPosition(0, 0).neighbor(Direction.RIGHT), 0.5f),
            ),
        )

        assertFalse(EconomyRescue.isStuck(inTransit))
    }

    @Test // денег хватает на постройку — игрок не заперт
    fun affordablePurchaseMeansNotStuck() {
        val rich = stuck.copy(coins = GameConstants.STARTING_COINS)

        assertFalse(EconomyRescue.isStuck(rich))
        assertEquals(0L, EconomyRescue.grantFor(rich))
    }

    @Test // пустое несозданное состояние не трогаем: игра ещё не началась
    fun uninitializedStateIsNotRescued() {
        assertFalse(EconomyRescue.isStuck(GameState.EMPTY.copy(coins = 0L)))
    }
}
