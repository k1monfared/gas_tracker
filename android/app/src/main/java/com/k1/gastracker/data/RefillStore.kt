package com.k1.gastracker.data

import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

const val REFILL_SCHEMA_VERSION = 1

sealed class RefillLoadResult {
    data class Loaded(
        val refills: List<Refill>,
        val recoveredFromBackup: Boolean = false,
    ) : RefillLoadResult()

    data object Missing : RefillLoadResult()

    data class Failed(
        val message: String,
        val quarantinedPath: String? = null,
    ) : RefillLoadResult()
}

class RefillStore(private val filesDir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(filesDir, "refills.json")
    private val tmp = File(filesDir, "refills.json.tmp")
    private val backup = File(filesDir, "refills.json.bak")

    @Volatile
    var lastLoad: RefillLoadResult = RefillLoadResult.Missing
        private set

    fun canSave(): Boolean = lastLoad is RefillLoadResult.Loaded || lastLoad is RefillLoadResult.Missing

    fun load(): RefillLoadResult {
        if (!file.exists() && !backup.exists()) {
            lastLoad = RefillLoadResult.Missing
            return lastLoad
        }
        val primaryText = file.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
        val primary = primaryText?.let { decode(it) }
        when (primary) {
            is RefillLoadResult.Loaded -> {
                lastLoad = primary
                return primary
            }
            is RefillLoadResult.Failed, null -> {
                val backupText = backup.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
                val recovered = backupText?.let { decode(it) }
                if (recovered is RefillLoadResult.Loaded) {
                    writeAtomic(encode(recovered.refills))
                    lastLoad = recovered.copy(recoveredFromBackup = true)
                    return lastLoad
                }
                val quarantined = quarantineCopy()
                val message = if (primary is RefillLoadResult.Failed) {
                    primary.message
                } else {
                    "Could not read refill history"
                }
                lastLoad = RefillLoadResult.Failed(message, quarantined)
                return lastLoad
            }
            is RefillLoadResult.Missing -> {
                lastLoad = RefillLoadResult.Missing
                return lastLoad
            }
        }
    }

    fun save(refills: List<Refill>): Boolean {
        if (!canSave()) return false
        writeAtomic(encode(refills))
        lastLoad = RefillLoadResult.Loaded(refills)
        return true
    }

    fun exportJson(refills: List<Refill>): String = encode(refills)

    fun rawText(): String? = file.takeIf { it.exists() }?.readText()

    fun restoreFromImport(text: String): RefillLoadResult {
        val parsed = decode(text)
        if (parsed is RefillLoadResult.Loaded) {
            writeAtomic(encode(parsed.refills))
            lastLoad = parsed
        } else if (parsed is RefillLoadResult.Missing) {
            writeAtomic(encode(emptyList()))
            lastLoad = RefillLoadResult.Loaded(emptyList())
        }
        return lastLoad
    }

    private fun encode(refills: List<Refill>): String {
        val dto = RefillListDto(version = REFILL_SCHEMA_VERSION, refills = refills.map { it.toDto() })
        return json.encodeToString(dto)
    }

    private fun decode(text: String): RefillLoadResult {
        if (text.isBlank()) return RefillLoadResult.Failed("empty history file")
        return try {
            val dto = json.decodeFromString<RefillListDto>(text)
            if (dto.version > REFILL_SCHEMA_VERSION) {
                RefillLoadResult.Failed("Unsupported history version ${dto.version}")
            } else {
                RefillLoadResult.Loaded(dto.refills.map { it.toDomain() })
            }
        } catch (e: Exception) {
            RefillLoadResult.Failed(e.message ?: "Could not parse refill history")
        }
    }

    private fun writeAtomic(text: String) {
        filesDir.mkdirs()
        tmp.writeText(text)
        if (file.exists()) {
            backup.delete()
            file.copyTo(backup, overwrite = true)
        }
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun quarantineCopy(): String? {
        if (!file.exists()) return backup.takeIf { it.exists() }?.absolutePath
        val dest = File(filesDir, "refills.json.corrupt")
        file.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }
}

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
    val odometer: Double? = null,
)

@Serializable
private data class RefillListDto(
    val version: Int = REFILL_SCHEMA_VERSION,
    val refills: List<RefillDto> = emptyList(),
)

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
    odometer = odometer,
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
    odometer = odometer,
)
