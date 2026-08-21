package com.k1.gastracker.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val DAYS_PER_WEEK = 7.0
const val DAYS_PER_MONTH = 28.0
const val DAYS_PER_YEAR = 365.25

data class WindowResult(
    val windowDays: Int,
    val nRefills: Int,
    val start: LocalDate?,
    val totalDistanceKm: Double?,
    val totalVolumeL: Double,
    val totalCost: Double?,
    val distancePerDay: Double?,
    val costPerDay: Double?,
)

data class RatioMetrics(
    val kmPerL: Double?,
    val lPer100Km: Double?,
    val mpg: Double?,
    val costPerKm: Double?,
    val avgPricePerLiter: Double?,
)

data class YearlyView(
    val periodDays: Int,
    val nRefills: Int,
    val actualCost: Double?,
    val actualDistanceKm: Double?,
    val extrapolatedCost: Double?,
    val extrapolatedDistanceKm: Double?,
)

private fun spanDays(start: LocalDate, end: LocalDate): Int =
    maxOf(ChronoUnit.DAYS.between(start, end).toInt() + 1, 1)

fun recentWindow(
    samples: List<Sample>,
    today: LocalDate,
    primaryDays: Int = 28,
    expandedDays: Int = 90,
    minRefills: Int = 2,
): WindowResult {
    val ordered = samples.sortedBy { it.date }
    if (ordered.isEmpty()) {
        return WindowResult(
            windowDays = primaryDays, nRefills = 0, start = null,
            totalDistanceKm = null, totalVolumeL = 0.0, totalCost = null,
            distancePerDay = null, costPerDay = null,
        )
    }

    fun window(days: Int): List<Sample> {
        val cutoff = today.minusDays((days - 1).toLong())
        return ordered.filter { it.date >= cutoff && it.date <= today }
    }

    var chosen = window(primaryDays)
    if (chosen.size < minRefills) {
        val expanded = window(expandedDays)
        if (expanded.size >= minRefills || chosen.isEmpty()) {
            chosen = expanded
        }
    }

    val windowDays = spanDays(chosen.minOf { it.date }, today)
    val distances = chosen.mapNotNull { it.distanceKm }
    val costs = chosen.mapNotNull { it.cost }
    val totalDistance = if (distances.isEmpty()) null else distances.sum()
    val totalCost = if (costs.isEmpty()) null else costs.sum()

    return WindowResult(
        windowDays = windowDays,
        nRefills = chosen.size,
        start = chosen.minOf { it.date },
        totalDistanceKm = totalDistance,
        totalVolumeL = chosen.sumOf { it.volumeL },
        totalCost = totalCost,
        distancePerDay = totalDistance?.div(windowDays),
        costPerDay = totalCost?.div(windowDays),
    )
}

fun flowValue(perDay: Double?, periodDays: Double): Double? =
    perDay?.times(periodDays)

fun windowRatios(samples: List<Sample>): RatioMetrics {
    val distances = samples.mapNotNull { it.distanceKm }
    val costs = samples.mapNotNull { it.cost }
    val totalDistance = if (distances.isEmpty()) null else distances.sum()
    val totalVolume = samples.sumOf { it.volumeL }
    val totalCost = if (costs.isEmpty()) null else costs.sum()

    if (totalVolume == 0.0) {
        return RatioMetrics(null, null, null, null, null)
    }

    val kmPerL = totalDistance?.div(totalVolume)
    val lPer100 = totalDistance?.let { totalVolume / it * 100 }
    val mpg = totalDistance?.let { kmToMiles(it) / litersToGallons(totalVolume) }
    val costPerKm = if (totalCost != null && totalDistance != null) totalCost / totalDistance else null
    val avgPrice = totalCost?.div(totalVolume)

    return RatioMetrics(kmPerL, lPer100, mpg, costPerKm, avgPrice)
}

fun yearlyView(
    samples: List<Sample>,
    today: LocalDate,
    yearDays: Int = 365,
): YearlyView {
    val ordered = samples.sortedBy { it.date }
    val cutoff = today.minusDays((yearDays - 1).toLong())
    val inYear = ordered.filter { it.date >= cutoff && it.date <= today }

    if (inYear.isEmpty()) {
        return YearlyView(
            periodDays = yearDays, nRefills = 0,
            actualCost = null, actualDistanceKm = null,
            extrapolatedCost = null, extrapolatedDistanceKm = null,
        )
    }

    val distances = inYear.mapNotNull { it.distanceKm }
    val costs = inYear.mapNotNull { it.cost }
    val totalDistance = if (distances.isEmpty()) null else distances.sum()
    val totalCost = if (costs.isEmpty()) null else costs.sum()
    val coverageDays = spanDays(inYear.minOf { it.date }, today)

    return YearlyView(
        periodDays = yearDays,
        nRefills = inYear.size,
        actualCost = totalCost,
        actualDistanceKm = totalDistance,
        extrapolatedCost = flowValue(totalCost?.div(coverageDays), yearDays.toDouble()),
        extrapolatedDistanceKm = flowValue(totalDistance?.div(coverageDays), yearDays.toDouble()),
    )
}
