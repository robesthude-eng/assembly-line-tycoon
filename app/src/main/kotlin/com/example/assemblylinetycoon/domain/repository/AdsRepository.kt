package com.example.assemblylinetycoon.domain.repository

import com.example.assemblylinetycoon.domain.model.AdPlacement
import com.example.assemblylinetycoon.domain.model.AdResult
import kotlinx.coroutines.flow.Flow

/**
 * Контракт рекламы со стороны домена.
 *
 * Домен не знает про Yandex SDK и не видит Activity — этим занимается
 * слой monetization. Так рекламную сеть можно заменить, не трогая геймплей.
 */
interface AdsRepository {
    /** Готово ли объявление к показу для данного плейсмента. */
    fun observeAvailability(placement: AdPlacement): Flow<Boolean>

    /** Предзагрузка объявления. */
    suspend fun preload(placement: AdPlacement)

    /** Показ. Возвращает результат: награда получена, закрыто, ошибка. */
    suspend fun show(placement: AdPlacement): AdResult
}
