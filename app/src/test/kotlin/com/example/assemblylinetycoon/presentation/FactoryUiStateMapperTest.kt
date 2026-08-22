package com.example.assemblylinetycoon.presentation

import com.example.assemblylinetycoon.domain.catalog.MachineCatalog
import com.example.assemblylinetycoon.domain.catalog.RecipeCatalog
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import com.example.assemblylinetycoon.domain.model.ProductionStats
import com.example.assemblylinetycoon.presentation.mapper.FactoryUiStateMapper
import com.example.assemblylinetycoon.presentation.state.FactoryDialog
import com.example.assemblylinetycoon.presentation.state.FactoryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проекция состояния симуляции в состояние экрана.
 *
 * Это стык двух слоёв, поэтому проверяется отдельно от UI: тесту не нужен ни
 * Compose, ни эмулятор — только чистые данные.
 */
class FactoryUiStateMapperTest {

    private val smelter = Machine(
        id = 7,
        type = MachineType.SMELTER,
        position = GridPosition(2, 2),
        facing = Direction.RIGHT,
        level = 3,
        recipeOutputId = ItemId.IRON_INGOT.key,
        status = MachineStatus.CRAFTING,
        elapsedMillis = 1_000L,
    )

    private fun domainState(coins: Long = 5_000L): GameState = GameState.EMPTY.copy(
        coins = coins,
        isInitialized = true,
        grid = FactoryGrid.EMPTY
            .withMachine(smelter)
            .withBelt(GridPosition(3, 2), Direction.RIGHT),
        machines = mapOf(smelter.id to smelter),
        movingItems = listOf(
            MovingItem(
                itemId = ItemId.IRON_ORE.key,
                from = GridPosition(3, 2),
                to = GridPosition(4, 2),
                progress = 0.5f,
            ),
        ),
        stats = ProductionStats(coinsEarned = 600L, simulatedMillis = 60_000L),
    )

    @Test // баланс и производство попадают в HUD как есть
    fun balanceAndRateAreProjected() {
        val ui = FactoryUiStateMapper.map(domainState(coins = 1_234L))

        assertEquals(1_234L, ui.coins)
        // 600 монет за 60 секунд = 10 в секунду; считает домен, не маппер.
        assertEquals(10.0, ui.coinsPerSecond, 0.0001)
        assertTrue("Загрузка должна закончиться с приходом состояния", !ui.isLoading)
    }

    @Test // рендерер получает ссылки на доменные объекты, а не их копии
    fun renderModelReusesDomainCollections() {
        val domain = domainState()

        val ui = FactoryUiStateMapper.map(domain)

        // Тождество, а не равенство: копирование сетки и списка предметов
        // 20 раз в секунду означало бы лишний мусор на каждом кадре.
        assertSame(domain.grid, ui.render.grid)
        assertSame(domain.machines, ui.render.machines)
        assertSame(domain.movingItems, ui.render.movingItems)
    }

    @Test // карточка машины собирается из доменных расчётов
    fun machineInfoTakesNumbersFromDomain() {
        val domain = domainState(coins = 10_000_000L)

        val info = FactoryUiStateMapper.machineInfo(smelter, domain)

        val expectedDuration = MachineCatalog.craftDuration(
            RecipeCatalog.forOutput(ItemId.IRON_INGOT)!!.baseDurationMillis,
            level = smelter.level,
        )
        assertEquals(smelter.id, info.id)
        assertEquals(MachineType.SMELTER, info.type)
        assertEquals(3, info.level)
        assertEquals(expectedDuration, info.craftDurationMillis)
        assertEquals(smelter.nextUpgradeCost(), info.upgradeCost)
        assertEquals("Слиток железа", info.outputItemName)
        assertTrue(info.canAffordUpgrade)
    }

    @Test // при нехватке денег кнопка апгрейда должна гаснуть
    fun upgradeAffordabilityIsComparison() {
        val poor = FactoryUiStateMapper.machineInfo(smelter, domainState(coins = 0L))
        val rich = FactoryUiStateMapper.machineInfo(smelter, domainState(coins = Long.MAX_VALUE))

        assertTrue(!poor.canAffordUpgrade)
        assertTrue(rich.canAffordUpgrade)
    }

    @Test // выбор ячейки принадлежит экрану и переживает тик симуляции
    fun selectionSurvivesStateUpdates() {
        val domain = domainState()
        val selected = FactoryUiStateMapper.withSelectedCell(
            previous = FactoryUiState(),
            position = smelter.position,
            domain = domain,
        )

        val afterTick = FactoryUiStateMapper.map(domain.copy(coins = 9_999L), previous = selected)

        assertEquals(smelter.position, afterTick.selectedCell)
        assertEquals(smelter.position, afterTick.render.selectedCell)
        assertNotNull(afterTick.selectedMachine)
        assertEquals(9_999L, afterTick.coins)
    }

    @Test // открытый диалог обновляется свежими данными машины
    fun openDialogIsRefreshedWithLiveProgress() {
        val domain = domainState()
        val opened = FactoryUiState(
            dialog = FactoryDialog.MachineInfo(FactoryUiStateMapper.machineInfo(smelter, domain)),
        )

        val advanced = domain.copy(
            machines = mapOf(smelter.id to smelter.copy(elapsedMillis = 2_000L)),
        )
        val ui = FactoryUiStateMapper.map(advanced, previous = opened)

        val dialog = ui.dialog as FactoryDialog.MachineInfo
        val stale = (opened.dialog as FactoryDialog.MachineInfo).machine
        assertTrue(
            "Полоса прогресса в диалоге должна двигаться вместе с заводом",
            dialog.machine.progress > stale.progress,
        )
    }

    @Test // снесённая машина закрывает свой диалог, а не показывает призрак
    fun dialogClosesWhenMachineDisappears() {
        val domain = domainState()
        val opened = FactoryUiState(
            dialog = FactoryDialog.MachineInfo(FactoryUiStateMapper.machineInfo(smelter, domain)),
        )

        val ui = FactoryUiStateMapper.map(domain.copy(machines = emptyMap()), previous = opened)

        assertEquals(FactoryDialog.None, ui.dialog)
    }

    @Test // выбор за пределами поля не переносится в новое состояние
    fun selectionOutsideGridIsDropped() {
        val previous = FactoryUiState(selectedCell = GridPosition(99, 99))

        val ui = FactoryUiStateMapper.map(domainState(), previous = previous)

        assertNull(ui.selectedCell)
    }

    @Test // плашка «Ускорения» считает остаток от текущего времени
    fun boostReflectsOverdriveWindow() {
        val domain = domainState().copy(overdriveUntilMillis = 10_000L)

        val active = FactoryUiStateMapper.map(domain, nowMillis = 4_000L)
        val expired = FactoryUiStateMapper.map(domain, nowMillis = 20_000L)

        assertTrue(active.boost.isOverdriveActive)
        assertEquals(6_000L, active.boost.remainingMillis)
        assertTrue(!expired.boost.isOverdriveActive)
        assertEquals(0L, expired.boost.remainingMillis)
    }
}
