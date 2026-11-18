package dev.xdripyou.app.data

import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class GlookoRegion(
    val wireValue: String,
    val labelDe: String,
    val apiHost: String,
    val webHost: String,
) {
    EU_DE("eu_de", "DE / FR (de-fr)", "de-fr.api.glooko.com", "de-fr.my.glooko.com"),
    EU("eu", "EU (eu.api)", "eu.api.glooko.com", "eu.my.glooko.com"),
    US("us", "USA (api.glooko)", "api.glooko.com", "my.glooko.com"),
    GB("gb", "UK (gb.api)", "gb.api.glooko.com", "gb.my.glooko.com"),
    CA("ca", "Kanada (ca.api)", "ca.api.glooko.com", "ca.my.glooko.com"),
    AU("au", "Australien (au.api)", "au.api.glooko.com", "au.my.glooko.com");

    companion object {
        fun fromWire(value: String?): GlookoRegion =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: EU_DE
    }
}

data class GlookoReading(
    val timestampMs: Long,
    val mgdl: Double,
    val trend: TrendArrow = TrendArrow.NOT_COMPUTABLE,
    val source: String = "Glooko",
)

data class GlookoPayload(
    val readings: List<GlookoReading> = emptyList(),
    val treatments: List<TreatmentEntry> = emptyList(),
    val glookoCode: String? = null,
)

/**
 * Glooko Cloud client supporting regional API clusters (`de-fr.api.glooko.com`, `eu.api.glooko.com`, `api.glooko.com`),
 * fetching CGM values, insulin boluses, carbohydrates, and meter BG checks.
 * Uses pure-Kotlin JSON scanning in parsers so JVM unit tests and Android runtime behave identically.
 */
object GlookoClient {

    private const val MGDL_PER_MMOL = 18.0182
    private val numberFieldRegex = Regex(""""([A-Za-z0-9_]+)"\s*:\s*(-?\d+(?:\.\d+)?)""")
    private val stringFieldRegex = Regex(""""([A-Za-z0-9_]+)"\s*:\s*"([^"]*)"""")

    suspend fun fetchData(
        region: GlookoRegion,
        email: String,
        password: String,
        lookbackHours: Int = 48,
    ): Result<GlookoPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val user = email.trim()
            require(user.isNotBlank() && password.isNotBlank()) { "Glooko E-Mail oder Passwort fehlt" }

            val cookieJar = mutableMapOf<String, String>()

            val loginUrl = "https://${region.apiHost}/api/v2/sessions"
            val escEmail = escapeJson(user)
            val escPass = escapeJson(password)
            val loginBody = """{"userLogin":{"email":"$escEmail","password":"$escPass"},"deviceInformation":{"applicationType":"logbook","os":"android","osVersion":"34","device":"Android","applicationVersion":"6.1.3"}}"""

            val loginResp = executeHttp(
                urlStr = loginUrl,
                method = "POST",
                body = loginBody,
                origin = "https://${region.webHost}",
                cookies = cookieJar,
                bearerToken = null,
            )

            if (loginResp.code == 401 || loginResp.code == 403) {
                if (loginResp.body.contains("cloudflare", ignoreCase = true) ||
                    loginResp.body.contains("captcha", ignoreCase = true)
                ) {
                    error("Glooko Cloud blockiert direkten Login (Captcha/Bot-Schutz). Nutze den Glooko ZIP/CSV-Import.")
                }
                error("Glooko Login fehlgeschlagen (${loginResp.code}). Zugangsdaten oder Region prüfen.")
            }
            if (loginResp.code !in 200..299) {
                error("Glooko Server HTTP ${loginResp.code} bei Login (Tipp: ggf. Region prüfen oder ZIP/CSV-Import nutzen).")
            }

            val authHeaderToken = extractFieldString(loginResp.body, "token")
                ?: extractFieldString(loginResp.body, "session_token")

            var glookoCode: String? = null
            val sessionUserResp = executeHttp(
                urlStr = "https://${region.apiHost}/api/v3/session/users",
                method = "GET",
                body = null,
                origin = "https://${region.webHost}",
                cookies = cookieJar,
                bearerToken = authHeaderToken,
            )
            if (sessionUserResp.code in 200..299) {
                glookoCode = extractGlookoCode(sessionUserResp.body)
            }
            if (glookoCode == null) {
                glookoCode = extractGlookoCode(loginResp.body)
            }

            val endMs = System.currentTimeMillis()
            val startMs = endMs - lookbackHours.coerceIn(1, 24 * 30) * 3_600_000L
            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val startIso = isoFmt.format(startMs)
            val endIso = isoFmt.format(endMs)
            val patientParam = glookoCode?.let { "&patient=$it" }.orEmpty()

            val allReadings = mutableListOf<GlookoReading>()
            val allTreatments = mutableListOf<TreatmentEntry>()

            val graphUrl = buildString {
                append("https://${region.apiHost}/api/v3/graph/data")
                append("?startDate=$startIso&endDate=$endIso$patientParam")
                append("&series[]=cgmHigh&series[]=cgmLow&series[]=cgmNormal")
                append("&series[]=bgHigh&series[]=bgLow&series[]=bgNormal")
                append("&series[]=bolus&series[]=carbs&series[]=manualInsulin")
            }
            val graphResp = executeHttp(
                urlStr = graphUrl,
                method = "GET",
                body = null,
                origin = "https://${region.webHost}",
                cookies = cookieJar,
                bearerToken = authHeaderToken,
            )
            if (graphResp.code in 200..299) {
                val parsedGraph = parseGraphDataJson(graphResp.body)
                allReadings += parsedGraph.readings
                allTreatments += parsedGraph.treatments
            }

            val v2Endpoints = listOf(
                "https://${region.apiHost}/api/v2/readings?startDate=$startIso&endDate=$endIso$patientParam" to "cgm",
                "https://${region.apiHost}/api/v2/pumps/normal_boluses?startDate=$startIso&endDate=$endIso$patientParam" to "bolus",
                "https://${region.apiHost}/api/v2/pumps/extended_boluses?startDate=$startIso&endDate=$endIso$patientParam" to "bolus",
                "https://${region.apiHost}/api/v2/pumps/carbs?startDate=$startIso&endDate=$endIso$patientParam" to "carbs",
                "https://${region.apiHost}/api/v2/food?startDate=$startIso&endDate=$endIso$patientParam" to "carbs",
                "https://${region.apiHost}/api/v2/insulin?startDate=$startIso&endDate=$endIso$patientParam" to "bolus",
                "https://${region.apiHost}/api/v2/readings/bg?startDate=$startIso&endDate=$endIso$patientParam" to "bg",
            )
            for ((url, kind) in v2Endpoints) {
                val resp = executeHttp(
                    urlStr = url,
                    method = "GET",
                    body = null,
                    origin = "https://${region.webHost}",
                    cookies = cookieJar,
                    bearerToken = authHeaderToken,
                )
                if (resp.code in 200..299) {
                    when (kind) {
                        "cgm" -> allReadings += parseV2ReadingsJson(resp.body)
                        "bolus", "carbs", "bg" -> allTreatments += parseV2TreatmentsJson(resp.body, kind)
                    }
                }
            }

            val mergedReadings = allReadings
                .filter { it.mgdl in 20.0..600.0 && it.timestampMs > 0L }
                .distinctBy { it.timestampMs / 60_000L }
                .sortedBy { it.timestampMs }

            val mergedTreatments = deduplicateTreatments(allTreatments)
                .sortedBy { it.timestampMs }

            GlookoPayload(
                readings = mergedReadings,
                treatments = mergedTreatments,
                glookoCode = glookoCode,
            )
        }
    }

    internal fun extractGlookoCode(jsonText: String): String? =
        extractFieldString(jsonText, "glookoCode")?.takeIf { it.isNotBlank() }
            ?: extractFieldString(jsonText, "patientId")?.takeIf { it.isNotBlank() }

    fun parseGraphDataJson(rawJson: String): GlookoPayload {
        val readings = mutableListOf<GlookoReading>()
        val treatments = mutableListOf<TreatmentEntry>()
        val unitStr = extractFieldString(rawJson, "units") ?: ""
        val isMmol = unitStr.contains("mmol", ignoreCase = true)

        val cgmKeys = listOf("cgmHigh", "cgmLow", "cgmNormal", "cgmReadings", "cgm", "readings")
        for (key in cgmKeys) {
            for (item in extractObjectArray(rawJson, key)) {
                val ts = extractTimestamp(item) ?: continue
                val rawVal = extractDouble(item, "y", "value", "mgdl", "glucose", "reading") ?: continue
                val mgdl = normalizeBgValue(rawVal, item, isMmol) ?: continue
                readings += GlookoReading(timestampMs = ts, mgdl = mgdl, source = "Glooko")
            }
        }

        val bolusKeys = listOf("bolus", "normalBolus", "manualInsulin", "insulin")
        for (key in bolusKeys) {
            for (item in extractObjectArray(rawJson, key)) {
                val ts = extractTimestamp(item) ?: continue
                val units = extractDouble(item, "y", "value", "insulin", "deliveredUnits", "units") ?: 0.0
                val carbs = extractDouble(item, "carbs", "carbsInput") ?: 0.0
                if (units > 0.0 || carbs > 0.0) {
                    val label = extractFieldString(item, "label").orEmpty()
                    treatments += TreatmentEntry(
                        timestampMs = ts,
                        insulinUnits = units.coerceAtLeast(0.0),
                        carbsGrams = carbs.coerceAtLeast(0.0),
                        note = label.ifBlank { "Glooko Cloud" },
                        eventType = if (carbs > 0.0) "Meal Bolus" else "Correction Bolus",
                    )
                }
            }
        }

        val carbKeys = listOf("carbs", "food", "meals")
        for (key in carbKeys) {
            for (item in extractObjectArray(rawJson, key)) {
                val ts = extractTimestamp(item) ?: continue
                val carbs = extractDouble(item, "y", "value", "carbs", "grams") ?: 0.0
                if (carbs > 0.0) {
                    val name = extractFieldString(item, "name") ?: extractFieldString(item, "label").orEmpty()
                    treatments += TreatmentEntry(
                        timestampMs = ts,
                        carbsGrams = carbs,
                        note = name.ifBlank { "Glooko Cloud" },
                        eventType = "Meal Bolus",
                    )
                }
            }
        }

        val bgKeys = listOf("bgHigh", "bgLow", "bgNormal", "bg")
        for (key in bgKeys) {
            for (item in extractObjectArray(rawJson, key)) {
                val ts = extractTimestamp(item) ?: continue
                val rawVal = extractDouble(item, "y", "value", "bg") ?: continue
                val mgdl = normalizeBgValue(rawVal, item, isMmol) ?: continue
                treatments += TreatmentEntry(
                    timestampMs = ts,
                    bloodGlucoseMgdl = mgdl,
                    note = "Glooko BZ",
                    eventType = "BG Check",
                )
            }
        }

        return GlookoPayload(
            readings = readings.sortedBy { it.timestampMs },
            treatments = deduplicateTreatments(treatments),
        )
    }

    fun parseV2ReadingsJson(rawJson: String): List<GlookoReading> {
        val objects = extractFirstMatchingArray(rawJson, "readings", "values", "cgmReadings", "data")
        val out = mutableListOf<GlookoReading>()
        for (obj in objects) {
            val ts = extractTimestamp(obj) ?: continue
            val raw = extractDouble(obj, "value", "mgdl", "reading", "glucoseValue") ?: continue
            val mgdl = normalizeBgValue(raw, obj, defaultMmol = false) ?: continue
            val trendStr = extractFieldString(obj, "trendArrow") ?: extractFieldString(obj, "trend")
            out += GlookoReading(
                timestampMs = ts,
                mgdl = mgdl,
                trend = mapTrend(trendStr),
                source = "Glooko",
            )
        }
        return out
    }

    fun parseV2TreatmentsJson(rawJson: String, kind: String): List<TreatmentEntry> {
        val objects = extractFirstMatchingArray(
            rawJson,
            "normalBoluses",
            "extendedBoluses",
            "boluses",
            "carbs",
            "foods",
            "insulins",
            "readings",
            "values",
            "data",
        )
        val out = mutableListOf<TreatmentEntry>()
        for (obj in objects) {
            val ts = extractTimestamp(obj) ?: continue
            when (kind) {
                "bolus" -> {
                    val units = extractDouble(obj, "insulinDelivered", "deliveredUnits", "units", "value", "insulin") ?: 0.0
                    val carbs = extractDouble(obj, "carbsInput", "carbs", "grams") ?: 0.0
                    if (units > 0.0 || carbs > 0.0) {
                        val bolusType = (extractFieldString(obj, "bolusType") ?: extractFieldString(obj, "insulinType")).orEmpty().trim()
                        out += TreatmentEntry(
                            timestampMs = ts,
                            insulinUnits = units.coerceAtLeast(0.0),
                            carbsGrams = carbs.coerceAtLeast(0.0),
                            note = if (bolusType.isNotEmpty()) "$bolusType (Glooko)" else "Glooko",
                            eventType = if (carbs > 0.0) "Meal Bolus" else "Correction Bolus",
                        )
                    }
                }
                "carbs" -> {
                    val carbs = extractDouble(obj, "value", "carbs", "grams", "carbsGrams") ?: 0.0
                    if (carbs > 0.0) {
                        val name = (extractFieldString(obj, "name") ?: extractFieldString(obj, "foodName")).orEmpty().trim()
                        out += TreatmentEntry(
                            timestampMs = ts,
                            carbsGrams = carbs,
                            note = if (name.isNotEmpty()) "$name (Glooko)" else "Glooko",
                            eventType = "Meal Bolus",
                        )
                    }
                }
                "bg" -> {
                    val raw = extractDouble(obj, "value", "bg", "glucoseValue") ?: continue
                    val mgdl = normalizeBgValue(raw, obj, defaultMmol = false) ?: continue
                    out += TreatmentEntry(
                        timestampMs = ts,
                        bloodGlucoseMgdl = mgdl,
                        note = "Glooko BZ",
                        eventType = "BG Check",
                    )
                }
            }
        }
        return out
    }

    private fun escapeJson(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun extractFirstMatchingArray(rawJson: String, vararg keys: String): List<String> {
        for (k in keys) {
            val found = extractObjectArray(rawJson, k)
            if (found.isNotEmpty()) return found
        }
        if (rawJson.trimStart().startsWith("[")) {
            return extractRootArrayObjects(rawJson)
        }
        return emptyList()
    }

    private fun extractObjectArray(json: String, arrayKey: String): List<String> {
        val keyMatch = Regex(""""$arrayKey"\s*:\s*\[""").find(json) ?: return emptyList()
        return scanArrayObjects(json, keyMatch.range.last + 1)
    }

    private fun extractRootArrayObjects(json: String): List<String> {
        val idx = json.indexOf('[')
        if (idx < 0) return emptyList()
        return scanArrayObjects(json, idx + 1)
    }

    private fun scanArrayObjects(json: String, startIdx: Int): List<String> {
        var idx = startIdx
        var bracketDepth = 1
        val objects = mutableListOf<String>()
        var objStart = -1
        var braceDepth = 0
        var inString = false
        var escaped = false

        while (idx < json.length && bracketDepth > 0) {
            val ch = json[idx]
            if (escaped) {
                escaped = false
            } else if (ch == '\\' && inString) {
                escaped = true
            } else if (ch == '"') {
                inString = !inString
            } else if (!inString) {
                when (ch) {
                    '[' -> bracketDepth++
                    ']' -> bracketDepth--
                    '{' -> {
                        if (braceDepth == 0) objStart = idx
                        braceDepth++
                    }
                    '}' -> {
                        braceDepth--
                        if (braceDepth == 0 && objStart >= 0) {
                            objects += json.substring(objStart, idx + 1)
                            objStart = -1
                        }
                    }
                }
            }
            idx++
        }
        return objects
    }

    private fun extractFieldNumber(obj: String, key: String): Double? {
        for (m in numberFieldRegex.findAll(obj)) {
            if (m.groupValues[1] == key) return m.groupValues[2].toDoubleOrNull()
        }
        return null
    }

    private fun extractFieldString(obj: String, key: String): String? {
        for (m in stringFieldRegex.findAll(obj)) {
            if (m.groupValues[1] == key) return m.groupValues[2]
        }
        return null
    }

    private fun extractDouble(obj: String, vararg keys: String): Double? {
        for (k in keys) {
            extractFieldNumber(obj, k)?.let { return it }
        }
        return null
    }

    private fun extractTimestamp(obj: String): Long? {
        val numKeys = listOf("x", "timestampMs", "epochMs")
        for (k in numKeys) {
            val v = extractFieldNumber(obj, k)?.toLong() ?: continue
            if (v > 1_000_000_000_000L) return v
            if (v in 1_000_000_000L..9_999_999_999L) return v * 1000L
        }
        val strKeys = listOf("timestamp", "systemTime", "displayTime", "dateTime", "updatedAt", "createdAt")
        for (k in strKeys) {
            val s = extractFieldString(obj, k)?.trim().orEmpty()
            if (s.isNotEmpty()) {
                parseIsoOrDateTime(s)?.let { return it }
            }
        }
        return null
    }

    internal fun parseIsoOrDateTime(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli() }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
        )
        for (p in patterns) {
            runCatching {
                val fmt = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                fmt.parse(s)?.time?.let { return it }
            }
        }
        return null
    }

    private fun normalizeBgValue(raw: Double, obj: String, defaultMmol: Boolean): Double? {
        val unitStr = (extractFieldString(obj, "units") ?: extractFieldString(obj, "unit") ?: "").lowercase()
        val isMmol = unitStr.contains("mmol") || (defaultMmol && !unitStr.contains("mg"))
        val mgdl = if (isMmol || (!unitStr.contains("mg") && raw in 1.0..33.0)) raw * MGDL_PER_MMOL else raw
        return mgdl.takeIf { it in 20.0..600.0 }
    }

    private fun mapTrend(raw: String?): TrendArrow = when (raw?.trim()?.uppercase()) {
        "DOUBLE_UP", "UP_DOUBLE", "2" -> TrendArrow.DOUBLE_UP
        "SINGLE_UP", "UP", "1" -> TrendArrow.SINGLE_UP
        "FORTY_FIVE_UP", "UP_45", "SLIGHTLY_UP" -> TrendArrow.FORTY_FIVE_UP
        "FLAT", "STABLE", "0" -> TrendArrow.FLAT
        "FORTY_FIVE_DOWN", "DOWN_45", "SLIGHTLY_DOWN" -> TrendArrow.FORTY_FIVE_DOWN
        "SINGLE_DOWN", "DOWN", "-1" -> TrendArrow.SINGLE_DOWN
        "DOUBLE_DOWN", "DOWN_DOUBLE", "-2" -> TrendArrow.DOUBLE_DOWN
        else -> TrendArrow.NOT_COMPUTABLE
    }

    private fun deduplicateTreatments(items: List<TreatmentEntry>): List<TreatmentEntry> {
        val out = mutableListOf<TreatmentEntry>()
        for (item in items.sortedBy { it.timestampMs }) {
            val dup = out.any { existing ->
                abs(existing.timestampMs - item.timestampMs) <= 60_000L &&
                    abs(existing.insulinUnits - item.insulinUnits) < 0.05 &&
                    abs(existing.carbsGrams - item.carbsGrams) < 0.5 &&
                    existing.eventType == item.eventType
            }
            if (!dup) out += item
        }
        return out
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun executeHttp(
        urlStr: String,
        method: String,
        body: String?,
        origin: String,
        cookies: MutableMap<String, String>,
        bearerToken: String?,
    ): HttpResult {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Origin", origin)
            setRequestProperty("Referer", "$origin/")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            if (cookies.isNotEmpty()) {
                setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        }
        val code = runCatching { conn.responseCode }.getOrDefault(-1)
        val setCookies = conn.headerFields["Set-Cookie"] ?: conn.headerFields["set-cookie"] ?: emptyList()
        for (header in setCookies) {
            runCatching {
                HttpCookie.parse(header).forEach { cookie ->
                    if (cookie.value.isNotBlank()) cookies[cookie.name] = cookie.value
                }
            }
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = runCatching { stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty() }.getOrDefault("")
        conn.disconnect()
        return HttpResult(code, text)
    }
}
