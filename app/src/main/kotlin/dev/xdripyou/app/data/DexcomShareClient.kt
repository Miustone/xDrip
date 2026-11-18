package dev.xdripyou.app.data

import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class DexcomShareReading(
    val timestampMs: Long,
    val mgdl: Double,
    val trend: TrendArrow,
)

/**
 * Port of xDrip's `ShareFollowDownload` / `DexcomShareInterface` for Dexcom Share Follower.
 * Supports both US (`share2.dexcom.com`) and Outside-US (`shareous1.dexcom.com`) servers.
 */
object DexcomShareClient {
    private const val APPLICATION_ID = "d89443d2-327c-4a6f-89e5-496bbb0317db"
    private const val USER_AGENT = "Dexcom Share/3.0.2.11 CFNetwork/711.2.23 Darwin/14.0.0"

    fun baseUrl(usAccount: Boolean): String = if (usAccount) {
        "https://share2.dexcom.com/ShareWebServices/Services"
    } else {
        "https://shareous1.dexcom.com/ShareWebServices/Services"
    }

    fun parseShareRecords(jsonText: String): List<DexcomShareReading> {
        val arr = JSONArray(jsonText)
        val out = mutableListOf<DexcomShareReading>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val value = obj.optDouble("Value", Double.NaN)
            if (value.isNaN() || value < 20.0 || value > 600.0) continue
            val wt = obj.optString("WT", obj.optString("DT", ""))
            val ts = parseDotNetTimestamp(wt) ?: continue
            val trend = parseShareTrend(obj.opt("Trend"))
            out.add(DexcomShareReading(timestampMs = ts, mgdl = value, trend = trend))
        }
        return out
    }

    fun parseDotNetTimestamp(raw: String): Long? {
        val digits = raw.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return null
        return digits.toLongOrNull()?.takeIf { it > 1_000_000_000_000L }
    }

    fun parseShareTrend(rawTrend: Any?): TrendArrow {
        return when (rawTrend) {
            is Number -> when (rawTrend.toInt()) {
                1 -> TrendArrow.DOUBLE_UP
                2 -> TrendArrow.SINGLE_UP
                3 -> TrendArrow.FORTY_FIVE_UP
                4 -> TrendArrow.FLAT
                5 -> TrendArrow.FORTY_FIVE_DOWN
                6 -> TrendArrow.SINGLE_DOWN
                7 -> TrendArrow.DOUBLE_DOWN
                else -> TrendArrow.NOT_COMPUTABLE
            }
            is String -> TrendArrow.fromWireName(rawTrend)
            else -> TrendArrow.NOT_COMPUTABLE
        }
    }

    suspend fun fetchLatestReadings(
        username: String,
        password: String,
        usAccount: Boolean,
        count: Int = 24,
    ): Result<List<DexcomShareReading>> = withContext(Dispatchers.IO) {
        runCatching {
            val user = username.trim()
            val pass = password
            require(user.isNotBlank() && pass.isNotBlank()) { "Dexcom Share Zugangsdaten fehlen" }
            val base = baseUrl(usAccount)

            val authBody = JSONObject().apply {
                put("accountName", user)
                put("password", pass)
                put("applicationId", APPLICATION_ID)
            }.toString()
            val accountId = postJson("$base/General/AuthenticatePublisherAccount", authBody)
                .trim().trim('"')
            require(accountId.isNotBlank() && accountId != "00000000-0000-0000-0000-000000000000") {
                "Dexcom Share Account-Authentifizierung fehlgeschlagen"
            }

            val loginBody = JSONObject().apply {
                put("accountId", accountId)
                put("password", pass)
                put("applicationId", APPLICATION_ID)
            }.toString()
            val sessionId = postJson("$base/General/LoginPublisherAccountById", loginBody)
                .trim().trim('"')
            require(sessionId.isNotBlank() && sessionId != "00000000-0000-0000-0000-000000000000") {
                "Dexcom Share Session-Login fehlgeschlagen"
            }

            val maxCount = count.coerceIn(1, 288)
            val dataUrl = "$base/Publisher/ReadPublisherLatestGlucoseValues?sessionId=$sessionId&minutes=1440&maxCount=$maxCount"
            val recordsJson = postJson(dataUrl, "")
            parseShareRecords(recordsJson)
        }
    }

    private fun postJson(urlStr: String, body: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error("Dexcom Share HTTP $code: ${text.take(120)}")
            }
            text
        } finally {
            conn.disconnect()
        }
    }
}
