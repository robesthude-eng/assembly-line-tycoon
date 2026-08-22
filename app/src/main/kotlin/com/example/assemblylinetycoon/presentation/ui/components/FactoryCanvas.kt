package com.example.assemblylinetycoon.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.example.assemblylinetycoon.presentation.state.GameUiState

/**
 * Слой отрисовки завода.
 *
 * Жёсткое правило проекта: **рендерер только читает состояние**. Он не хранит
 * игровые данные, не считает экономику и не меняет [GameUiState]. Всё, что он
 * умеет наружу, — сообщить координату касания через [onCellTapped]; решение,
 * что с ней делать, принимает ViewModel, а изменение — игровой движок.
 *
 * Canvas выбран вместо Compose-виджетов потому, что на экране одновременно
 * движутся сотни предметов: дерево композиции такого не выдержит.
 */
@Composable
fun FactoryCanvas(
    state: GameUiState,
    onCellTapped: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset: Offset ->
                    // TODO(этап 3): перевод экранных координат в координаты сетки
                    onCellTapped(offset.x.toInt(), offset.y.toInt())
                }
            },
    ) {
        // TODO(этап 3): отрисовка сетки, конвейеров, машин и предметов в пути.
        // Здесь допустимы только операции рисования по данным из state.
    }
}
