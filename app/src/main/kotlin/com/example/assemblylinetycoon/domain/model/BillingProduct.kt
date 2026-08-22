package com.example.assemblylinetycoon.domain.model

/** Товар из каталога RuStore, приведённый к доменному виду. */
data class BillingProduct(
    val id: String,
    val title: String = "",
    val description: String = "",
    val formattedPrice: String = "",
    val priceMinorUnits: Long = 0L,
    val isPurchased: Boolean = false,
)
