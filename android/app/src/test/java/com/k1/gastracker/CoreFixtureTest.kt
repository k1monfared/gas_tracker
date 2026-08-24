package com.k1.gastracker

import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.buildDataset
import com.k1.gastracker.core.summarize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CoreFixtureTest {
    @Test
    fun sharedFixturesMatchPython() {
        val text = javaClass.classLoader!!.getResource("core_cases.json")!!.readText()
        val file = Json { ignoreUnknownKeys = true }.decodeFromString<FixtureFile>(text)
        assertEquals(1, file.version)
        for (case in file.cases) {
            val refills = case.refills.map { it.toDomain() }
            val summary = summarize(buildDataset(refills))
            assertEquals(case.id, case.summary.n_refills, summary.nRefills)
            assertEquals(case.id, case.summary.total_volume_l, summary.totalVolumeL, 1e-9)
            case.summary.total_distance_km?.let { assertEquals(case.id, it, summary.totalDistanceKm!!, 1e-9) }
            case.summary.l_per_100_km?.let { assertEquals(case.id, it, summary.lPer100Km!!, 1e-9) }
            case.summary.total_cost?.let { assertEquals(case.id, it, summary.totalCost!!, 1e-9) }
            case.summary.n_days?.let { assertEquals(case.id, it, summary.nDays) }
        }
    }
}

@Serializable
private data class FixtureFile(val version: Int, val cases: List<FixtureCase>)

@Serializable
private data class FixtureCase(
    val id: String,
    val refills: List<FixtureRefill>,
    val summary: FixtureSummary,
)

@Serializable
private data class FixtureRefill(
    val date: String,
    val volume: Double,
    val distance: Double? = null,
    val cost: Double? = null,
    val odometer: Double? = null,
)

@Serializable
private data class FixtureSummary(
    val n_refills: Int,
    val total_volume_l: Double,
    val total_distance_km: Double? = null,
    val l_per_100_km: Double? = null,
    val total_cost: Double? = null,
    val n_days: Int? = null,
)

private fun FixtureRefill.toDomain() = Refill(
    date = LocalDate.parse(date),
    volume = volume,
    distance = distance,
    cost = cost,
    odometer = odometer,
)
