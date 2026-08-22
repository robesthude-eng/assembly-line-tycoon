package com.example.assemblylinetycoon.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.assemblylinetycoon.core.utils.NumberFormatter
import com.example.assemblylinetycoon.presentation.state.BoostUiState
import com.example.assemblylinetycoon.presentation.ui.theme.AssemblyLineTycoonTheme

/** Теги для UI-тестов HUD. */
const val HUD_COINS_TAG = "hud_coins"
const val HUD_RATE_TAG = "hud_rate"
const val HUD_BOOST_TAG = "hud_boost"

/**
 * Верхняя панель: сколько денег, сколько приносит завод, какие усиления идут.
 *
 * Панель принимает готовые значения и не имеет доступа ни к движку, ни к
 * ViewModel: единственный источник данных — UiState. Поэтому её можно
 * отрисовать в Preview и в тесте, передав числа руками.
 */
@Composable
fun FactoryHud(
    coins: Long,
    coinsPerSecond: Double,
    boost: BoostUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudValue(
                caption = "Баланс",
                // Компактная запись: на балансе быстро появляются миллионы,
                // и полное число выдавило бы остальные показатели с экрана.
                value = NumberFormatter.format(coins),
                tag = HUD_COINS_TAG,
                emphasised = true,
            )

            HudValue(
                caption = "Производство",
                // В минуту, а не в секунду: на старте завод приносит меньше
                // монеты в секунду, и показатель выглядел бы нулевым.
                value = "${NumberFormatter.format((coinsPerSecond * 60).toLong())}/мин",
                tag = HUD_RATE_TAG,
            )

            BoostChip(boost = boost)
        }
    }
}

@Composable
private fun HudValue(
    caption: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.testTag(tag),
            style = if (emphasised) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold,
            color = if (emphasised) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Место под усиления.
 *
 * Показывает состояние «Ускорения» из симуляции; когда оно выключено, плашка
 * не исчезает, а гаснет — иначе HUD прыгал бы шириной при каждом включении.
 */
@Composable
private fun BoostChip(
    boost: BoostUiState,
    modifier: Modifier = Modifier,
) {
    val active = boost.isOverdriveActive
    Card(
        modifier = modifier.testTag(HUD_BOOST_TAG),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = "Ускорение",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (active) {
                    NumberFormatter.formatDuration(boost.remainingMillis)
                } else {
                    "нет"
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Preview(name = "HUD", showBackground = true)
@Composable
private fun FactoryHudPreview() {
    AssemblyLineTycoonTheme(darkTheme = true) {
        FactoryHud(
            coins = 12_540L,
            coinsPerSecond = 3.5,
            boost = BoostUiState(isOverdriveActive = true, remainingMillis = 125_000L),
        )
    }
}
