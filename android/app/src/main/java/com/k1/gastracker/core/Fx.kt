package com.k1.gastracker.core

import java.time.LocalDate

typealias RatesTable = Map<Triple<LocalDate, String, String>, Double>

sealed class ConvertedCost {
    data class Ready(val amount: Double) : ConvertedCost()
    data object Missing : ConvertedCost()
    data object Unavailable : ConvertedCost()
}

data class FxConversion(
    val costs: Map<Refill, ConvertedCost>,
    val error: String? = null,
    val unavailableCount: Int = 0,
)

fun nearestRateAt(
    rates: RatesTable,
    day: LocalDate,
    fromCurrency: String,
    toCurrency: String,
    maxLookback: Int = 7,
): Double? {
    if (fromCurrency == toCurrency) return 1.0
    rates[Triple(day, fromCurrency, toCurrency)]?.let { return it }
    for (i in 1..maxLookback) {
        rates[Triple(day.minusDays(i.toLong()), fromCurrency, toCurrency)]?.let { return it }
    }
    return null
}

fun convertAmount(amount: Double, rate: Double?): Double? =
    rate?.times(amount)
