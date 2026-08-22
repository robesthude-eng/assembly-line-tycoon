package com.example.assemblylinetycoon.core.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Перезапускаемая корутина: предыдущий [Job] отменяется перед стартом нового.
 * Используется игровым циклом при перезапуске тикера.
 */
fun CoroutineScope.relaunch(current: Job?, block: suspend CoroutineScope.() -> Unit): Job {
    current?.cancel()
    return launch(block = block)
}
