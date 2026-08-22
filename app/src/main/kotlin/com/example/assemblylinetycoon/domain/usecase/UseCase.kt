package com.example.assemblylinetycoon.domain.usecase

/**
 * Базовые контракты use case'ов.
 *
 * Отдельные интерфейсы вместо одного абстрактного класса: часть сценариев
 * возвращает поток, часть — одно значение, часть ничего не возвращает.
 */
interface SuspendUseCase<in P, out R> {
    suspend operator fun invoke(params: P): R
}

interface NoParamsUseCase<out R> {
    suspend operator fun invoke(): R
}
