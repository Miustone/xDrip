package dev.xdripyou.app.data

import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LibreLinkRegion(val wireValue: String, val labelDe: String, val host: String) {
    EU("eu", "EU (api-eu)", "api-eu.libreview.io"),
    DE("de", "Deutschland (api-de)", "api-de.libreview.io"),
    US("us", "USA (api-us)", "api-us.libreview.io"),
    GLOBAL("global", "Global (api)", "api.libreview.io");

    companion object {
        fun fromWire(value: String?): LibreLinkRegion =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: EU
    }
}

data class LibreLinkReading(
    val timestampMs: Long,
    val mgdl: Double,
    val trend: TrendArrow,
)

/**
 * Direct LibreLinkUp follower client (compatible with LibreLinkUp v4.7+ API).
 */
object LibreLinkUpClient {
    private const val LLU_PRODUCT = "llu.android"
    private const val LLU_VERSION = "4.12.0"

    fun sha256Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun mapTrend(trendInt: Int): TrendArrow = when (trendInt) {
        1 -> TrendArrow.SINGLE_DOWN
        2 -> TrendArrow.FORTY_FIVE_DOWN
        3 -> TrendArrow.FLAT
        4 -> TrendArrow.FORTY_FIVE_UP
        5 -> TrendArrow.SINGLE_UP
        else -> TrendArrow.NOT_COMPUTABLE
    }

    fun parseTimestamp(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val patterns = listOf(
            "M/d/yyyy h:mm:ss a",
            "d/M/yyyy h:mm:ss a",
            "M/d/yyyy HH:mm:ss",
            "d/M/yyyy HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                val fmt = DateTimeFormatter.ofPattern(pattern, Locale.US)
                LocalDateTime.parse(trimmed, fmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    fun parseGraphMeasurements(jsonText: String): List<LibreLinkReading> {
        val root = JSONObject(jsonText)
        val data = root.optJSONObject("data") ?: return emptyList()
        val out = mutableListOf<LibreLinkReading>()

        val graph = data.optJSONArray("graphData") ?: JSONArray()
        for (i in 0 until graph.length()) {
            val item = graph.optJSONObject(i) ?: continue
            parseSingleItem(item)?.let { out.add(it) }
        }
        val latest = data.optJSONObject("connection")?.optJSONObject("glucoseMeasurement")
        if (latest != null) {
            parseSingleItem(latest)?.let { out.add(it) }
        }
        return out.distinctBy { it.timestampMs }.sortedBy { it.timestampMs }
    }

    private fun parseSingleItem(obj: JSONObject): LibreLinkReading? {
        val mgdl = obj.optDouble("ValueInMgPerDl", Double.NaN).takeIf { !it.isNaN() }
            ?: obj.optDouble("Value", Double.NaN).takeIf { !it.isNaN() }?.let { raw ->
                if (obj.optInt("GlucoseUnits", 1) == 0) raw * 18.0182 else raw
            }
            ?: return null
        if (mgdl < 20.0 || mgdl > 600.0) return null
        val tsRaw = obj.optString("FactoryTimestamp", obj.optString("Timestamp", ""))
        val ts = parseTimestamp(tsRaw) ?: return null
        val trend = mapTrend(obj.optInt("TrendArrow", 0))
        return LibreLinkReading(timestampMs = ts, mgdl = mgdl, trend = trend)
    }

    suspend fun fetchLatestReadings(
        email: String,
        password: String,
        region: LibreLinkRegion,
    ): Result<List<LibreLinkReading>> = withContext(Dispatchers.IO) {
        runCatching {
            val user = email.trim()
            require(user.isNotBlank() && password.isNotBlank()) { "LibreLinkUp Zugangsdaten fehlen" }
            var host = region.host
            val loginPayload = JSONObject().apply {
                put("email", user)
                put("password", password)
            }.toString()

            var loginResp = JSONObject(request("POST", "https://$host/llu/auth/login", loginPayload, null, null))
            var data = loginResp.optJSONObject("data")
            if (data?.optBoolean("redirect", false) == true) {
                val redirRegion = data.optString("region", "").lowercase(Locale.US)
                if (redirRegion.isNotBlank()) {
                    host = "api-$redirRegion.libreview.io"
                    loginResp = JSONObject(request("POST", "https://$host/llu/auth/login", loginPayload, null, null))
                    data = loginResp.optJSONObject("data")
                }
            }

            val token = data?.optJSONObject("authTicket")?.optString("token", "").orEmpty()
            val userId = data?.optJSONObject("user")?.optString("id", "").orEmpty()
            require(token.isNotBlank() && userId.isNotBlank()) {
                "LibreLinkUp Login fehlgeschlagen"
            }
            val accountIdHash = sha256Hex(userId)

            val connectionsResp = JSONObject(request("GET", "https://$host/llu/connections", null, token, accountIdHash))
            val connections = connectionsResp.optJSONArray("data") ?: JSONArray()
            require(connections.length() > 0) { "Keine LibreLinkUp-Verbindung gefunden" }
            val patientId = connections.optJSONObject(0)?.optString("patientId", "").orEmpty()
            require(patientId.isNotBlank()) { "LibreLinkUp Patient-ID fehlt" }

            val graphJson = request("GET", "https://$host/llu/connections/$patientId/graph", null, token, accountIdHash)
            parseGraphMeasurements(graphJson)
        }
    }

    private fun request(
        method: String,
        urlStr: String,
        jsonBody: String?,
        bearerToken: String?,
        accountIdHash: String?,
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("product", LLU_PRODUCT)
            setRequestProperty("version", LLU_VERSION)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (!accountIdHash.isNullOrBlank()) {
                setRequestProperty("Account-Id", accountIdHash)
            }
            if (jsonBody != null) {
                doOutput = true
            }
        }
        return try {
            if (jsonBody != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error("LibreLinkUp HTTP $code")
            }
            text
        } finally {
            conn.disconnect()
        }
    }
}
