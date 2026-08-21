package com.k1.gastracker.core

private val NUMBER_PATTERN = Regex("""\d+[.,]?\d*""")

fun extractNumbers(text: String): List<Double> {
    val normalized = text.replace(',', '.')
    return NUMBER_PATTERN.findAll(normalized)
        .mapNotNull { it.value.toDoubleOrNull() }
        .filter { it >= 0 }
        .distinct()
        .sorted()
        .toList()
}

fun classifyPhoto(text: String): OcrTarget {
    val upper = text.uppercase()
    val numbers = extractNumbers(text)
    val maxNumber = numbers.maxOrNull()
    val hasLargeNumber = maxNumber != null && maxNumber > 1000

    val odoKeywords = listOf("ODOMETER", "MILEAGE", "ODO", "TRIP", "KM/H", "MPH")
    val hasOdoKeyword = odoKeywords.any { it in upper }

    val hasVolumeMarker = Regex("""(?<![A-Z])L(?![A-Z])""").containsMatchIn(upper) ||
        listOf("LITER", "LITRE", "GALLON", "GLN", "FUEL").any { it in upper }

    val receiptKeywords = listOf("RECEIPT", "INVOICE", "TAX", "VAT", "GST", "STATION", "SHELL", " BP ", "ARAL", "CIRCLE K")
    val hasReceiptKeyword = receiptKeywords.any { it in upper }

    val totalMarkers = listOf("TOTAL", "AMOUNT", "PAY", "PRICE", "EUR", "USD", "GBP", "\$", "€", "£")
    val hasTotalMarker = totalMarkers.any { it in upper }

    return when {
        hasOdoKeyword && hasLargeNumber -> OcrTarget.ODOMETER
        hasVolumeMarker && (hasTotalMarker || hasReceiptKeyword) -> OcrTarget.PUMP
        hasVolumeMarker -> OcrTarget.PUMP
        hasReceiptKeyword -> OcrTarget.RECEIPT
        hasLargeNumber -> OcrTarget.ODOMETER
        else -> OcrTarget.PUMP
    }
}

fun extractVolumeAndCost(text: String, currencyCode: String): Pair<Double?, Double?> {
    val numbers = extractNumbers(text)
    if (numbers.isEmpty()) return null to null

    val upper = text.uppercase()
    val volume = findNumberNear(upper, listOf("L ", " L", "LITRE", "LITER", "GALLON", "GAL"), numbers, 1.0..200.0)
        ?: numbers.firstOrNull { it in 1.0..200.0 }

    val cost = numbers.maxOrNull()?.takeIf { it != volume }
    return volume to cost
}

fun extractOdometer(text: String): Double? {
    return extractNumbers(text).filter { it >= 100.0 }.maxOrNull()
}

private fun findNumberNear(
    text: String,
    anchors: List<String>,
    numbers: List<Double>,
    range: ClosedFloatingPointRange<Double>,
): Double? {
    val anchor = anchors.map { text.indexOf(it) }.firstOrNull { it >= 0 } ?: return null
    val window = text.substring(maxOf(0, anchor - 12), minOf(text.length, anchor + 12))
    return NUMBER_PATTERN.findAll(window.replace(',', '.'))
        .mapNotNull { it.value.toDoubleOrNull() }
        .filter { it in range }
        .firstOrNull()
}
