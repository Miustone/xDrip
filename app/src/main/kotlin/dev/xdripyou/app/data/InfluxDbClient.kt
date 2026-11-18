package dev.xdripyou.app.data

import android.util.Base64
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.TreatmentEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Port of xDrip's `InfluxDBUploader` supporting both InfluxDB v1 (`/write?db=...`)
 * and InfluxDB v2 (`/api/v2/write?org=...&bucket=...`) via Line Protocol.
 */
object InfluxDbClient {

    fun buildLineProtocol(
        readings: List<GlucoseReading>,
        treatments: List<TreatmentEntry>,
    ): String {
        val sb = StringBuilder()
        for (r in readings) {
            val sourceTag = escapeTag(r.source.ifBlank { "xDrip2You" })
            val mmol = r.mgdl * GlucoseUnit.MGDL_TO_MMOL
            val delta5m = r.slopePerMin * 5.0
            sb.append("glucose,source=").append(sourceTag)
                .append(" value_mgdl=").append(String.format(Locale.US, "%.2f", r.mgdl))
                .append(",value_mmol=").append(String.format(Locale.US, "%.2f", mmol))
                .append(",direction=\"").append(escapeFieldString(r.trend.wireName)).append('"')
                .append(",delta=").append(String.format(Locale.US, "%.2f", delta5m))
                .append(' ').append(r.timestampMs)
                .append('\n')
        }
        for (t in treatments) {
            if (t.insulinUnits <= 0.0 && t.carbsGrams <= 0.0 && (t.bloodGlucoseMgdl ?: 0.0) <= 0.0) continue
            val typeTag = escapeTag(t.eventType.ifBlank { "Treatment" })
            val fields = mutableListOf<String>()
            if (t.insulinUnits > 0.0) fields.add("insulin=${String.format(Locale.US, "%.2f", t.insulinUnits)}")
            if (t.carbsGrams > 0.0) fields.add("carbs=${String.format(Locale.US, "%.1f", t.carbsGrams)}")
            t.bloodGlucoseMgdl?.takeIf { it > 0.0 }?.let {
                fields.add("mbg=${String.format(Locale.US, "%.1f", it)}")
            }
            if (fields.isNotEmpty()) {
                sb.append("treatment,type=").append(typeTag)
                    .append(' ').append(fields.joinToString(","))
                    .append(' ').append(t.timestampMs)
                    .append('\n')
            }
        }
        return sb.toString()
    }

    suspend fun upload(
        baseUrl: String,
        databaseOrBucket: String,
        org: String,
        username: String,
        tokenOrPassword: String,
        readings: List<GlucoseReading>,
        treatments: List<TreatmentEntry>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val base = NightscoutClient.normalizeBaseUrl(baseUrl)
            require(base.isNotBlank()) { "InfluxDB-URL fehlt" }
            require(databaseOrBucket.isNotBlank()) { "InfluxDB Datenbank/Bucket fehlt" }

            val body = buildLineProtocol(readings, treatments)
            if (body.isBlank()) return@runCatching 0

            val isV2 = org.isNotBlank()
            val encDb = URLEncoder.encode(databaseOrBucket.trim(), "UTF-8")
            val endpoint = if (isV2) {
                val encOrg = URLEncoder.encode(org.trim(), "UTF-8")
                "$base/api/v2/write?org=$encOrg&bucket=$encDb&precision=ms"
            } else {
                "$base/write?db=$encDb&precision=ms"
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                if (isV2 && tokenOrPassword.isNotBlank()) {
                    setRequestProperty("Authorization", "Token ${tokenOrPassword.trim()}")
                } else if (username.isNotBlank() || tokenOrPassword.isNotBlank()) {
                    val creds = "${username.trim()}:${tokenOrPassword.trim()}"
                    val b64 = Base64.encodeToString(creds.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $b64")
                }
            }
            try {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("InfluxDB HTTP $code")
                }
                readings.size
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun escapeTag(raw: String): String =
        raw.replace(" ", "\\ ").replace(",", "\\,").replace("=", "\\=")

    private fun escapeFieldString(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"")
}
