package com.k1.gastracker.core

import java.time.LocalDate

data class Sample(
    val date: LocalDate,
    val volumeL: Double,
    val distanceKm: Double? = null,
    val cost: Double? = null,
    val currency: String = "USD",
    val odometerKm: Double? = null,
    val interpolateCost: Boolean = true,
)

data class Dataset(val samples: List<Sample>) {
    val currency: String?
        get() = samples.firstOrNull()?.currency
}

fun pairedDistanceVolume(samples: List<Sample>): Pair<Double, Double>? {
    val paired = samples.filter { it.distanceKm != null && it.distanceKm > 0 }
    if (paired.isEmpty()) return null
    return paired.sumOf { it.distanceKm!! } to paired.sumOf { it.volumeL }
}

fun pairedCostDistance(samples: List<Sample>): Pair<Double, Double>? {
    val paired = samples.filter { it.cost != null && it.distanceKm != null && it.distanceKm > 0 }
    if (paired.isEmpty()) return null
    return paired.sumOf { it.cost!! } to paired.sumOf { it.distanceKm!! }
}

private fun canonical(refill: Refill): Sample = Sample(
    date = refill.date,
    volumeL = refill.volumeL,
    distanceKm = refill.distanceKm,
    cost = refill.cost,
    currency = refill.currency,
    odometerKm = refill.odometerKm,
    interpolateCost = refill.interpolateCost,
)

private fun distancesFromOdometer(samples: List<Sample>): List<Sample> {
    val out = ArrayList<Sample>(samples.size)
    var prev: Double? = null
    for (s in samples) {
        var distance = s.distanceKm
        val odo = s.odometerKm
        if (distance == null && odo != null && prev != null) {
            distance = odo - prev
            if (distance < 0) distance = null
        }
        out.add(s.copy(distanceKm = distance))
        if (odo != null) prev = odo
    }
    return out
}

fun mergeSameDay(samples: List<Sample>): List<Sample> = samples
    .sortedBy { it.date }
    .groupBy { it.date }
    .map { (date, group) ->
        val currencies = group.map { it.currency }.toSet()
        require(currencies.size == 1) { "mixed currencies on $date: ${currencies.sorted()}" }

        fun coalesce(field: (Sample) -> Double?): Double? =
            if (group.any { field(it) == null }) null else group.sumOf { field(it)!! }

        val odometers = group.mapNotNull { it.odometerKm }
        val mergedOdometer = if (odometers.isEmpty()) null else odometers.max()
        val mergedDistance = if (mergedOdometer != null) null else coalesce { it.distanceKm }

        Sample(
            date = date,
            volumeL = group.sumOf { it.volumeL },
            distanceKm = mergedDistance,
            cost = coalesce { it.cost },
            currency = currencies.first(),
            odometerKm = mergedOdometer,
            interpolateCost = group.all { it.interpolateCost },
        )
    }

private fun interpolateDistances(samples: List<Sample>): List<Sample> {
    val cums = ArrayList<Double?>(samples.size)
    var running = 0.0
    for (s in samples) {
        val d = s.distanceKm
        if (d == null) {
            cums.add(null)
        } else {
            running += d
            cums.add(running)
        }
    }
    val known = cums.withIndex().filter { it.value != null }.map { it.index }
    if (known.size >= 2) {
        for ((a, b) in known.zipWithNext()) {
            val ca = cums[a]!!
            val cb = cums[b]!!
            for (i in a + 1 until b) {
                val t = (i - a).toDouble() / (b - a)
                cums[i] = ca + (cb - ca) * t
            }
        }
    }
    var prev = 0.0
    return samples.mapIndexed { i, s ->
        val c = cums[i]
        if (c == null) {
            s
        } else {
            val out = s.copy(distanceKm = c - prev)
            prev = c
            out
        }
    }
}

private fun interpolateCosts(samples: List<Sample>): List<Sample> {
    val ppls = samples.map { s -> s.cost?.div(s.volumeL) }
    val known = ppls.withIndex().filter { it.value != null }.map { it.index }
    return samples.mapIndexed { i, s ->
        if (s.cost != null || !s.interpolateCost) {
            s
        } else {
            val left = known.lastOrNull { it < i }
            val right = known.firstOrNull { it > i }
            val ppl: Double = when {
                left != null && right != null -> {
                    val t = (i - left).toDouble() / (right - left)
                    ppls[left]!! + (ppls[right]!! - ppls[left]!!) * t
                }
                left != null -> ppls[left]!!
                right != null -> ppls[right]!!
                else -> return@mapIndexed s
            }
            s.copy(cost = ppl * s.volumeL)
        }
    }
}

private fun odometerAtDateKm(date: LocalDate, refills: List<Refill>): Double? {
    val prior = refills.sortedBy { it.date }.filter { !it.date.isAfter(date) }
    if (prior.isEmpty()) return null
    return if (prior.last().odometer != null) {
        prior.last().odometerKm!!
    } else {
        val anchorIndex = prior.indexOfLast { it.odometer != null }
        if (anchorIndex == -1) return null
        var sum = prior[anchorIndex].odometerKm!!
        for (i in anchorIndex + 1 until prior.size) {
            val d = prior[i].distanceKm ?: return null
            sum += d
        }
        sum
    }
}

fun inferDistanceFromOdometer(
    currentOdometer: Double,
    currentDate: LocalDate,
    currentUnit: DistanceUnit,
    refills: List<Refill>,
): Double? {
    val prevKm = odometerAtDateKm(currentDate, refills) ?: return null
    val distanceKm = toKm(currentOdometer, currentUnit) - prevKm
    return if (distanceKm >= 0) distanceKm else null
}

fun previousOdometerForDate(
    date: LocalDate,
    unit: DistanceUnit,
    refills: List<Refill>,
): Double? {
    val prevKm = odometerAtDateKm(date, refills) ?: return null
    return fromKm(prevKm, unit)
}

fun pastAverageEfficiency(refills: List<Refill>): Double? {
    val data = buildDataset(refills).samples
        .filter { it.distanceKm != null && it.distanceKm > 0 }
    if (data.isEmpty()) return null
    val efficiencies = data.map { it.volumeL / it.distanceKm!! * 100.0 }
    return efficiencies.average()
}

fun buildDataset(refills: List<Refill>): Dataset {
    if (refills.isEmpty()) return Dataset(emptyList())
    val currencies = refills.map { it.currency }.toSet()
    require(currencies.size == 1) { "mixed currencies in dataset: ${currencies.sorted()}" }
    val ordered = refills.map(::canonical).sortedBy { it.date }
    val merged = mergeSameDay(ordered)
    val computed = distancesFromOdometer(merged)
    return Dataset(interpolateCosts(interpolateDistances(computed)))
}

fun applyConvertedCost(refill: Refill, converted: ConvertedCost?, targetCurrency: String): Refill {
    if (refill.currency == targetCurrency) {
        return refill.copy(currency = targetCurrency)
    }
    return when (converted) {
        is ConvertedCost.Ready -> refill.copy(
            cost = converted.amount,
            currency = targetCurrency,
            interpolateCost = true,
        )
        ConvertedCost.Missing -> refill.copy(
            cost = null,
            currency = targetCurrency,
            interpolateCost = true,
        )
        ConvertedCost.Unavailable, null -> refill.copy(
            cost = null,
            currency = targetCurrency,
            interpolateCost = false,
        )
    }
}
