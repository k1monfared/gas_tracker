package com.k1.gastracker.core

data class PhotoDraft(
    val target: OcrTarget,
    val imagePath: String,
    val rawText: String,
    val volume: Double? = null,
    val cost: Double? = null,
    val odometer: Double? = null,
    val distanceKm: Double? = null,
    val station: String? = null,
    val receiptDate: String? = null,
)
