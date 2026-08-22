package com.example.assemblylinetycoon.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ConveyorAmber,
    onPrimary = SteelDark,
    secondary = CopperAccent,
    tertiary = SignalGreen,
    background = SteelDark,
    onBackground = OnDarkPrimary,
    surface = SteelSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = SteelSurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,
    error = SignalRed,
)

private val LightColors = lightColorScheme(
    primary = ConveyorAmberDark,
    secondary = CopperAccent,
    tertiary = SignalGreen,
    error = SignalRed,
)

/**
 * Тема приложения.
 *
 * Динамические цвета Material You сознательно не используются: игровой экран
 * рисуется на Canvas фиксированной палитрой, и системная перекраска UI ломала бы
 * визуальную связку интерфейса и завода.
 */
@Composable
fun AssemblyLineTycoonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
