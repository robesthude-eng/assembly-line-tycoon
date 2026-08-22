package com.example.assemblylinetycoon.domain.model

/** Итог попытки покупки. */
sealed interface PurchaseResult {
    data class Success(val productId: String) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failure(val reason: String) : PurchaseResult
    /** Биллинг недоступен: приложение установлено не из RuStore или сервис отключён. */
    data object Unavailable : PurchaseResult
}
