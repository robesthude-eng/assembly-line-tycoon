package com.example.assemblylinetycoon.monetization.billing

import android.content.Intent
import com.example.assemblylinetycoon.domain.model.BillingProduct
import com.example.assemblylinetycoon.domain.model.PurchaseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Обёртка над RuStore Billing SDK (`ru.rustore.sdk:billingclient`).
 *
 * Реализация — отдельный этап. Заглушка возвращает [PurchaseResult.Unavailable],
 * то есть корректно отрабатывает случай «приложение установлено не из RuStore»,
 * который в бою встречается постоянно (сборки для тестеров, эмулятор).
 */
class RuStoreBillingManager : BillingManager {

    private val purchases = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun isAvailable(): Boolean = false
    // TODO(этап 6): RuStoreBillingClient.checkPurchasesAvailability()

    override suspend fun loadProducts(ids: List<String>): List<BillingProduct> = emptyList()
    // TODO(этап 6): billingClient.products.getProducts(ids)

    override fun observePurchases(): Flow<Set<String>> = purchases.asStateFlow()

    override suspend fun purchase(productId: String): PurchaseResult = PurchaseResult.Unavailable
    // TODO(этап 6): billingClient.purchases.purchaseProduct(productId)

    override suspend fun restorePurchases(): Set<String> = emptySet()

    override fun onNewIntent(intent: Intent?) {
        // TODO(этап 6): billingClient.onNewIntent(intent)
    }

    override fun release() = Unit
}
