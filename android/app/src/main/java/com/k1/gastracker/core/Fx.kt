package com.k1.gastracker.core

import java.time.LocalDate

typealias RatesTable = Map<Triple<LocalDate, String, String>, Double>

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
