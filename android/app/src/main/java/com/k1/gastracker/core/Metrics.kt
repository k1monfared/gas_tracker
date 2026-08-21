package com.k1.gastracker.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

enum class PeriodKind { WEEK, MONTH, YEAR }

data class EfficiencyPoint(
    val date: LocalDate,
    val lPer100Km: Double,
    val mpg: Double,
    val kmPerL: Double,
    val costPerKm: Double? = null,
)

data class PeriodPoint(
    val key: String,
    val start: LocalDate,
    val distanceKm: Double?,
    val volumeL: Double,
    val cost: Double?,
    val lPer100Km: Double?,
    val mpg: Double?,
)

data class Summary(
    val nRefills: Int,
    val nDays: Int,
    val totalDistanceKm: Double?,
    val totalVolumeL: Double,
    val totalCost: Double?,
    val kmPerL: Double?,
    val lPer100Km: Double?,
    val mpg: Double?,
    val costPerKm: Double?,
    val avgPricePerLiter: Double?,
    val distancePerDay: Double?,
    val costPerDay: Double?,
    val costPerWeek: Double?,
    val costPerMonth: Double?,
    val costPerYear: Double?,
    val meanDaysBetweenRefills: Double?,
)

private const val AVG_DAYS_PER_WEEK = 7.0
private const val AVG_DAYS_PER_MONTH = 30.4375
private const val AVG_DAYS_PER_YEAR = 365.25

private fun spanDays(samples: List<Sample>): Int {
    if (samples.size < 2) return 1
    val days = ChronoUnit.DAYS.between(samples.first().date, samples.last().date)
    return maxOf(days, 1L).toInt()
}

fun summarize(data: Dataset): Summary {
    val samples = data.samples
    val totalVolume = samples.sumOf { it.volumeL }
    val distances = samples.mapNotNull { it.distanceKm }
    val costs = samples.mapNotNull { it.cost }
    val totalDistance = if (distances.isEmpty()) null else distances.sum()
    val totalCost = if (costs.isEmpty()) null else costs.sum()
    val nDays = spanDays(samples)

    val kmPerL = totalDistance?.div(totalVolume)
    val lPer100 = totalDistance?.let { totalVolume / it * 100 }
    val mpg = totalDistance?.let { kmToMiles(it) / litersToGallons(totalVolume) }
    val costPerKm = if (totalCost != null && totalDistance != null) totalCost / totalDistance else null
    val avgPrice = totalCost?.div(totalVolume)

    val gaps = samples.zipWithNext().mapNotNull { (a, b) ->
        if (b.date > a.date) ChronoUnit.DAYS.between(a.date, b.date) else null
    }
    val meanGap = if (gaps.isEmpty()) null else gaps.sum().toDouble() / gaps.size

    fun perDay(amount: Double?): Double? = amount?.div(nDays)

    return Summary(
        nRefills = samples.size,
        nDays = nDays,
        totalDistanceKm = totalDistance,
        totalVolumeL = totalVolume,
        totalCost = totalCost,
        kmPerL = kmPerL,
        lPer100Km = lPer100,
        mpg = mpg,
        costPerKm = costPerKm,
        avgPricePerLiter = avgPrice,
        distancePerDay = perDay(totalDistance),
        costPerDay = perDay(totalCost),
        costPerWeek = perDay(totalCost)?.times(AVG_DAYS_PER_WEEK),
        costPerMonth = perDay(totalCost)?.times(AVG_DAYS_PER_MONTH),
        costPerYear = perDay(totalCost)?.times(AVG_DAYS_PER_YEAR),
        meanDaysBetweenRefills = meanGap,
    )
}

fun efficiencySeries(data: Dataset): List<EfficiencyPoint> = data.samples.mapNotNull { s ->
    val d = s.distanceKm
    if (d == null || d == 0.0) {
        null
    } else {
        EfficiencyPoint(
            date = s.date,
            lPer100Km = s.volumeL / d * 100,
            mpg = kmToMiles(d) / litersToGallons(s.volumeL),
            kmPerL = d / s.volumeL,
            costPerKm = s.cost?.div(d),
        )
    }
}

private fun periodStartAndKey(date: LocalDate, kind: PeriodKind): Pair<LocalDate, String> = when (kind) {
    PeriodKind.WEEK -> {
        val start = date.with(java.time.DayOfWeek.MONDAY)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        start to "%04d-W%02d".format(year, week)
    }
    PeriodKind.MONTH -> date.withDayOfMonth(1) to "%04d-%02d".format(date.year, date.monthValue)
    PeriodKind.YEAR -> date.withDayOfYear(1) to "%04d".format(date.year)
}

fun periodSeries(data: Dataset, kind: PeriodKind): List<PeriodPoint> {
    val buckets = LinkedHashMap<String, MutableList<Sample>>()
    val starts = HashMap<String, LocalDate>()
    for (s in data.samples) {
        val (start, key) = periodStartAndKey(s.date, kind)
        buckets.getOrPut(key) { mutableListOf() }.add(s)
        starts[key] = minOf(starts[key] ?: start, start)
    }
    return buckets.keys.sorted().map { key ->
        val group = buckets.getValue(key)
        val anyDistance = group.any { it.distanceKm != null }
        val distance = group.mapNotNull { it.distanceKm }.sum()
        val costs = group.mapNotNull { it.cost }
        val cost = if (costs.isEmpty()) null else costs.sum()
        val volume = group.sumOf { it.volumeL }
        val positive = anyDistance && distance > 0
        PeriodPoint(
            key = key,
            start = starts.getValue(key),
            distanceKm = if (anyDistance) distance else null,
            volumeL = volume,
            cost = cost,
            lPer100Km = if (positive) volume / distance * 100 else null,
            mpg = if (positive) kmToMiles(distance) / litersToGallons(volume) else null,
        )
    }
}

fun averageCostPerPeriod(data: Dataset, kind: PeriodKind): Double? {
    val summary = summarize(data)
    val total = summary.totalCost ?: return null
    val divisor = when (kind) {
        PeriodKind.WEEK -> AVG_DAYS_PER_WEEK
        PeriodKind.MONTH -> AVG_DAYS_PER_MONTH
        PeriodKind.YEAR -> AVG_DAYS_PER_YEAR
    }
    return total / (summary.nDays / divisor)
}
