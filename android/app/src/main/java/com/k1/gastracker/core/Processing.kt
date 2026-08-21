package com.k1.gastracker.core

import java.time.LocalDate

data class Sample(
    val date: LocalDate,
    val volumeL: Double,
    val distanceKm: Double? = null,
    val cost: Double? = null,
    val currency: String = "USD",
)

data class Dataset(val samples: List<Sample>) {
    val currency: String?
        get() = samples.firstOrNull()?.currency
}

private fun canonical(refill: Refill): Sample = Sample(
    date = refill.date,
    volumeL = refill.volumeL,
    distanceKm = refill.distanceKm,
    cost = refill.cost,
    currency = refill.currency,
)

fun mergeSameDay(samples: List<Sample>): List<Sample> = samples
    .sortedBy { it.date }
    .groupBy { it.date }
    .map { (date, group) ->
        val currencies = group.map { it.currency }.toSet()
        require(currencies.size == 1) { "mixed currencies on $date: ${currencies.sorted()}" }

        fun coalesce(field: (Sample) -> Double?): Double? =
            if (group.any { field(it) == null }) null else group.sumOf { field(it)!! }

        Sample(
            date = date,
            volumeL = group.sumOf { it.volumeL },
            distanceKm = coalesce { it.distanceKm },
            cost = coalesce { it.cost },
            currency = currencies.first(),
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
        if (s.cost != null) {
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

fun buildDataset(refills: List<Refill>): Dataset {
    if (refills.isEmpty()) return Dataset(emptyList())
    val currencies = refills.map { it.currency }.toSet()
    require(currencies.size == 1) { "mixed currencies in dataset: ${currencies.sorted()}" }
    val merged = mergeSameDay(refills.map(::canonical))
    return Dataset(interpolateCosts(interpolateDistances(merged)))
}
