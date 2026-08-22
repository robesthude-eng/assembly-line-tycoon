package com.example.assemblylinetycoon.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.assemblylinetycoon.presentation.ui.theme.AssemblyLineTycoonTheme

/**
 * Единственная Activity приложения.
 *
 * В ней нет игровой логики: только тема, навигация и проброс системных событий.
 * Любая попытка посчитать здесь экономику или подвигать предметы — нарушение
 * архитектурного правила проекта (см. ARCHITECTURE.md).
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as GameApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Требование RuStore Billing: доставить стартовый intent в SDK.
        if (savedInstanceState == null) {
            container.billingManager.onNewIntent(intent)
        }

        setContent {
            AssemblyLineTycoonTheme {
                AppNavHost(container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Возврат из платёжного флоу RuStore приходит сюда по deeplink.
        container.billingManager.onNewIntent(intent)
    }
}
