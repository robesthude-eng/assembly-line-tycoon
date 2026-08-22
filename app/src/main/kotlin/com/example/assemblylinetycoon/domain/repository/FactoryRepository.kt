package com.example.assemblylinetycoon.domain.repository

import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.Machine
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к состоянию завода: поле и машины на нём.
 *
 * Отделено от [GameRepository], который отвечает за сохранение целиком:
 * завод меняется часто и по частям, а сохранение снимается редко и целиком.
 * Смешивать эти два ритма в одном интерфейсе — верный способ получить
 * запись всего состояния на каждое движение предмета по ленте.
 *
 * Заготовка: реализация появится вместе с игровым циклом.
 */
interface FactoryRepository {

    /** Текущее поле. Поток, потому что на него подписан рендерер. */
    fun observeGrid(): Flow<FactoryGrid>

    /** Машины, установленные на поле. */
    fun observeMachines(): Flow<List<Machine>>

    /** Заменить поле целиком — результат такта симуляции. */
    suspend fun updateGrid(grid: FactoryGrid)

    /** Добавить машину; возвращает её с присвоенным идентификатором. */
    suspend fun addMachine(machine: Machine): Machine

    /** Обновить состояние одной машины. */
    suspend fun updateMachine(machine: Machine)

    /** Снести машину с поля. */
    suspend fun removeMachine(machineId: Int)

    /** Очистить завод — используется при сбросе прогресса. */
    suspend fun clear()
}
