package com.k1.gastracker.data

import android.content.Context
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Serializable
private data class RefillDto(
    val date: String,
    val volume: Double,
    val distance: Double? = null,
    val cost: Double? = null,
    val distanceUnit: String = "KM",
    val volumeUnit: String = "LITER",
    val currency: String = "USD",
    val octane: Int? = null,
    val station: String? = null,
)

@Serializable
private data class RefillListDto(val refills: List<RefillDto> = emptyList())

class RefillStore(context: Context) {
    private val file = File(context.filesDir, "refills.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<Refill> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<RefillListDto>(file.readText()).refills.map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    fun save(refills: List<Refill>) {
        val dto = RefillListDto(refills.map { it.toDto() })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(dto))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

private fun RefillDto.toDomain() = Refill(
    date = LocalDate.parse(date),
    volume = volume,
    distance = distance,
    cost = cost,
    distanceUnit = runCatching { DistanceUnit.valueOf(distanceUnit) }.getOrDefault(DistanceUnit.KM),
    volumeUnit = runCatching { VolumeUnit.valueOf(volumeUnit) }.getOrDefault(VolumeUnit.LITER),
    currency = currency,
    octane = octane,
    station = station,
)

private fun Refill.toDto() = RefillDto(
    date = date.toString(),
    volume = volume,
    distance = distance,
    cost = cost,
    distanceUnit = distanceUnit.name,
    volumeUnit = volumeUnit.name,
    currency = currency,
    octane = octane,
    station = station,
)
