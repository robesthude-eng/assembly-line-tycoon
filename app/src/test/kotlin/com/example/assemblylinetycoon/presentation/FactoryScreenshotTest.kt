package com.example.assemblylinetycoon.presentation

import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import com.example.assemblylinetycoon.presentation.state.FactoryRenderModel
import com.example.assemblylinetycoon.presentation.state.FactoryUiState
import com.example.assemblylinetycoon.presentation.ui.screens.FACTORY_SCREEN_TAG
import com.example.assemblylinetycoon.presentation.ui.screens.FactoryScreen
import com.example.assemblylinetycoon.presentation.ui.theme.AssemblyLineTycoonTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Снимок экрана завода в файл.
 *
 * Не проверка на совпадение с эталоном, а способ посмотреть на настоящую
 * отрисовку без устройства: тест рисует экран нативной графикой Robolectric и
 * сохраняет PNG рядом со сборкой. Полезно при правках рендерера — видно, что
 * получилось, до установки APK на телефон.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class FactoryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureFactoryScreen() {
        val spawner = Machine(
            id = 1,
            type = MachineType.SPAWNER,
            position = GridPosition(1, 3),
            facing = Direction.RIGHT,
            level = 2,
            recipeOutputId = ItemId.IRON_ORE.key,
            status = MachineStatus.CRAFTING,
            elapsedMillis = 1_200L,
        )
        val smelter = Machine(
            id = 2,
            type = MachineType.SMELTER,
            position = GridPosition(5, 3),
            facing = Direction.DOWN,
            level = 4,
            recipeOutputId = ItemId.IRON_INGOT.key,
            status = MachineStatus.CRAFTING,
            elapsedMillis = 2_500L,
        )
        val exporter = Machine(
            id = 3,
            type = MachineType.EXPORTER,
            position = GridPosition(5, 7),
            facing = Direction.RIGHT,
            level = 1,
        )

        var grid = FactoryGrid.EMPTY
            .withMachine(spawner)
            .withMachine(smelter)
            .withMachine(exporter)
        (2..4).forEach { x -> grid = grid.withBelt(GridPosition(x, 3), Direction.RIGHT) }
        (4..6).forEach { y -> grid = grid.withBelt(GridPosition(5, y), Direction.DOWN) }

        val state = FactoryUiState(
            isLoading = false,
            coins = 12_540L,
            coinsPerSecond = 4.2,
            render = FactoryRenderModel(
                grid = grid,
                machines = mapOf(1 to spawner, 2 to smelter, 3 to exporter),
                movingItems = listOf(
                    MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(2, 3), GridPosition(3, 3), 0.4f),
                    MovingItem(ItemId.IRON_ORE.key, 1, GridPosition(3, 3), GridPosition(4, 3), 0.9f),
                    MovingItem(ItemId.IRON_INGOT.key, 1, GridPosition(5, 5), GridPosition(5, 6), 0.6f),
                ),
                selectedCell = GridPosition(5, 3),
            ),
            selectedCell = GridPosition(5, 3),
            boost = BoostUiState(isOverdriveActive = true, remainingMillis = 96_000L),
            dialog = FactoryDialog.None,
        )

        compose.setContent {
            AssemblyLineTycoonTheme(darkTheme = true) {
                FactoryScreen(state = state, onIntent = {})
            }
        }
        compose.waitForIdle()

        // Путь относительный: абсолютный сломал бы прогон в CI, где нет
        // домашнего каталога разработчика.
        val output = File("build/reports/screenshots/factory-screen.png")
        output.parentFile?.mkdirs()
        compose.onNodeWithTag(FACTORY_SCREEN_TAG).captureRoboImage(output.path)

        assertTrue("Снимок должен быть непустым", output.length() > 0)
    }
}
