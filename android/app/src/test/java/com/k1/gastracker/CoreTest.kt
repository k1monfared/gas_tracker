package com.k1.gastracker

import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.Sample
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.core.averageCostPerPeriod
import com.k1.gastracker.core.buildDataset
import com.k1.gastracker.core.convertAmount
import com.k1.gastracker.core.efficiencySeries
import com.k1.gastracker.core.flowValue
import com.k1.gastracker.core.gallonsToLiters
import com.k1.gastracker.core.kmToMiles
import com.k1.gastracker.core.litersToGallons
import com.k1.gastracker.core.mergeSameDay
import com.k1.gastracker.core.milesToKm
import com.k1.gastracker.core.nearestRateAt
import com.k1.gastracker.core.periodSeries
import com.k1.gastracker.core.recentWindow
import com.k1.gastracker.core.summarize
import com.k1.gastracker.core.toKm
import com.k1.gastracker.core.toLiters
import com.k1.gastracker.core.windowRatios
import com.k1.gastracker.core.yearlyView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun d(s: String): LocalDate = LocalDate.parse(s)

private fun r(date: String, volume: Double, distance: Double? = null, cost: Double? = null) =
    Refill(date = d(date), volume = volume, distance = distance, cost = cost)

class UnitsTest {
    @Test
    fun mileConversion() {
        assertEquals(1.609344, milesToKm(1.0), 1e-12)
        assertEquals(1.0, kmToMiles(1.609344), 1e-12)
    }

    @Test
    fun gallonConversion() {
        assertEquals(3.785411784, gallonsToLiters(1.0), 1e-12)
        assertEquals(1.0, litersToGallons(3.785411784), 1e-12)
    }

    @Test
    fun enumDispatch() {
        assertEquals(1.609344, toKm(1.0, DistanceUnit.MILE), 1e-12)
        assertEquals(5.0, toKm(5.0, DistanceUnit.KM), 1e-12)
        assertEquals(3.785411784, toLiters(1.0, VolumeUnit.GALLON), 1e-12)
    }
}

class ProcessingTest {
    @Test
    fun emptyDataset() {
        val data = buildDataset(emptyList())
        assertTrue(data.samples.isEmpty())
        assertNull(data.currency)
    }

    @Test
    fun unsortedInputIsSorted() {
        val data = buildDataset(listOf(r("2026-03-01", 40.0, 500.0, 80.0), r("2026-01-01", 40.0, 500.0, 80.0)))
        assertEquals(listOf(d("2026-01-01"), d("2026-03-01")), data.samples.map { it.date })
    }

    @Test
    fun unitNormalization() {
        val data = buildDataset(
            listOf(
                Refill(
                    date = d("2026-01-01"),
                    volume = 1.0,
                    distance = 100.0,
                    distanceUnit = DistanceUnit.MILE,
                    volumeUnit = VolumeUnit.GALLON,
                )
            )
        )
        val s = data.samples[0]
        assertEquals(3.785411784, s.volumeL, 1e-9)
        assertEquals(160.9344, s.distanceKm!!, 1e-9)
    }

    @Test
    fun sameDayDoubleRefillIsMerged() {
        val merged = mergeSameDay(
            listOf(
                Sample(d("2026-05-01"), 30.0, 200.0, 60.0),
                Sample(d("2026-05-01"), 10.0, 0.0, 20.0),
            )
        )
        assertEquals(1, merged.size)
        assertEquals(40.0, merged[0].volumeL, 1e-12)
        assertEquals(200.0, merged[0].distanceKm!!, 1e-12)
        assertEquals(80.0, merged[0].cost!!, 1e-12)
    }

    @Test
    fun interiorMissingDistanceIsInterpolated() {
        val data = buildDataset(
            listOf(
                r("2026-01-01", 10.0, 100.0),
                r("2026-01-10", 10.0),
                r("2026-01-20", 10.0),
                r("2026-01-30", 10.0, 60.0),
            )
        )
        val distances = data.samples.map { it.distanceKm }
        assertEquals(100.0, distances[0]!!, 1e-9)
        assertEquals(20.0, distances[1]!!, 1e-9)
        assertEquals(20.0, distances[2]!!, 1e-9)
        assertEquals(20.0, distances[3]!!, 1e-9)
        assertEquals(160.0, distances.filterNotNull().sum(), 1e-9)
    }

    @Test
    fun leadingMissingDistanceStaysNone() {
        val data = buildDataset(
            listOf(
                r("2026-01-01", 10.0),
                r("2026-01-10", 10.0, 100.0),
            )
        )
        assertNull(data.samples[0].distanceKm)
        assertEquals(100.0, data.samples[1].distanceKm!!, 1e-12)
    }

    @Test
    fun missingCostInterpolatedFromPricePerLiter() {
        val data = buildDataset(
            listOf(
                r("2026-01-01", 10.0, 100.0, 100.0),
                r("2026-01-10", 10.0, 100.0),
                r("2026-01-20", 10.0, 100.0, 130.0),
            )
        )
        assertEquals(115.0, data.samples[1].cost!!, 1e-9)
    }

    @Test
    fun edgeMissingCostUsesNearestKnownPrice() {
        val data = buildDataset(
            listOf(
                r("2026-01-01", 10.0, 100.0),
                r("2026-01-10", 10.0, 100.0, 120.0),
            )
        )
        assertEquals(120.0, data.samples[0].cost!!, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mixedCurrenciesThrow() {
        buildDataset(
            listOf(
                r("2026-01-01", 10.0, 100.0, 100.0),
                Refill(date = d("2026-01-10"), volume = 10.0, distance = 100.0, cost = 100.0, currency = "EUR"),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRefillRejected() {
        Refill(date = d("2026-01-01"), volume = 0.0)
    }
}

class MetricsTest {
    private val steady = buildDataset(
        listOf(
            r("2026-06-15", 40.0, 500.0, 80.0),
            r("2026-07-15", 40.0, 500.0, 80.0),
            r("2026-08-15", 40.0, 500.0, 80.0),
        )
    )

    @Test
    fun summarySteadyConsumption() {
        val s = summarize(steady)
        assertEquals(3, s.nRefills)
        assertEquals(61, s.nDays)
        assertEquals(120.0, s.totalVolumeL, 1e-9)
        assertEquals(1500.0, s.totalDistanceKm!!, 1e-9)
        assertEquals(240.0, s.totalCost!!, 1e-9)
        assertEquals(8.0, s.lPer100Km!!, 1e-9)
        assertEquals(12.5, s.kmPerL!!, 1e-9)
        assertEquals(kmToMiles(1500.0) / litersToGallons(120.0), s.mpg!!, 1e-9)
        assertEquals(0.16, s.costPerKm!!, 1e-9)
        assertEquals(2.0, s.avgPricePerLiter!!, 1e-9)
        assertEquals(240.0 / 61 * 7, s.costPerWeek!!, 1e-9)
        assertEquals(30.5, s.meanDaysBetweenRefills!!, 1e-9)
    }

    @Test
    fun averageCostPerPeriodValues() {
        assertEquals(240.0 / (61.0 / 7.0), averageCostPerPeriod(steady, com.k1.gastracker.core.PeriodKind.WEEK)!!, 1e-9)
        assertEquals(240.0 / (61.0 / 30.4375), averageCostPerPeriod(steady, com.k1.gastracker.core.PeriodKind.MONTH)!!, 1e-9)
        assertEquals(240.0 / (61.0 / 365.25), averageCostPerPeriod(steady, com.k1.gastracker.core.PeriodKind.YEAR)!!, 1e-9)
    }

    @Test
    fun periodSeriesMonthly() {
        val points = periodSeries(steady, com.k1.gastracker.core.PeriodKind.MONTH)
        assertEquals(listOf("2026-06", "2026-07", "2026-08"), points.map { it.key })
        assertTrue(points.all { it.volumeL == 40.0 })
        assertTrue(points.all { kotlin.math.abs(it.lPer100Km!! - 8.0) < 1e-9 })
        assertEquals(d("2026-06-01"), points[0].start)
    }

    @Test
    fun periodSeriesIsoWeek() {
        val data = buildDataset(
            listOf(
                r("2026-01-05", 40.0, 500.0, 80.0),
                r("2026-01-07", 40.0, 500.0, 80.0),
            )
        )
        val points = periodSeries(data, com.k1.gastracker.core.PeriodKind.WEEK)
        assertEquals(1, points.size)
        assertEquals("2026-W02", points[0].key)
    }

    @Test
    fun efficiencySeriesSkipsUnknownDistance() {
        val data = buildDataset(
            listOf(
                r("2026-01-01", 40.0, null, 80.0),
                r("2026-02-01", 40.0, 500.0, 80.0),
            )
        )
        val points = efficiencySeries(data)
        assertEquals(1, points.size)
        assertEquals(8.0, points[0].lPer100Km, 1e-9)
        assertEquals(kmToMiles(500.0) / litersToGallons(40.0), points[0].mpg, 1e-9)
        assertEquals(0.16, points[0].costPerKm!!, 1e-9)
    }

    @Test
    fun singleSampleSpanGuard() {
        val data = buildDataset(listOf(r("2026-01-01", 40.0, 500.0, 80.0)))
        val s = summarize(data)
        assertEquals(1, s.nDays)
        assertEquals(80.0, s.costPerDay!!, 1e-9)
        assertNull(s.meanDaysBetweenRefills)
    }

    @Test
    fun emptyDatasetSummary() {
        val s = summarize(buildDataset(emptyList()))
        assertEquals(0, s.nRefills)
        assertNull(s.totalCost)
        assertNull(s.mpg)
        assertNull(averageCostPerPeriod(buildDataset(emptyList()), com.k1.gastracker.core.PeriodKind.MONTH))
    }
}

class WindowingTest {
    private fun sd(date: String, volume: Double, distance: Double? = null, cost: Double? = null) =
        Sample(LocalDate.parse(date), volume, distance, cost)

    @Test
    fun emptyWindow() {
        val result = recentWindow(emptyList(), LocalDate.parse("2026-08-21"))
        assertEquals(28, result.windowDays)
        assertEquals(0, result.nRefills)
        assertNull(result.start)
    }

    @Test
    fun primaryWindowWhenEnoughData() {
        val today = LocalDate.parse("2026-08-21")
        val samples = listOf(
            sd("2026-08-21", 40.0, 500.0, 80.0),
            sd("2026-08-14", 40.0, 500.0, 80.0),
            sd("2026-06-01", 40.0, 500.0, 80.0),
        )
        val result = recentWindow(samples, today, primaryDays = 28, expandedDays = 90, minRefills = 2)
        assertEquals(2, result.nRefills)
        assertEquals(8, result.windowDays)
        assertEquals(160.0, result.totalCost!!, 1e-9)
        assertEquals(1000.0, result.totalDistanceKm!!, 1e-9)
    }

    @Test
    fun expandsToThreeMonthsWhenSparse() {
        val today = LocalDate.parse("2026-08-21")
        val samples = listOf(
            sd("2026-08-21", 40.0, 500.0, 80.0),
            sd("2026-06-01", 40.0, 500.0, 80.0),
        )
        val result = recentWindow(samples, today, primaryDays = 28, expandedDays = 90, minRefills = 2)
        assertEquals(2, result.nRefills)
        assertEquals(ChronoUnit.DAYS.between(LocalDate.parse("2026-06-01"), today).toInt() + 1, result.windowDays)
    }

    @Test
    fun flowValueScaling() {
        assertEquals(70.0, flowValue(10.0, 7.0)!!, 1e-9)
        assertEquals(280.0, flowValue(10.0, 28.0)!!, 1e-9)
        assertNull(flowValue(null, 28.0))
    }

    @Test
    fun windowRatios() {
        val ratios = windowRatios(listOf(sd("2026-08-21", 40.0, 500.0, 80.0)))
        assertEquals(8.0, ratios.lPer100Km!!, 1e-9)
        assertEquals(0.16, ratios.costPerKm!!, 1e-9)
        assertEquals(2.0, ratios.avgPricePerLiter!!, 1e-9)
    }

    @Test
    fun yearlyViewExtrapolates() {
        val today = LocalDate.parse("2026-08-21")
        val samples = listOf(
            sd("2026-08-21", 40.0, 500.0, 80.0),
            sd("2026-07-21", 40.0, 500.0, 80.0),
        )
        val view = yearlyView(samples, today)
        assertEquals(2, view.nRefills)
        assertEquals(160.0, view.actualCost!!, 1e-9)
        assertEquals(1000.0, view.actualDistanceKm!!, 1e-9)
        val coverage = ChronoUnit.DAYS.between(LocalDate.parse("2026-07-21"), today).toInt() + 1
        assertEquals(160.0 / coverage * view.periodDays, view.extrapolatedCost!!, 1e-9)
    }

    @Test
    fun octaneAndStationRoundtrip() {
        val r = Refill(LocalDate.parse("2026-08-21"), 40.0, octane = 95, station = "Shell")
        assertEquals(95, r.octane)
        assertEquals("Shell", r.station)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeOctaneRejected() {
        Refill(LocalDate.parse("2026-08-21"), 40.0, octane = -1)
    }
}

class FxTest {
    @Test
    fun sameCurrencyIsOne() {
        assertEquals(1.0, nearestRateAt(emptyMap(), LocalDate.parse("2026-08-21"), "EUR", "EUR")!!, 1e-12)
    }

    @Test
    fun exactAndLookback() {
        val rates = mapOf(
            Triple(LocalDate.parse("2026-08-18"), "USD", "CAD") to 1.34,
            Triple(LocalDate.parse("2026-08-21"), "USD", "CAD") to 1.35,
        )
        assertEquals(1.35, nearestRateAt(rates, LocalDate.parse("2026-08-22"), "USD", "CAD")!!, 1e-12)
        assertEquals(1.34, nearestRateAt(rates, LocalDate.parse("2026-08-20"), "USD", "CAD")!!, 1e-12)
    }

    @Test
    fun missingRateReturnsNull() {
        assertNull(nearestRateAt(emptyMap(), LocalDate.parse("2026-08-21"), "USD", "CAD"))
    }

    @Test
    fun convertAmount() {
        assertEquals(135.0, convertAmount(100.0, 1.35)!!, 1e-12)
        assertNull(convertAmount(100.0, null))
    }
}
