package com.example.assemblylinetycoon.monetization.billing

import android.content.Intent
import com.example.assemblylinetycoon.domain.model.BillingProduct
import com.example.assemblylinetycoon.domain.model.PurchaseResult
import kotlinx.coroutines.flow.Flow

/**
 * Контракт биллинга.
 *
 * [onNewIntent] нужен из-за схемы оплаты RuStore: приложение уходит в платёжный
 * флоу и возвращается по deeplink — без проброса интента покупка «зависает».
 */
interface BillingManager {
    suspend fun isAvailable(): Boolean
    suspend fun loadProducts(ids: List<String>): List<BillingProduct>
    fun observePurchases(): Flow<Set<String>>
    suspend fun purchase(productId: String): PurchaseResult
    suspend fun restorePurchases(): Set<String>
    fun onNewIntent(intent: Intent?)
    fun release()
}
