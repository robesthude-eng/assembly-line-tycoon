package com.example.assemblylinetycoon.presentation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import com.example.assemblylinetycoon.presentation.state.BoostUiState
import com.example.assemblylinetycoon.presentation.state.FactoryDialog
import com.example.assemblylinetycoon.presentation.state.FactoryEffect
import com.example.assemblylinetycoon.presentation.state.FactoryIntent
import com.example.assemblylinetycoon.presentation.state.FactoryRenderModel
import com.example.assemblylinetycoon.presentation.state.FactoryUiState
import com.example.assemblylinetycoon.presentation.ui.components.EmptyCellDialog
import com.example.assemblylinetycoon.presentation.ui.components.FactoryCanvas
import com.example.assemblylinetycoon.presentation.ui.components.FactoryHud
import com.example.assemblylinetycoon.presentation.ui.components.MachineDialog
import com.example.assemblylinetycoon.presentation.ui.theme.AssemblyLineTycoonTheme
import com.example.assemblylinetycoon.presentation.viewmodel.FactoryViewModel

/** Тег для UI-тестов: корень экрана завода. */
const val FACTORY_SCREEN_TAG = "factory_screen"

/**
 * «Умная» половина экрана: знает про ViewModel и жизненный цикл.
 *
 * Разделение на Route и Screen нужно ради тестируемости: вторая функция
 * принимает готовое состояние и лямбду, поэтому проверяется без DI и
 * рисуется в Preview.
 */
@Composable
fun FactoryRoute(
    viewModel: FactoryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Симуляция крутится, только пока экран виден: в фоне доход считает
    // офлайн-калькулятор, а не работающий тикер.
    LifecycleStartEffect(viewModel) {
        viewModel.onIntent(FactoryIntent.ScreenStarted)
        onStopOrDispose { viewModel.onIntent(FactoryIntent.ScreenStopped) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is FactoryEffect.ShowMessage -> effect.text
                is FactoryEffect.NotImplementedYet -> "${effect.feature}: появится на следующем этапе"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    FactoryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * «Глупая» половина: только отображение состояния и отправка намерений.
 *
 * Здесь нет ни одного расчёта: всё, что видно на экране, уже посчитано
 * симуляцией и разложено маппером по полям [FactoryUiState].
 */
@Composable
fun FactoryScreen(
    state: FactoryUiState,
    onIntent: (FactoryIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(FACTORY_SCREEN_TAG),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FactoryHud(
                coins = state.coins,
                coinsPerSecond = state.coinsPerSecond,
                boost = state.boost,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FactoryCanvas(
                    model = state.render,
                    onCellTapped = { position -> onIntent(FactoryIntent.SelectCell(position)) },
                )

                if (state.isLoading) {
                    Text(
                        text = "Загрузка завода…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    FactoryDialogs(state = state, onIntent = onIntent)
}

/**
 * Диалоги живут в состоянии, а не в локальной переменной экрана: открытая
 * карточка машины переживает поворот телефона и не показывается дважды.
 */
@Composable
private fun FactoryDialogs(
    state: FactoryUiState,
    onIntent: (FactoryIntent) -> Unit,
) {
    when (val dialog = state.dialog) {
        FactoryDialog.None -> Unit

        is FactoryDialog.MachineInfo -> MachineDialog(
            machine = dialog.machine,
            onUpgrade = { id -> onIntent(FactoryIntent.UpgradeMachine(id)) },
            onDismiss = { onIntent(FactoryIntent.CloseDialog) },
        )

        is FactoryDialog.EmptyCell -> EmptyCellDialog(
            position = dialog.position,
            options = dialog.options,
            onBuild = { type -> onIntent(FactoryIntent.PlaceMachine(dialog.position, type)) },
            onDismiss = { onIntent(FactoryIntent.CloseDialog) },
        )
    }
}

// ── Preview ─────────────────────────────────────────────────────────────────

/** Небольшой демонстрационный завод: карьер, лента, экспортёр и предмет в пути. */
private fun previewState(): FactoryUiState {
    val spawner = Machine(
        id = 1,
        type = MachineType.SPAWNER,
        position = GridPosition(1, 4),
        facing = Direction.RIGHT,
        level = 2,
        recipeOutputId = "iron_ore",
        status = MachineStatus.CRAFTING,
        elapsedMillis = 900L,
    )
    val exporter = Machine(
        id = 2,
        type = MachineType.EXPORTER,
        position = GridPosition(5, 4),
        facing = Direction.RIGHT,
    )
    val grid = FactoryGrid.EMPTY
        .withMachine(spawner)
        .withMachine(exporter)
        .withBelt(GridPosition(2, 4), Direction.RIGHT)
        .withBelt(GridPosition(3, 4), Direction.RIGHT)
        .withBelt(GridPosition(4, 4), Direction.RIGHT)

    return FactoryUiState(
        isLoading = false,
        coins = 12_540L,
        coinsPerSecond = 4.2,
        render = FactoryRenderModel(
            grid = grid,
            machines = mapOf(1 to spawner, 2 to exporter),
            movingItems = listOf(
                MovingItem(
                    itemId = "iron_ore",
                    from = GridPosition(2, 4),
                    to = GridPosition(3, 4),
                    progress = 0.6f,
                ),
            ),
            selectedCell = GridPosition(1, 4),
        ),
        selectedCell = GridPosition(1, 4),
        boost = BoostUiState(isOverdriveActive = true, remainingMillis = 96_000L),
    )
}

@Preview(name = "Завод — тёмная тема", showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun FactoryScreenPreview() {
    AssemblyLineTycoonTheme(darkTheme = true) {
        FactoryScreen(state = previewState(), onIntent = {})
    }
}
