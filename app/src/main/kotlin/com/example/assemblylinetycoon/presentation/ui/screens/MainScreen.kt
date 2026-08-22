package com.example.assemblylinetycoon.presentation.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.assemblylinetycoon.app.AppContainer
import com.example.assemblylinetycoon.app.AppNavHost

/** Тег для UI-тестов: корень приложения. */
const val MAIN_SCREEN_TAG = "main_screen"

/**
 * Корневой экран приложения.
 *
 * Отвечает за одно: даёт фон темы и держит навигацию. Игровой логики здесь
 * нет и не появится — иначе при добавлении второго экрана (магазин, дерево
 * технологий) состояние завода начало бы утекать в контейнер навигации.
 */
@Composable
fun MainScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(MAIN_SCREEN_TAG),
    ) {
        AppNavHost(container = container, navController = navController)
    }
}
