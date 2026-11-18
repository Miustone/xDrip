package dev.xdripyou.app.data

import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

enum class CareLinkRegion(val wireValue: String, val labelDe: String, val host: String) {
    EU("eu", "EU (carelink.minimed.eu)", "carelink.minimed.eu"),
    US("us", "USA (carelink.minimed.com)", "carelink.minimed.com");

    companion object {
        fun fromWire(value: String?): CareLinkRegion =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: EU
    }
}

data class CareLinkReading(
    val timestampMs: Long,
    val mgdl: Double,
    val trend: TrendArrow,
    val activeInsulin: Double? = null,
    val pumpBatteryPercent: Int? = null,
)

/**
 * CareLink Connect follower client (compatible with Medtronic MiniMed / CareLink Partner endpoints).
 */
object CareLinkFollowClient {

    fun mapCareLinkTrend(arrow: String?): TrendArrow = when (arrow?.uppercase()) {
        "UP", "SINGLE_UP" -> TrendArrow.SINGLE_UP
        "UP_DOUBLE", "DOUBLE_UP" -> TrendArrow.DOUBLE_UP
        "UP_HALF", "FORTY_FIVE_UP" -> TrendArrow.FORTY_FIVE_UP
        "NONE", "FLAT", "STABLE" -> TrendArrow.FLAT
        "DOWN_HALF", "FORTY_FIVE_DOWN" -> TrendArrow.FORTY_FIVE_DOWN
        "DOWN", "SINGLE_DOWN" -> TrendArrow.SINGLE_DOWN
        "DOWN_DOUBLE", "DOUBLE_DOWN" -> TrendArrow.DOUBLE_DOWN
        else -> TrendArrow.NOT_COMPUTABLE
    }

    fun parseConnectDataJson(rawJson: String): Result<List<CareLinkReading>> = runCatching {
        val root = JSONObject(rawJson)
        val trend = mapCareLinkTrend(root.optString("lastSGTrend"))
        val activeInsulin = root.optJSONObject("activeInsulin")?.optDouble("amount")?.takeIf { !it.isNaN() }
        val battery = root.optInt("medicalDeviceBatteryLevelPercent", -1).takeIf { it in 0..100 }
        val out = mutableListOf<CareLinkReading>()

        val sgs = root.optJSONArray("sgs")
        if (sgs != null) {
            for (i in 0 until sgs.length()) {
                val obj = sgs.optJSONObject(i) ?: continue
                val sg = obj.optDouble("sg", Double.NaN)
                if (sg.isNaN() || sg !in 20.0..600.0) continue
                val ts = parseTimestamp(obj.optString("timestamp"))
                    ?: obj.optLong("datetimeAsMs", 0L).takeIf { it > 0L }
                    ?: continue
                val isLast = i == sgs.length() - 1
                out += CareLinkReading(
                    timestampMs = ts,
                    mgdl = sg,
                    trend = if (isLast) trend else TrendArrow.NOT_COMPUTABLE,
                    activeInsulin = if (isLast) activeInsulin else null,
                    pumpBatteryPercent = if (isLast) battery else null,
                )
            }
        }

        if (out.isEmpty()) {
            val lastSgObj = root.optJSONObject("lastSG")
            if (lastSgObj != null) {
                val sg = lastSgObj.optDouble("sg", Double.NaN)
                val ts = parseTimestamp(lastSgObj.optString("timestamp")) ?: System.currentTimeMillis()
                if (!sg.isNaN() && sg in 20.0..600.0) {
                    out += CareLinkReading(
                        timestampMs = ts,
                        mgdl = sg,
                        trend = trend,
                        activeInsulin = activeInsulin,
                        pumpBatteryPercent = battery,
                    )
                }
            }
        }
        out.sortedBy { it.timestampMs }
    }

    private fun parseTimestamp(raw: String?): Long? {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return null
        return runCatching { Instant.parse(clean).toEpochMilli() }.getOrNull()
    }

    suspend fun fetchLatest(
        region: CareLinkRegion,
        username: String,
        password: String,
    ): Result<List<CareLinkReading>> = withContext(Dispatchers.IO) {
        runCatching {
            val user = username.trim()
            val pass = password
            require(user.isNotBlank() && pass.isNotBlank()) { "CareLink Benutzername/Passwort fehlen" }

            val baseUrl = "https://${region.host}"
            val authConn = (URL("$baseUrl/patient/sso/recreate").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject()
                .put("username", user)
                .put("password", pass)
                .toString()
            OutputStreamWriter(authConn.outputStream, Charsets.UTF_8).use { it.write(payload) }

            val authCode = authConn.responseCode
            val authBody = (if (authCode in 200..299) authConn.inputStream else authConn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            val setCookie = authConn.headerFields["Set-Cookie"]?.joinToString("; ") { it.substringBefore(';') }.orEmpty()
            authConn.disconnect()

            if (authCode !in 200..299) {
                error("CareLink Login HTTP $authCode")
            }
            val authToken = runCatching { JSONObject(authBody).optString("authToken") }.getOrDefault("")

            val dataConn = (URL("$baseUrl/patient/connect/data?cpSerialNumber=NONE&msgType=last24hours&requestTime=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                if (setCookie.isNotBlank()) setRequestProperty("Cookie", setCookie)
                if (authToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $authToken")
            }
            val dataCode = dataConn.responseCode
            val dataBody = (if (dataCode in 200..299) dataConn.inputStream else dataConn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            dataConn.disconnect()

            if (dataCode !in 200..299) {
                error("CareLink Daten HTTP $dataCode")
            }
            parseConnectDataJson(dataBody).getOrThrow()
        }
    }
}
