package com.example.assemblylinetycoon.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.assemblylinetycoon.domain.catalog.ItemCatalog
import com.example.assemblylinetycoon.domain.model.Cell
import com.example.assemblylinetycoon.domain.model.CellType
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemShape
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.MovingItem
import com.example.assemblylinetycoon.presentation.state.FactoryRenderModel
import com.example.assemblylinetycoon.presentation.ui.render.FactoryGeometry
import com.example.assemblylinetycoon.presentation.ui.render.FactoryLabels
import com.example.assemblylinetycoon.presentation.ui.render.FactoryPalette

/** Тег для UI-тестов: холст завода. */
const val FACTORY_CANVAS_TAG = "factory_canvas"

/**
 * Отрисовка завода.
 *
 * Жёсткое правило проекта: **рендерер только читает состояние**. Он не хранит
 * игровых данных, ничего не считает и не меняет; единственное, что он умеет
 * наружу, — сообщить, в какую клетку попал палец. Что с этим делать, решает
 * ViewModel, а меняет состояние движок.
 *
 * Canvas вместо дерева Compose-виджетов выбран потому, что на поле 10×10
 * одновременно движутся десятки предметов: сотня перекомпоновок в кадре
 * дороже одной отрисовки.
 *
 * Что сделано ради производительности:
 *  * геометрия и разметка подписей считаются в `remember` и переживают кадры —
 *    во время отрисовки только чтение;
 *  * цвета разобраны заранее в [FactoryPalette];
 *  * данные приходят ссылками на неизменяемые доменные объекты, новых
 *    коллекций на кадр не создаётся.
 */
@Composable
fun FactoryCanvas(
    model: FactoryRenderModel,
    onCellTapped: (GridPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val geometry = remember(model.width, model.height, widthPx, heightPx) {
            FactoryGeometry(
                gridWidth = model.width,
                gridHeight = model.height,
                canvasWidth = widthPx,
                canvasHeight = heightPx,
            )
        }

        val textMeasurer = rememberTextMeasurer()
        // Значки машин измеряются один раз на размер клетки, а не каждый кадр:
        // разметка текста — самая дорогая операция в этой отрисовке.
        val glyphs = remember(geometry.cellSize, textMeasurer) {
            measureGlyphs(textMeasurer, geometry.cellSize)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag(FACTORY_CANVAS_TAG)
                .pointerInput(geometry) {
                    detectTapGestures { offset: Offset ->
                        geometry.cellAt(offset.x, offset.y)?.let(onCellTapped)
                    }
                },
        ) {
            drawFactory(model, geometry, glyphs)
        }
    }
}

private fun measureGlyphs(
    measurer: TextMeasurer,
    cellSize: Float,
): Map<MachineType, TextLayoutResult> {
    if (cellSize <= 0f) return emptyMap()
    val style = TextStyle(
        fontSize = (cellSize * 0.30f).coerceIn(8f, 22f).sp,
        fontWeight = FontWeight.Bold,
        color = FactoryPalette.machineText,
    )
    return MachineType.entries.associateWith { type ->
        measurer.measure(text = FactoryLabels.machineGlyph(type), style = style)
    }
}

/** Порядок слоёв: поле → ленты → машины → предметы → выделение. */
private fun DrawScope.drawFactory(
    model: FactoryRenderModel,
    geometry: FactoryGeometry,
    glyphs: Map<MachineType, TextLayoutResult>,
) {
    if (geometry.cellSize <= 0f) return

    drawRect(color = FactoryPalette.background, size = size)

    val grid = model.grid
    for (y in 0 until grid.height) {
        for (x in 0 until grid.width) {
            val position = GridPosition(x, y)
            val cell = grid[position] ?: continue
            drawCell(position, cell, geometry)
        }
    }

    model.machines.values.forEach { machine ->
        drawMachine(machine, geometry, glyphs[machine.type])
    }

    model.movingItems.forEach { item ->
        drawMovingItem(item, geometry)
    }

    model.selectedCell?.let { drawSelection(it, geometry) }
}

private fun DrawScope.drawCell(position: GridPosition, cell: Cell, geometry: FactoryGeometry) {
    val cellSize = geometry.cellSize
    val topLeft = Offset(geometry.left(position.x), geometry.top(position.y))
    val cellArea = Size(cellSize, cellSize)

    when (cell.type) {
        CellType.EMPTY -> {
            drawRect(color = FactoryPalette.emptyCell, topLeft = topLeft, size = cellArea)
            drawRect(
                color = FactoryPalette.gridLine,
                topLeft = topLeft,
                size = cellArea,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
            )
        }

        CellType.BELT -> {
            val inset = cellSize * 0.08f
            drawRect(
                color = FactoryPalette.belt,
                topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
                size = Size(cellSize - inset * 2, cellSize - inset * 2),
            )
            drawBeltArrow(position, cell.direction, geometry)
        }

        // Клетки машин рисует drawMachine: цвет и значок зависят от самой
        // машины, а не только от типа ячейки.
        CellType.MACHINE, CellType.SPAWNER, CellType.EXPORTER -> Unit
    }
}

/** Стрелка направления ленты: без неё поле нечитаемо, когда предметы не едут. */
private fun DrawScope.drawBeltArrow(
    position: GridPosition,
    direction: Direction,
    geometry: FactoryGeometry,
) {
    val cx = geometry.centerX(position.x)
    val cy = geometry.centerY(position.y)
    val arm = geometry.cellSize * 0.22f

    val tip = Offset(cx + direction.dx * arm, cy + direction.dy * arm)
    // Основание стрелки — отрезок, перпендикулярный направлению движения.
    val backX = cx - direction.dx * arm * 0.6f
    val backY = cy - direction.dy * arm * 0.6f
    val sideX = direction.dy * arm * 0.55f
    val sideY = direction.dx * arm * 0.55f

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(backX + sideX, backY + sideY)
        lineTo(backX - sideX, backY - sideY)
        close()
    }
    drawPath(path = path, color = FactoryPalette.beltArrow)
}

private fun DrawScope.drawMachine(
    machine: Machine,
    geometry: FactoryGeometry,
    glyph: TextLayoutResult?,
) {
    val cellSize = geometry.cellSize
    val inset = cellSize * 0.06f
    val topLeft = Offset(geometry.left(machine.position.x) + inset, geometry.top(machine.position.y) + inset)
    val body = Size(cellSize - inset * 2, cellSize - inset * 2)

    drawRoundRect(
        color = FactoryPalette.machine(machine.type),
        topLeft = topLeft,
        size = body,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellSize * 0.14f),
    )

    glyph?.let {
        drawText(
            textLayoutResult = it,
            topLeft = Offset(
                x = topLeft.x + (body.width - it.size.width) / 2f,
                y = topLeft.y + (body.height - it.size.height) / 2f - cellSize * 0.06f,
            ),
        )
    }

    drawUpgradeLevel(machine, topLeft, body, cellSize)
    drawMachineProgress(machine, topLeft, body, cellSize)
}

/**
 * Уровень апгрейда — точками, а не цифрой.
 *
 * Цифра требовала бы измерения текста для каждого значения на каждом кадре;
 * до пяти точек читаются мгновенно, дальше показываем «полную» шкалу. Это
 * заглушка под будущие иконки, а не игровая механика.
 */
private fun DrawScope.drawUpgradeLevel(
    machine: Machine,
    topLeft: Offset,
    body: Size,
    cellSize: Float,
) {
    if (machine.level <= 0) return
    val dots = machine.level.coerceAtMost(MAX_LEVEL_DOTS)
    val radius = cellSize * 0.035f
    val step = radius * 2.6f
    val startX = topLeft.x + body.width / 2f - step * (dots - 1) / 2f
    val y = topLeft.y + body.height * 0.80f

    repeat(dots) { index ->
        drawCircle(
            color = FactoryPalette.machineText,
            radius = radius,
            center = Offset(startX + index * step, y),
        )
    }
}

/** Полоса такта снизу корпуса: видно, что машина работает, а не стоит. */
private fun DrawScope.drawMachineProgress(
    machine: Machine,
    topLeft: Offset,
    body: Size,
    cellSize: Float,
) {
    val barHeight = cellSize * 0.08f
    val barTop = topLeft.y + body.height - barHeight - cellSize * 0.04f
    val barLeft = topLeft.x + cellSize * 0.08f
    val barWidth = body.width - cellSize * 0.16f

    drawRect(
        color = FactoryPalette.progressTrack,
        topLeft = Offset(barLeft, barTop),
        size = Size(barWidth, barHeight),
    )

    val fill = when (machine.status) {
        MachineStatus.CRAFTING -> progressFraction(machine)
        MachineStatus.OUTPUT_EJECT -> 1f
        MachineStatus.IDLE -> 0f
    }
    if (fill > 0f) {
        drawRect(
            color = FactoryPalette.progressFill,
            topLeft = Offset(barLeft, barTop),
            size = Size(barWidth * fill, barHeight),
        )
    }
}

/**
 * Доля такта берётся из накопленного времени машины.
 *
 * Длительность такта зависит от рецепта и уровня — её считает домен. Рендерер
 * не имеет права её вычислять, поэтому при неизвестном рецепте честно рисует
 * пустую полосу вместо выдумывания числа.
 */
private fun progressFraction(machine: Machine): Float {
    val duration = machine.recipeOutputId
        ?.let { com.example.assemblylinetycoon.domain.catalog.RecipeCatalog.forOutput(it) }
        ?.let {
            com.example.assemblylinetycoon.domain.catalog.MachineCatalog
                .craftDuration(it.baseDurationMillis, machine.level)
        }
        ?: return 0f
    return machine.progress(duration)
}

private fun DrawScope.drawMovingItem(item: MovingItem, geometry: FactoryGeometry) {
    val x = geometry.interpolateX(item.from, item.to, item.progress)
    val y = geometry.interpolateY(item.from, item.to, item.progress)
    val radius = geometry.cellSize * 0.16f
    val color = FactoryPalette.item(item.itemId)
    val shape = ItemCatalog.find(item.itemId)?.visual?.shape ?: ItemShape.CIRCLE

    when (shape) {
        ItemShape.CIRCLE -> drawCircle(color = color, radius = radius, center = Offset(x, y))
        ItemShape.SQUARE -> drawRect(
            color = color,
            topLeft = Offset(x - radius, y - radius),
            size = Size(radius * 2, radius * 2),
        )
        ItemShape.TRIANGLE -> drawPath(
            path = trianglePath(x, y, radius),
            color = color,
        )
        ItemShape.HEXAGON -> drawRoundRect(
            color = color,
            topLeft = Offset(x - radius, y - radius * 0.85f),
            size = Size(radius * 2, radius * 1.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.5f),
        )
    }

    // Тонкая обводка отделяет предмет от ленты того же оттенка.
    drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = radius,
        center = Offset(x, y),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
    )
}

private fun trianglePath(x: Float, y: Float, radius: Float): Path = Path().apply {
    moveTo(x, y - radius)
    lineTo(x + radius, y + radius * 0.8f)
    lineTo(x - radius, y + radius * 0.8f)
    close()
}

private fun DrawScope.drawSelection(position: GridPosition, geometry: FactoryGeometry) {
    val cellSize = geometry.cellSize
    drawRoundRect(
        color = FactoryPalette.selection,
        topLeft = Offset(geometry.left(position.x), geometry.top(position.y)),
        size = Size(cellSize, cellSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellSize * 0.12f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = cellSize * 0.06f),
    )
}

private const val MAX_LEVEL_DOTS = 5
