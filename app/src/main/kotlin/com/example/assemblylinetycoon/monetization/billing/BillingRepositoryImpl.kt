package com.example.assemblylinetycoon.monetization.billing

import com.example.assemblylinetycoon.domain.model.BillingProduct
import com.example.assemblylinetycoon.domain.model.PurchaseResult
import com.example.assemblylinetycoon.domain.repository.BillingRepository
import kotlinx.coroutines.flow.Flow

/** Мост между доменным [BillingRepository] и SDK-обёрткой [BillingManager]. */
class BillingRepositoryImpl(
    private val billingManager: BillingManager,
) : BillingRepository {

    override suspend fun isAvailable(): Boolean = billingManager.isAvailable()

    override suspend fun loadProducts(ids: List<String>): List<BillingProduct> =
        billingManager.loadProducts(ids)

    override fun observePurchases(): Flow<Set<String>> = billingManager.observePurchases()

    override suspend fun purchase(productId: String): PurchaseResult =
        billingManager.purchase(productId)

    override suspend fun restorePurchases(): Set<String> = billingManager.restorePurchases()
}
