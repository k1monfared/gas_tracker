package com.k1.gastracker.data

import android.content.Context
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

@Serializable
private data class FxCache(val rates: Map<String, Double> = emptyMap())

class FxRepository(context: Context) {
    private val cacheFile = File(context.filesDir, "fx_cache.json")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun convertedCosts(
        refills: List<Refill>,
        homeCurrency: String,
    ): Map<Refill, Double?> = withContext(Dispatchers.IO) {
        if (refills.isEmpty()) return@withContext emptyMap<Refill, Double?>()
        val cache = loadCache().rates.toMutableMap()

        val needed = refills
            .filter { it.cost != null && it.currency != homeCurrency }
            .map { it.date to it.currency }
            .distinct()

        for ((date, fromCurrency) in needed) {
            val key = rateKey(date, fromCurrency, homeCurrency)
            if (!cache.containsKey(key)) {
                fetchRate(date, fromCurrency, homeCurrency)?.let { cache[key] = it }
            }
        }
        saveCache(FxCache(cache))

        val result = HashMap<Refill, Double?>()
        for (refill in refills) {
            result[refill] = when {
                refill.cost == null -> null
                refill.currency == homeCurrency -> refill.cost
                else -> {
                    val rate = nearestRateAt(
                        rates = buildRateTable(cache),
                        day = refill.date,
                        fromCurrency = refill.currency,
                        toCurrency = homeCurrency,
                    )
                    rate?.times(refill.cost)
                }
            }
        }
        result
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
        val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        tmp.writeText(json.encodeToString(cache))
        if (!tmp.renameTo(cacheFile)) {
            cacheFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun fetchRate(date: LocalDate, from: String, to: String): Double? = runCatching {
        val url = URL("https://api.frankfurter.dev/v1/$date?from=$from&to=$to")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        if (conn.responseCode != 200) return@runCatching null
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        obj.getJSONObject("rates").getDouble(to)
    }.getOrNull()
}
