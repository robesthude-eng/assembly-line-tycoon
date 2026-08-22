package com.example.assemblylinetycoon.app

import android.app.Application

/**
 * Точка входа процесса.
 *
 * Здесь только создание графа зависимостей. Рекламный SDK намеренно не
 * инициализируется: до экрана согласия (152-ФЗ) никакие данные уходить не должны.
 */
class GameApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
