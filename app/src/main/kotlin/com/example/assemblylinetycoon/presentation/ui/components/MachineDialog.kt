package com.example.assemblylinetycoon.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.assemblylinetycoon.core.utils.NumberFormatter
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.presentation.state.MachineUiInfo
import com.example.assemblylinetycoon.presentation.ui.render.FactoryLabels
import com.example.assemblylinetycoon.presentation.ui.theme.AssemblyLineTycoonTheme

/** Теги для UI-тестов диалогов. */
const val MACHINE_DIALOG_TAG = "machine_dialog"
const val MACHINE_DIALOG_UPGRADE_TAG = "machine_dialog_upgrade"
const val EMPTY_CELL_DIALOG_TAG = "empty_cell_dialog"

/**
 * Карточка машины.
 *
 * Все числа приходят готовыми в [MachineUiInfo]: диалог ничего не считает —
 * ни цену улучшения, ни длительность такта. Он показывает то, что домен уже
 * посчитал, и отправляет наверх намерение игрока.
 */
@Composable
fun MachineDialog(
    machine: MachineUiInfo,
    onUpgrade: (machineId: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(MACHINE_DIALOG_TAG),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = FactoryLabels.machineName(machine.type),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Уровень", machine.level.toString())
                InfoRow("Состояние", FactoryLabels.status(machine.status))
                InfoRow("Ячейка", "${machine.position.x}, ${machine.position.y}")

                machine.outputItemName?.let { InfoRow("Производит", it) }
                if (machine.craftDurationMillis > 0L) {
                    InfoRow("Такт", NumberFormatter.formatDuration(machine.craftDurationMillis))
                }

                if (machine.status == MachineStatus.CRAFTING) {
                    LinearProgressIndicator(
                        progress = { machine.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUpgrade(machine.id) },
                modifier = Modifier.testTag(MACHINE_DIALOG_UPGRADE_TAG),
                // Кнопка неактивна, когда денег не хватает. Сравнение делает
                // домен (canAffordUpgrade), диалог только читает флаг.
                enabled = machine.canAffordUpgrade,
            ) {
                Text("Улучшить · ${NumberFormatter.format(machine.upgradeCost)}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

/**
 * Диалог пустой ячейки — точка входа для будущей постройки.
 *
 * Список машин со стоимостью появится вместе с командой постройки в движке;
 * пока диалог честно говорит, что здесь свободно, и не показывает цен,
 * которых ещё нет в этом слое.
 */
@Composable
fun EmptyCellDialog(
    position: GridPosition,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(EMPTY_CELL_DIALOG_TAG),
        onDismissRequest = onDismiss,
        title = { Text("Свободная ячейка") },
        text = {
            Text("Ячейка ${position.x}, ${position.y} пуста. Постройка появится на следующем этапе.")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Понятно") }
        },
    )
}

@Composable
private fun InfoRow(caption: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(name = "Карточка машины")
@Composable
private fun MachineDialogPreview() {
    AssemblyLineTycoonTheme(darkTheme = true) {
        MachineDialog(
            machine = MachineUiInfo(
                id = 1,
                type = MachineType.SMELTER,
                position = GridPosition(2, 3),
                level = 3,
                status = MachineStatus.CRAFTING,
                progress = 0.45f,
                outputItemName = "Железный слиток",
                craftDurationMillis = 3_400L,
                upgradeCost = 1_240L,
                canAffordUpgrade = true,
            ),
            onUpgrade = {},
            onDismiss = {},
        )
    }
}
