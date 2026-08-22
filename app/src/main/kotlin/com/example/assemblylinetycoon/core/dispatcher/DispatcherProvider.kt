package com.example.assemblylinetycoon.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Абстракция над диспетчерами корутин.
 *
 * Нужна, чтобы игровой движок и use case'ы можно было тестировать на
 * `TestDispatcher`, не завися от Android Main Looper.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}
