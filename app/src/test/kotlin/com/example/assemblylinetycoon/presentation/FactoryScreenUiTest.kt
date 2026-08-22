package com.example.assemblylinetycoon.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import com.example.assemblylinetycoon.presentation.state.BoostUiState
import com.example.assemblylinetycoon.presentation.state.FactoryDialog
import com.example.assemblylinetycoon.presentation.state.FactoryIntent
import com.example.assemblylinetycoon.presentation.state.FactoryRenderModel
import com.example.assemblylinetycoon.presentation.state.FactoryUiState
import com.example.assemblylinetycoon.presentation.state.MachineUiInfo
import com.example.assemblylinetycoon.presentation.ui.components.EMPTY_CELL_DIALOG_TAG
import com.example.assemblylinetycoon.presentation.ui.components.buildOptionTag
import com.example.assemblylinetycoon.presentation.ui.components.FACTORY_CANVAS_TAG
import com.example.assemblylinetycoon.presentation.ui.components.HUD_BOOST_TAG
import com.example.assemblylinetycoon.presentation.ui.components.HUD_COINS_TAG
import com.example.assemblylinetycoon.presentation.ui.components.HUD_RATE_TAG
import com.example.assemblylinetycoon.presentation.ui.components.MACHINE_DIALOG_TAG
import com.example.assemblylinetycoon.presentation.ui.components.MACHINE_DIALOG_UPGRADE_TAG
import com.example.assemblylinetycoon.presentation.ui.screens.FACTORY_SCREEN_TAG
import com.example.assemblylinetycoon.presentation.ui.screens.FactoryScreen
import com.example.assemblylinetycoon.presentation.ui.theme.AssemblyLineTycoonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверка экрана завода.
 *
 * Тесты идут через Robolectric в обычной JVM: эмулятора нет ни в песочнице,
 * ни в CI, а экран должен проверяться на каждом коммите, а не «когда дойдут
 * руки до устройства». Размер экрана зафиксирован в [Config], иначе геометрия
 * холста зависела бы от машины, где запущен тест.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class FactoryScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val smelter = Machine(
        id = 1,
        type = MachineType.SMELTER,
        position = GridPosition(2, 2),
        facing = Direction.RIGHT,
        level = 2,
        recipeOutputId = ItemId.IRON_INGOT.key,
        status = MachineStatus.CRAFTING,
        elapsedMillis = 800L,
    )

    private val machineInfo = MachineUiInfo(
        id = smelter.id,
        type = MachineType.SMELTER,
        position = smelter.position,
        level = 2,
        status = MachineStatus.CRAFTING,
        progress = 0.4f,
        outputItemName = "Слиток железа",
        craftDurationMillis = 3_600L,
        upgradeCost = 1_200L,
        canAffordUpgrade = true,
    )

    private fun uiState(
        coins: Long = 12_540L,
        dialog: FactoryDialog = FactoryDialog.None,
        movingItems: List<MovingItem> = emptyList(),
    ) = FactoryUiState(
        isLoading = false,
        coins = coins,
        coinsPerSecond = 2.0,
        render = FactoryRenderModel(
            grid = FactoryGrid.EMPTY
                .withMachine(smelter)
                .withBelt(GridPosition(3, 2), Direction.RIGHT),
            machines = mapOf(smelter.id to smelter),
            movingItems = movingItems,
        ),
        dialog = dialog,
        boost = BoostUiState(isOverdriveActive = false, remainingMillis = 0L),
    )

    /** Магазин с реальными каталожными ценами: 200 монет в кармане. */
    private fun shopDialog(): FactoryDialog.EmptyCell {
        val position = GridPosition(5, 5)
        val domain = com.example.assemblylinetycoon.domain.model.GameState.EMPTY.copy(coins = 200L)
        return FactoryDialog.EmptyCell(
            position = position,
            options = com.example.assemblylinetycoon.presentation.mapper.FactoryUiStateMapper
                .buildOptions(domain, position),
        )
    }

    private fun show(
        state: FactoryUiState,
        onIntent: (FactoryIntent) -> Unit = {},
    ) {
        compose.setContent {
            AssemblyLineTycoonTheme(darkTheme = true) {
                FactoryScreen(state = state, onIntent = onIntent)
            }
        }
    }

    @Test // экран завода открывается и показывает поле
    fun factoryScreenOpensWithCanvas() {
        show(uiState())

        compose.onNodeWithTag(FACTORY_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithTag(FACTORY_CANVAS_TAG).assertIsDisplayed()
    }

    @Test // HUD показывает баланс в компактном виде
    fun hudDisplaysBalance() {
        show(uiState(coins = 12_540L))

        compose.onNodeWithTag(HUD_COINS_TAG).assertTextEquals("12.5K")
        // 2 монеты в секунду — это 120 в минуту.
        compose.onNodeWithTag(HUD_RATE_TAG).assertTextEquals("120/мин")
        compose.onNodeWithTag(HUD_BOOST_TAG).assertIsDisplayed()
    }

    @Test // касание поля уходит наверх как намерение, а не меняет UI само
    fun tappingCanvasSendsSelectIntent() {
        val intents = mutableListOf<FactoryIntent>()
        show(uiState(), onIntent = intents::add)

        compose.onNodeWithTag(FACTORY_CANVAS_TAG).performTouchInput { click(center) }
        compose.waitForIdle()

        assertEquals(1, intents.size)
        val intent = intents.single()
        assertTrue("Ожидали выбор ячейки, получили $intent", intent is FactoryIntent.SelectCell)
    }

    @Test // выбранная машина показывает карточку с названием и уровнем
    fun selectedMachineOpensDialog() {
        show(uiState(dialog = FactoryDialog.MachineInfo(machineInfo)))

        compose.onNodeWithTag(MACHINE_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText("Плавильня").assertIsDisplayed()
        compose.onNodeWithText("Слиток железа").assertIsDisplayed()
        compose.onNodeWithTag(MACHINE_DIALOG_UPGRADE_TAG).assertIsEnabled()
    }

    @Test // кнопка улучшения гаснет, когда денег не хватает
    fun upgradeButtonDisabledWithoutCoins() {
        show(uiState(dialog = FactoryDialog.MachineInfo(machineInfo.copy(canAffordUpgrade = false))))

        compose.onNodeWithTag(MACHINE_DIALOG_UPGRADE_TAG).assertIsNotEnabled()
    }

    @Test // кнопка улучшения шлёт намерение, а не считает цену сама
    fun upgradeButtonSendsIntent() {
        val intents = mutableListOf<FactoryIntent>()
        show(uiState(dialog = FactoryDialog.MachineInfo(machineInfo)), onIntent = intents::add)

        compose.onNodeWithTag(MACHINE_DIALOG_UPGRADE_TAG).performClick()
        compose.waitForIdle()

        assertEquals(FactoryIntent.UpgradeMachine(smelter.id), intents.single())
    }

    @Test // пустая ячейка открывает магазин с ценами
    fun emptyCellDialogListsMachinesWithPrices() {
        show(uiState(dialog = shopDialog()))

        compose.onNodeWithTag(EMPTY_CELL_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithTag(buildOptionTag(MachineType.SPAWNER)).assertIsEnabled()
        compose.onNodeWithText("Карьер").assertIsDisplayed()
        compose.onNodeWithText("50").assertIsDisplayed()
        // На сборщик денег не хватает — строка неактивна.
        compose.onNodeWithTag(buildOptionTag(MachineType.ASSEMBLER)).assertIsNotEnabled()
    }

    @Test // выбор машины в магазине уходит намерением постройки
    fun buildingSendsPlaceIntent() {
        val intents = mutableListOf<FactoryIntent>()
        show(uiState(dialog = shopDialog()), onIntent = intents::add)

        compose.onNodeWithTag(buildOptionTag(MachineType.SMELTER)).performClick()
        compose.waitForIdle()

        assertEquals(
            FactoryIntent.PlaceMachine(GridPosition(5, 5), MachineType.SMELTER),
            intents.single(),
        )
    }

    @Test // холст переживает полный завод с движущимися предметами
    fun canvasRendersFullFactoryWithoutCrash() {
        val items = (0 until 40).map { index ->
            MovingItem(
                itemId = if (index % 2 == 0) ItemId.IRON_ORE.key else ItemId.GEAR.key,
                from = GridPosition(index % 10, index / 10),
                to = GridPosition((index % 10 + 1).coerceAtMost(9), index / 10),
                progress = index / 40f,
            )
        }

        show(uiState(movingItems = items))

        compose.onNodeWithTag(FACTORY_CANVAS_TAG).assertIsDisplayed()
    }
}
