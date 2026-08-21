package com.k1.gastracker.core

enum class DistanceUnit(val label: String) {
    KM("km"),
    MILE("mi"),
}

enum class VolumeUnit(val label: String) {
    LITER("L"),
    GALLON("gal"),
}

const val KM_PER_MILE = 1.609344
const val LITERS_PER_GALLON = 3.785411784

fun milesToKm(miles: Double): Double = miles * KM_PER_MILE

fun kmToMiles(km: Double): Double = km / KM_PER_MILE

fun gallonsToLiters(gallons: Double): Double = gallons * LITERS_PER_GALLON

fun litersToGallons(liters: Double): Double = liters / LITERS_PER_GALLON

fun toKm(value: Double, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.KM -> value
    DistanceUnit.MILE -> milesToKm(value)
}

fun fromKm(value: Double, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.KM -> value
    DistanceUnit.MILE -> kmToMiles(value)
}

fun toLiters(value: Double, unit: VolumeUnit): Double = when (unit) {
    VolumeUnit.LITER -> value
    VolumeUnit.GALLON -> gallonsToLiters(value)
}

fun fromLiters(value: Double, unit: VolumeUnit): Double = when (unit) {
    VolumeUnit.LITER -> value
    VolumeUnit.GALLON -> litersToGallons(value)
}
