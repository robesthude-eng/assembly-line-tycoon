package com.example.assemblylinetycoon.domain.repository

import com.example.assemblylinetycoon.domain.model.BillingProduct
import com.example.assemblylinetycoon.domain.model.PurchaseResult
import kotlinx.coroutines.flow.Flow

/** Контракт покупок. Реализация — обёртка над RuStore Billing в слое monetization. */
interface BillingRepository {
    /** Доступен ли биллинг на устройстве (установлен RuStore, поддерживается версия). */
    suspend fun isAvailable(): Boolean

    /** Каталог товаров. */
    suspend fun loadProducts(ids: List<String>): List<BillingProduct>

    /** Поток идентификаторов уже приобретённых товаров. */
    fun observePurchases(): Flow<Set<String>>

    /** Запуск покупки. */
    suspend fun purchase(productId: String): PurchaseResult

    /** Восстановление покупок (переустановка, смена устройства). */
    suspend fun restorePurchases(): Set<String>
}
