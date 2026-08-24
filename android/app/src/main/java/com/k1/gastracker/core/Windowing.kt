package com.k1.gastracker.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val DAYS_PER_WEEK = 7.0
const val DAYS_PER_MONTH = 28.0
const val DAYS_PER_YEAR = 365.25
const val MIN_RATE_REFILLS = 2
const val MIN_RATE_COVERAGE_DAYS = 7
const val MIN_YEARLY_EXTRAPOLATION_DAYS = 28

data class WindowResult(
    val windowDays: Int,
    val nRefills: Int,
    val start: LocalDate?,
    val totalDistanceKm: Double?,
    val totalVolumeL: Double,
    val totalCost: Double?,
    val distancePerDay: Double?,
    val costPerDay: Double?,
    val coverageDays: Int = 0,
    val canExtrapolate: Boolean = false,
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
            coverageDays = 0, canExtrapolate = false,
        )
    }

    fun window(days: Int): List<Sample> {
        val cutoff = today.minusDays((days - 1).toLong())
        return ordered.filter { it.date >= cutoff && it.date <= today }
    }

    var chosen = window(primaryDays)
    var usedDays = primaryDays
    if (chosen.size < minRefills) {
        val expanded = window(expandedDays)
        if (expanded.size >= minRefills || chosen.isEmpty()) {
            chosen = expanded
            usedDays = expandedDays
        }
    }

    if (chosen.isEmpty()) {
        return WindowResult(
            windowDays = usedDays, nRefills = 0, start = null,
            totalDistanceKm = null, totalVolumeL = 0.0, totalCost = null,
            distancePerDay = null, costPerDay = null,
            coverageDays = 0, canExtrapolate = false,
        )
    }

    val coverageDays = spanDays(chosen.minOf { it.date }, today)
    val canExtrapolate = chosen.size >= MIN_RATE_REFILLS && coverageDays >= MIN_RATE_COVERAGE_DAYS
    val distances = chosen.mapNotNull { it.distanceKm }
    val costs = chosen.mapNotNull { it.cost }
    val totalDistance = if (distances.isEmpty()) null else distances.sum()
    val totalCost = if (costs.isEmpty()) null else costs.sum()

    return WindowResult(
        windowDays = coverageDays,
        nRefills = chosen.size,
        start = chosen.minOf { it.date },
        totalDistanceKm = totalDistance,
        totalVolumeL = chosen.sumOf { it.volumeL },
        totalCost = totalCost,
        distancePerDay = if (totalDistance != null && canExtrapolate) totalDistance / coverageDays else null,
        costPerDay = if (totalCost != null && canExtrapolate) totalCost / coverageDays else null,
        coverageDays = coverageDays,
        canExtrapolate = canExtrapolate,
    )
}

fun flowValue(perDay: Double?, periodDays: Double): Double? =
    perDay?.times(periodDays)

fun windowRatios(samples: List<Sample>): RatioMetrics {
    val totalVolume = samples.sumOf { it.volumeL }
    if (totalVolume == 0.0) {
        return RatioMetrics(null, null, null, null, null)
    }

    val paired = pairedDistanceVolume(samples)
    val kmPerL = paired?.let { it.first / it.second }
    val lPer100 = paired?.let { it.second / it.first * 100 }
    val mpg = paired?.let { kmToMiles(it.first) / litersToGallons(it.second) }
    val costPair = pairedCostDistance(samples)
    val costPerKm = costPair?.let { it.first / it.second }
    val priced = samples.filter { it.cost != null }
    val avgPrice = if (priced.isEmpty()) null else priced.sumOf { it.cost!! } / priced.sumOf { it.volumeL }

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
    val canExtrapolate = inYear.size >= MIN_RATE_REFILLS && coverageDays >= MIN_YEARLY_EXTRAPOLATION_DAYS

    return YearlyView(
        periodDays = yearDays,
        nRefills = inYear.size,
        actualCost = totalCost,
        actualDistanceKm = totalDistance,
        extrapolatedCost = if (canExtrapolate) flowValue(totalCost?.div(coverageDays), yearDays.toDouble()) else null,
        extrapolatedDistanceKm = if (canExtrapolate) {
            flowValue(totalDistance?.div(coverageDays), yearDays.toDouble())
        } else {
            null
        },
    )
}
