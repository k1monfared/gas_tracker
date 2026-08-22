package com.k1.gastracker.data

import com.k1.gastracker.core.ConvertedCost
import com.k1.gastracker.core.FxConversion
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.nearestRateAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

fun interface RateFetcher {
    fun fetch(date: LocalDate, from: String, to: String): Result<Double>
}

class HttpRateFetcher : RateFetcher {
    override fun fetch(date: LocalDate, from: String, to: String): Result<Double> = runCatching {
        val url = URL("https://api.frankfurter.dev/v1/$date?from=$from&to=$to")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode != 200) {
                error("FX HTTP ${conn.responseCode}")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text).getJSONObject("rates").getDouble(to)
        } finally {
            conn.disconnect()
        }
    }
}

class FxRepository(
    private val cacheFile: File,
    private val fetcher: RateFetcher = HttpRateFetcher(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun convertedCosts(
        refills: List<Refill>,
        homeCurrency: String,
    ): FxConversion = withContext(Dispatchers.IO) {
        if (refills.isEmpty()) return@withContext FxConversion(emptyMap())
        val cache = loadCache().rates.toMutableMap()
        var error: String? = null

        val needed = refills
            .filter { it.cost != null && it.currency != homeCurrency }
            .map { it.date to it.currency }
            .distinct()

        for ((date, fromCurrency) in needed) {
            val key = rateKey(date, fromCurrency, homeCurrency)
            if (!cache.containsKey(key)) {
                val fetched = fetcher.fetch(date, fromCurrency, homeCurrency)
                fetched.onSuccess { cache[key] = it }
                fetched.onFailure { e ->
                    if (error == null) error = e.message ?: "Could not fetch exchange rates"
                }
            }
        }
        saveCache(FxCache(cache))

        val table = buildRateTable(cache)
        val result = LinkedHashMap<Refill, ConvertedCost>()
        for (refill in refills) {
            result[refill] = when {
                refill.cost == null -> ConvertedCost.Missing
                refill.currency == homeCurrency -> ConvertedCost.Ready(refill.cost)
                else -> {
                    val rate = nearestRateAt(
                        rates = table,
                        day = refill.date,
                        fromCurrency = refill.currency,
                        toCurrency = homeCurrency,
                    )
                    if (rate == null) ConvertedCost.Unavailable else ConvertedCost.Ready(rate * refill.cost)
                }
            }
        }
        val unavailable = result.values.count { it is ConvertedCost.Unavailable }
        if (unavailable > 0 && error == null) {
            error = "Missing exchange rates for $unavailable refill(s)"
        }
        FxConversion(result, error, unavailable)
    }

    private fun rateKey(date: LocalDate, from: String, to: String): String =
        "$date|$from|$to"

    private fun buildRateTable(cache: Map<String, Double>): Map<Triple<LocalDate, String, String>, Double> {
        val table = HashMap<Triple<LocalDate, String, String>, Double>()
        for ((key, rate) in cache) {
            val parts = key.split("|")
            if (parts.size != 3) continue
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: continue
            table[Triple(date, parts[1], parts[2])] = rate
        }
        return table
    }

    private fun loadCache(): FxCache =
        if (cacheFile.exists()) runCatching {
            json.decodeFromString<FxCache>(cacheFile.readText())
        }.getOrDefault(FxCache()) else FxCache()

    private fun saveCache(cache: FxCache) {
        val parent = cacheFile.parentFile ?: return
        parent.mkdirs()
        val tmp = File(parent, cacheFile.name + ".tmp")
        tmp.writeText(json.encodeToString(cache))
        if (!tmp.renameTo(cacheFile)) {
            cacheFile.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

@Serializable
private data class FxCache(val rates: Map<String, Double> = emptyMap())
