package com.example.assemblylinetycoon.core.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Извлекает подсостояние из потока UI-состояния и отбрасывает повторы.
 *
 * Позволяет Compose-экранам подписываться на узкий срез состояния и не
 * перерисовываться на каждое изменение всего [Flow].
 */
inline fun <T, R> Flow<T>.mapDistinct(crossinline selector: (T) -> R): Flow<R> =
    map { selector(it) }.distinctUntilChanged()
