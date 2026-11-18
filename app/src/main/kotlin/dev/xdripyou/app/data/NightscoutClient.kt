package dev.xdripyou.app.data

import dev.xdripyou.app.domain.ExternalPumpStatus
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.roundToInt

data class NightscoutEntryDto(
    val timestampMs: Long,
    val sgvMgdl: Double,
    val direction: String,
)

/**
 * Lightweight Nightscout REST v1 client (Follower + Uploader) using standard HttpURLConnection
 * and SHA-1 hashed `api-secret` matching xDrip's Nightscout integration.
 */
object NightscoutClient {

    fun normalizeBaseUrl(rawUrl: String): String {
        var trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isNotEmpty() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }

    fun sha1Hex(secret: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(secret.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun parseEntriesJson(jsonText: String): List<NightscoutEntryDto> {
        val array = JSONArray(jsonText)
        val out = mutableListOf<NightscoutEntryDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val sgv = obj.optDouble("sgv", Double.NaN)
            if (sgv.isNaN() || sgv < 20.0 || sgv > 600.0) continue
            val dateMs = obj.optLong("date", -1L).takeIf { it > 0L }
                ?: obj.optString("dateString", "").takeIf { it.isNotBlank() }?.let {
                    runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
                }
                ?: continue
            val direction = obj.optString("direction", "Flat")
            out.add(NightscoutEntryDto(timestampMs = dateMs, sgvMgdl = sgv, direction = direction))
        }
        return out
    }

    fun parseTreatmentsJson(jsonText: String): List<TreatmentEntry> {
        val array = JSONArray(jsonText)
        val out = mutableListOf<TreatmentEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val createdStr = obj.optString("created_at", "")
            val timestampMs = obj.optLong("mills", -1L).takeIf { it > 0L }
                ?: createdStr.takeIf { it.isNotBlank() }?.let {
                    runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
                }
                ?: continue
            val insulin = obj.optDouble("insulin", 0.0).let { if (it.isNaN() || it < 0.0) 0.0 else it }
            val carbs = obj.optDouble("carbs", 0.0).let { if (it.isNaN() || it < 0.0) 0.0 else it }
            val glucoseRaw = obj.optDouble("glucose", Double.NaN)
            val units = obj.optString("units", "mg/dl").lowercase()
            val bgMgdl = if (!glucoseRaw.isNaN() && glucoseRaw > 0.0) {
                if (units.contains("mmol") || glucoseRaw < 35.0) glucoseRaw * 18.0182 else glucoseRaw
            } else {
                null
            }
            val note = obj.optString("notes", "").trim()
            val eventType = obj.optString("eventType", "Note").ifBlank { "Note" }
            if (insulin <= 0.0 && carbs <= 0.0 && bgMgdl == null && note.isBlank()) continue
            out.add(
                TreatmentEntry(
                    timestampMs = timestampMs,
                    insulinUnits = insulin,
                    carbsGrams = carbs,
                    bloodGlucoseMgdl = bgMgdl,
                    note = note,
                    eventType = eventType,
                    syncedNightscout = true,
                )
            )
        }
        return out
    }

    fun parseDeviceStatusJson(jsonText: String): ExternalPumpStatus? {
        val array = JSONArray(jsonText)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val createdStr = obj.optString("created_at", "")
            val ts = obj.optLong("mills", -1L).takeIf { it > 0L }
                ?: createdStr.takeIf { it.isNotBlank() }?.let {
                    runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
                }
                ?: System.currentTimeMillis()
            val openaps = obj.optJSONObject("openaps")
            val iobObj = openaps?.optJSONObject("iob")
            val suggestedObj = openaps?.optJSONObject("suggested") ?: openaps?.optJSONObject("enacted")
            val pumpObj = obj.optJSONObject("pump")
            val iob = iobObj?.optDouble("iob", Double.NaN)?.takeIf { !it.isNaN() }
            val cob = suggestedObj?.optDouble("COB", Double.NaN)?.takeIf { !it.isNaN() }
            val reservoir = pumpObj?.optDouble("reservoir", Double.NaN)?.takeIf { !it.isNaN() && it >= 0.0 }
            val pumpBatt = pumpObj?.optJSONObject("battery")?.optInt("percent", -1)?.takeIf { it in 0..100 }
            val uploaderBatt = obj.optInt("uploaderBattery", -1).takeIf { it in 0..100 }
            if (iob != null || cob != null || reservoir != null || pumpBatt != null || uploaderBatt != null) {
                return ExternalPumpStatus(
                    timestampMs = ts,
                    iobUnits = iob,
                    cobGrams = cob,
                    reservoirUnits = reservoir,
                    pumpBatteryPercent = pumpBatt,
                    uploaderBatteryPercent = uploaderBatt,
                )
            }
        }
        return null
    }

    fun readingToNightscoutJson(reading: GlucoseReading): JSONObject = JSONObject().apply {
        put("type", "sgv")
        put("sgv", reading.mgdl.roundToInt())
        put("date", reading.timestampMs)
        put("dateString", Instant.ofEpochMilli(reading.timestampMs).toString())
        put("direction", reading.trend.wireName)
        put("device", "xDrip2You-${reading.source}")
    }

    fun treatmentToNightscoutJson(t: TreatmentEntry): JSONObject = JSONObject().apply {
        put("eventType", t.eventType)
        put("created_at", Instant.ofEpochMilli(t.timestampMs).toString())
        if (t.insulinUnits > 0.0) put("insulin", t.insulinUnits)
        if (t.carbsGrams > 0.0) put("carbs", t.carbsGrams)
        t.bloodGlucoseMgdl?.let {
            put("glucose", it.roundToInt())
            put("glucoseType", "Finger")
        }
        if (t.note.isNotBlank()) put("notes", t.note)
        put("enteredBy", "xDrip 2 You")
    }

    suspend fun fetchLatestEntries(
        baseUrl: String,
        apiSecret: String,
        count: Int = 36,
    ): Result<List<NightscoutEntryDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeBaseUrl(baseUrl)
            require(base.isNotBlank()) { "Nightscout-URL fehlt" }
            val conn = (URL("$base/api/v1/entries/sgv.json?count=$count").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                if (apiSecret.isNotBlank()) {
                    setRequestProperty("api-secret", sha1Hex(apiSecret.trim()))
                }
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("Nightscout HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseEntriesJson(body)
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun fetchLatestTreatments(
        baseUrl: String,
        apiSecret: String,
        count: Int = 36,
    ): Result<List<TreatmentEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeBaseUrl(baseUrl)
            require(base.isNotBlank()) { "Nightscout-URL fehlt" }
            val conn = (URL("$base/api/v1/treatments.json?count=$count").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                if (apiSecret.isNotBlank()) {
                    setRequestProperty("api-secret", sha1Hex(apiSecret.trim()))
                }
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("Nightscout HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseTreatmentsJson(body)
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun fetchLatestDeviceStatus(
        baseUrl: String,
        apiSecret: String,
    ): Result<ExternalPumpStatus?> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeBaseUrl(baseUrl)
            require(base.isNotBlank()) { "Nightscout-URL fehlt" }
            val conn = (URL("$base/api/v1/devicestatus.json?count=5").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                if (apiSecret.isNotBlank()) {
                    setRequestProperty("api-secret", sha1Hex(apiSecret.trim()))
                }
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("Nightscout HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseDeviceStatusJson(body)
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun uploadDeviceStatus(
        baseUrl: String,
        apiSecret: String,
        uploaderBatteryPercent: Int?,
        iobUnits: Double?,
        cobGrams: Double?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("device", "xDrip2You")
            put("created_at", Instant.now().toString())
            if (uploaderBatteryPercent != null) {
                put("uploaderBattery", uploaderBatteryPercent)
                put("uploader", JSONObject().apply { put("battery", uploaderBatteryPercent) })
            }
            if (iobUnits != null || cobGrams != null) {
                put("openaps", JSONObject().apply {
                    if (iobUnits != null) {
                        put("iob", JSONObject().apply { put("iob", iobUnits) })
                    }
                    if (cobGrams != null) {
                        put("suggested", JSONObject().apply { put("COB", cobGrams) })
                    }
                })
            }
        }.toString()
        postJson("$baseUrl/api/v1/devicestatus", apiSecret, payload)
    }

    suspend fun uploadReadings(
        baseUrl: String,
        apiSecret: String,
        readings: List<GlucoseReading>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (readings.isEmpty()) return@withContext Result.success(Unit)
        val payload = JSONArray().apply {
            readings.forEach { put(readingToNightscoutJson(it)) }
        }.toString()
        postJson("$baseUrl/api/v1/entries", apiSecret, payload)
    }

    suspend fun uploadTreatments(
        baseUrl: String,
        apiSecret: String,
        treatments: List<TreatmentEntry>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (treatments.isEmpty()) return@withContext Result.success(Unit)
        val payload = JSONArray().apply {
            treatments.forEach { put(treatmentToNightscoutJson(it)) }
        }.toString()
        postJson("$baseUrl/api/v1/treatments", apiSecret, payload)
    }

    private fun postJson(rawEndpoint: String, apiSecret: String, jsonBody: String): Result<Unit> = runCatching {
        val base = normalizeBaseUrl(rawEndpoint.substringBefore("/api/v1"))
        val path = "/api/v1" + rawEndpoint.substringAfter("/api/v1")
        require(base.isNotBlank()) { "Nightscout-URL fehlt" }
        require(apiSecret.isNotBlank()) { "Nightscout API-Secret fehlt" }
        val conn = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("api-secret", sha1Hex(apiSecret.trim()))
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Nightscout Upload HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun directionToTrend(direction: String): TrendArrow = TrendArrow.fromWireName(direction)
}
