package dev.xdripyou.app.data

import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class WebFollowReading(
    val timestampMs: Long,
    val mgdl: Double,
    val trend: TrendArrow = TrendArrow.NOT_COMPUTABLE,
)

/**
 * Port of xDrip's `WebFollowService`: fetches glucose values from an arbitrary JSON or plain-text
 * HTTP endpoint using a configurable JSON field key (e.g. `sgv`, `bg`, `glucose.value`).
 */
object WebFollowClient {

    fun parsePayload(
        body: String,
        jsonFieldKey: String = "sgv",
        nowMs: Long = System.currentTimeMillis(),
    ): Result<List<WebFollowReading>> = runCatching {
        val text = body.trim()
        require(text.isNotEmpty()) { "Leere WebFollow-Antwort" }

        val targetKey = jsonFieldKey.substringAfterLast('.').trim().ifEmpty { "sgv" }
        val escapedKey = Regex.escape(targetKey)
        val keyRegex = Regex(""""$escapedKey"\s*:\s*(-?\d+(?:\.\d+)?)""")
        val dateRegex = Regex(""""(?:date|timestamp|ts|dateString)"\s*:\s*(\d{10,13})""")
        val dirRegex = Regex(""""(?:direction|trend)"\s*:\s*"([^"]*)"""")

        val matches = keyRegex.findAll(text).toList()
        if (matches.isEmpty()) {
            // Try direct numeric response (e.g. "124" or "6.9")
            val direct = text.replace(',', '.').toDoubleOrNull()
                ?: error("Feld '$targetKey' in WebFollow-Antwort nicht gefunden")
            val mgdl = normalizeGlucoseUnit(direct)
                ?: error("Ungültiger Glukosewert: $direct")
            return@runCatching listOf(WebFollowReading(timestampMs = nowMs, mgdl = mgdl))
        }

        val dates = dateRegex.findAll(text).mapNotNull { it.groupValues[1].toLongOrNull() }.toList()
        val dirs = dirRegex.findAll(text).map { it.groupValues[1] }.toList()
        val results = mutableListOf<WebFollowReading>()
        matches.forEachIndexed { idx, match ->
            val rawNum = match.groupValues[1].toDoubleOrNull() ?: return@forEachIndexed
            val mgdl = normalizeGlucoseUnit(rawNum) ?: return@forEachIndexed
            val rawTs = dates.getOrNull(idx)
            val ts = when {
                rawTs == null -> nowMs - idx * 300_000L
                rawTs < 10_000_000_000L -> rawTs * 1000L
                else -> rawTs
            }
            val trend = NightscoutClient.directionToTrend(dirs.getOrNull(idx).orEmpty())
            results += WebFollowReading(timestampMs = ts, mgdl = mgdl, trend = trend)
        }
        results.sortedBy { it.timestampMs }
    }

    private fun normalizeGlucoseUnit(value: Double): Double? = when {
        value in 1.1..33.3 -> value * GlucoseUnit.MMOL_TO_MGDL
        value in 20.0..600.0 -> value
        else -> null
    }

    suspend fun fetchLatest(urlString: String, jsonFieldKey: String): Result<List<WebFollowReading>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = urlString.trim()
                require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    "WebFollow-URL muss mit http:// oder https:// beginnen"
                }
                val conn = (URL(trimmed).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json, text/plain")
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText().orEmpty()
                conn.disconnect()
                if (code !in 200..299) error("WebFollow HTTP $code")
                parsePayload(body, jsonFieldKey).getOrThrow()
            }
        }
}
