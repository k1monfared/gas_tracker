package com.k1.gastracker.core

import java.time.LocalDate

data class Refill(
    val date: LocalDate,
    val volume: Double,
    val distance: Double? = null,
    val cost: Double? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val volumeUnit: VolumeUnit = VolumeUnit.LITER,
    val currency: String = "USD",
    val octane: Int? = null,
    val station: String? = null,
    val odometer: Double? = null,
    val interpolateCost: Boolean = true,
) {
    init {
        require(volume.isFinite() && volume > 0) { "volume must be a finite positive number" }
        require(distance == null || (distance.isFinite() && distance >= 0)) { "distance cannot be negative or non-finite" }
        require(cost == null || (cost.isFinite() && cost >= 0)) { "cost cannot be negative or non-finite" }
        require(octane == null || octane >= 0) { "octane cannot be negative" }
        require(odometer == null || (odometer.isFinite() && odometer >= 0)) { "odometer cannot be negative or non-finite" }
    }

    val volumeL: Double
        get() = toLiters(volume, volumeUnit)

    val distanceKm: Double?
        get() = distance?.let { toKm(it, distanceUnit) }

    val odometerKm: Double?
        get() = odometer?.let { toKm(it, distanceUnit) }
}
