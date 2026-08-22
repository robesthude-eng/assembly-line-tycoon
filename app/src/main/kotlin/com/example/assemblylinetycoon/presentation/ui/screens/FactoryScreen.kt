package com.example.assemblylinetycoon.presentation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.assemblylinetycoon.presentation.state.GameIntent
import com.example.assemblylinetycoon.presentation.state.GameUiState
import com.example.assemblylinetycoon.presentation.ui.components.FactoryCanvas
import com.example.assemblylinetycoon.presentation.ui.theme.AssemblyLineTycoonTheme
import com.example.assemblylinetycoon.presentation.viewmodel.GameViewModel

/**
 * Каркас главного экрана.
 *
 * Экран разделён на две функции: «умную» (получает ViewModel) и «глупую»
 * (принимает состояние и лямбды). Вторая тестируется и превьюится без всякого
 * DI — именно она обеспечивает работу Compose Preview.
 */
@Composable
fun FactoryRoute(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FactoryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
fun FactoryScreen(
    state: GameUiState,
    onIntent: (GameIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            FactoryCanvas(
                state = state,
                onCellTapped = { x, y -> onIntent(GameIntent.CellTapped(x, y)) },
            )
            // Плейсхолдер до появления игрового HUD.
            Text(
                text = "Конвейер: Завод Деталей",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(name = "Завод — тёмная тема", showBackground = true)
@Composable
private fun FactoryScreenPreview() {
    AssemblyLineTycoonTheme(darkTheme = true) {
        FactoryScreen(
            state = GameUiState(isLoading = false, coins = 12_500L),
            onIntent = {},
        )
    }
}
