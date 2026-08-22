package com.k1.gastracker

import com.k1.gastracker.core.ConvertedCost
import com.k1.gastracker.core.Refill
import com.k1.gastracker.data.FxRepository
import com.k1.gastracker.data.RateFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class FxRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun refill(currency: String, cost: Double? = 80.0) = Refill(
        date = LocalDate.parse("2026-08-01"),
        volume = 40.0,
        distance = 500.0,
        cost = cost,
        currency = currency,
    )

    @Test
    fun sameCurrencyIsReady() = runBlocking {
        val repo = FxRepository(tmp.root.resolve("fx_cache.json"), RateFetcher { _, _, _ -> error("should not fetch") })
        val result = repo.convertedCosts(listOf(refill("EUR")), "EUR")
        assertEquals(ConvertedCost.Ready(80.0), result.costs.values.single())
        assertNull(result.error)
    }

    @Test
    fun fetchFailureIsUnavailableNotInterpolatedNull() = runBlocking {
        val repo = FxRepository(
            tmp.root.resolve("fx_cache.json"),
            RateFetcher { _, _, _ -> Result.failure(IllegalStateException("offline")) },
        )
        val result = repo.convertedCosts(listOf(refill("USD")), "EUR")
        assertEquals(ConvertedCost.Unavailable, result.costs.values.single())
        assertEquals(1, result.unavailableCount)
        assertEquals("offline", result.error)
    }

    @Test
    fun cachedRateIsUsedWhenFetchWouldFail() = runBlocking {
        val cache = tmp.root.resolve("fx_cache.json")
        cache.writeText("""{"rates":{"2026-08-01|USD|EUR":0.9}}""")
        val repo = FxRepository(cache, RateFetcher { _, _, _ -> Result.failure(IllegalStateException("offline")) })
        val result = repo.convertedCosts(listOf(refill("USD")), "EUR")
        assertEquals(ConvertedCost.Ready(72.0), result.costs.values.single())
        assertNull(result.error)
    }

    @Test
    fun originallyMissingCostStaysMissing() = runBlocking {
        val repo = FxRepository(tmp.root.resolve("fx_cache.json"), RateFetcher { _, _, _ -> Result.success(2.0) })
        val result = repo.convertedCosts(listOf(refill("USD", cost = null)), "EUR")
        assertEquals(ConvertedCost.Missing, result.costs.values.single())
        assertEquals(0, result.unavailableCount)
    }

    @Test
    fun successfulFetchConverts() = runBlocking {
        var fetched = 0
        val repo = FxRepository(
            tmp.root.resolve("fx_cache.json"),
            RateFetcher { _, from, to ->
                fetched += 1
                assertEquals("USD", from)
                assertEquals("EUR", to)
                Result.success(0.5)
            },
        )
        val first = repo.convertedCosts(listOf(refill("USD")), "EUR")
        val second = repo.convertedCosts(listOf(refill("USD")), "EUR")
        assertEquals(ConvertedCost.Ready(40.0), first.costs.values.single())
        assertEquals(ConvertedCost.Ready(40.0), second.costs.values.single())
        assertEquals(1, fetched)
        assertTrue(tmp.root.resolve("fx_cache.json").exists())
    }
}
