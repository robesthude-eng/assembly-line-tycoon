package com.example.assemblylinetycoon.domain.save

import com.example.assemblylinetycoon.domain.model.GameState

/**
 * Контракт автосохранения. Реализация — в слое данных (`SaveManager`).
 *
 * Интерфейс нужен ровно для того, чтобы презентация могла сказать «игра
 * началась» и «игра свёрнута», не зная ни про DataStore, ни про корутинные
 * скоупы, в которых идёт запись.
 */
interface AutoSave {

    /** Идёт ли сейчас периодическое сохранение. */
    val isRunning: Boolean

    /**
     * Начать периодическое сохранение.
     *
     * @param snapshot источник актуального состояния: сохранять надо то, что
     *   в движке сейчас, а не то, что было в момент запуска цикла.
     */
    fun start(snapshot: () -> GameState)

    /** Остановить периодическое сохранение. */
    fun stop()

    /** Немедленно записать состояние и дождаться завершения записи. */
    suspend fun saveNow(state: GameState)
}
