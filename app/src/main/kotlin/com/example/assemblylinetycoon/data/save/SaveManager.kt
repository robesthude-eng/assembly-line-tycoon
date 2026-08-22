package com.example.assemblylinetycoon.data.save

import com.example.assemblylinetycoon.core.constants.GameConstants
import com.example.assemblylinetycoon.core.dispatcher.DispatcherProvider
import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.repository.GameRepository
import com.example.assemblylinetycoon.domain.save.AutoSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Автосохранение игры.
 *
 * Живёт в слое данных, а не во ViewModel. Причина не в чистоте схемы:
 * автосейв обязан переживать смену экрана и поворот устройства, а ViewModel
 * этого не гарантирует. Экран лишь сообщает менеджеру, что игра идёт, —
 * когда именно писать файл, решает менеджер.
 *
 * Что здесь важно:
 *  * **не блокировать главный поток** — вся запись уходит на IO-диспетчер;
 *  * **отменяемость** — цикл живёт в собственном [Job], который снимается
 *    вместе с уходом приложения в фон;
 *  * **никаких одновременных сохранений** — [mutex] превращает совпавшие по
 *    времени автосейв и сохранение в фон в две последовательные записи.
 *    Без него две корутины могли бы писать один файл наперегонки, а победил
 *    бы тот, кто пришёл вторым, то есть более старый снапшот.
 *
 * Отметку времени ставит [SaveManager], потому что она обязана совпадать с
 * моментом реальной записи: именно от неё считается офлайн-доход.
 */
class SaveManager(
    private val repository: GameRepository,
    private val dispatchers: DispatcherProvider,
    private val timeProvider: TimeProvider,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = GameConstants.AUTOSAVE_INTERVAL_MS,
) : AutoSave {

    private val mutex = Mutex()
    private var autosaveJob: Job? = null

    /** Идёт ли сейчас цикл автосохранения. Нужен тестам и отладке. */
    override val isRunning: Boolean get() = autosaveJob?.isActive == true

    /**
     * Запуск периодического сохранения.
     *
     * @param snapshot источник актуального состояния. Менеджер намеренно не
     *   держит ссылку на движок: его дело — записать то, что дадут, а не
     *   разбираться в устройстве симуляции.
     */
    override fun start(snapshot: () -> GameState) {
        if (isRunning) return // повторный запуск не должен плодить циклы
        autosaveJob = scope.launch {
            while (isActive) {
                delay(intervalMillis)
                save(snapshot())
            }
        }
    }

    /** Остановка цикла. Уже начатая запись доводится до конца. */
    override fun stop() {
        autosaveJob?.cancel()
        autosaveJob = null
    }

    /**
     * Немедленное сохранение — уход в фон, закрытие экрана, важное событие.
     *
     * Ждёт завершения записи: вызывающий код должен иметь возможность
     * убедиться, что прогресс на диске, прежде чем система убьёт процесс.
     */
    override suspend fun saveNow(state: GameState) = save(state)

    private suspend fun save(state: GameState) {
        // Ровно одна запись в момент времени. Второй вызов не отбрасывается,
        // а ждёт: потерянное сохранение хуже, чем задержанное.
        mutex.withLock {
            withContext(dispatchers.io) {
                repository.saveGameState(
                    state.copy(
                        lastSavedAtMillis = timeProvider.nowMillis(),
                        // Запись состоялась — значит игра точно начата,
                        // и офлайн при следующем запуске уже можно считать.
                        isInitialized = true,
                    ),
                )
            }
        }
    }
}
