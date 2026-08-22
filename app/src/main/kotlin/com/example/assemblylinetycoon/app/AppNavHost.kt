package com.example.assemblylinetycoon.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.assemblylinetycoon.presentation.ui.screens.FactoryRoute
import com.example.assemblylinetycoon.presentation.viewmodel.GameViewModel
import com.example.assemblylinetycoon.presentation.viewmodel.ViewModelFactory

/** Маршруты приложения. Строки в одном месте, чтобы не разъезжались. */
object Routes {
    const val FACTORY = "factory"
    // TODO: SHOP, SETTINGS, TECH_TREE
}

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.FACTORY) {
        composable(Routes.FACTORY) {
            val viewModel: GameViewModel = viewModel(factory = ViewModelFactory(container))
            FactoryRoute(viewModel = viewModel)
        }
    }
}
