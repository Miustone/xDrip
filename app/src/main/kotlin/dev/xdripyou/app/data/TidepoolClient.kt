package dev.xdripyou.app.data

import android.util.Base64
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TreatmentEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.TimeZone
import java.util.UUID

/**
 * Port of xDrip's `TidepoolUploader` for uploading CGM readings (`cbg`) and treatments (`bolus`/`food`/`smbg`)
 * to Tidepool (`api.tidepool.org`).
 */
object TidepoolClient {
    private const val PRODUCTION_BASE = "https://api.tidepool.org"
    private const val DEV_BASE = "https://int-api.tidepool.org"
    private const val SESSION_HEADER = "x-tidepool-session-token"

    fun buildPayloadJson(
        readings: List<GlucoseReading>,
        treatments: List<TreatmentEntry>,
    ): JSONArray {
        val arr = JSONArray()
        val tzOffsetMin = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        for (r in readings) {
            val iso = Instant.ofEpochMilli(r.timestampMs).toString()
            arr.put(
                JSONObject().apply {
                    put("type", "cbg")
                    put("units", "mg/dL")
                    put("value", r.mgdl)
                    put("time", iso)
                    put("deviceTime", iso.removeSuffix("Z"))
                    put("timezoneOffset", tzOffsetMin)
                    put("clockDriftOffset", 0)
                    put("conversionOffset", 0)
                    put("deviceId", "xDrip2You")
                    put("guid", UUID.nameUUIDFromBytes("cbg_${r.timestampMs}".toByteArray()).toString())
                }
            )
        }
        for (t in treatments) {
            val iso = Instant.ofEpochMilli(t.timestampMs).toString()
            if (t.insulinUnits > 0.0) {
                arr.put(
                    JSONObject().apply {
                        put("type", "bolus")
                        put("subType", "normal")
                        put("normal", t.insulinUnits)
                        put("time", iso)
                        put("deviceTime", iso.removeSuffix("Z"))
                        put("timezoneOffset", tzOffsetMin)
                        put("deviceId", "xDrip2You")
                        put("guid", UUID.nameUUIDFromBytes("bolus_${t.timestampMs}_${t.id}".toByteArray()).toString())
                    }
                )
            }
            if (t.carbsGrams > 0.0) {
                arr.put(
                    JSONObject().apply {
                        put("type", "food")
                        put("nutrition", JSONObject().apply {
                            put("carbohydrate", JSONObject().apply {
                                put("net", t.carbsGrams)
                                put("units", "grams")
                            })
                        })
                        put("time", iso)
                        put("deviceTime", iso.removeSuffix("Z"))
                        put("timezoneOffset", tzOffsetMin)
                        put("deviceId", "xDrip2You")
                        put("guid", UUID.nameUUIDFromBytes("food_${t.timestampMs}_${t.id}".toByteArray()).toString())
                    }
                )
            }
            t.bloodGlucoseMgdl?.takeIf { it > 0.0 }?.let { bg ->
                arr.put(
                    JSONObject().apply {
                        put("type", "smbg")
                        put("units", "mg/dL")
                        put("value", bg)
                        put("subType", "manual")
                        put("time", iso)
                        put("deviceTime", iso.removeSuffix("Z"))
                        put("timezoneOffset", tzOffsetMin)
                        put("deviceId", "xDrip2You")
                        put("guid", UUID.nameUUIDFromBytes("smbg_${t.timestampMs}_${t.id}".toByteArray()).toString())
                    }
                )
            }
        }
        return arr
    }

    suspend fun upload(
        email: String,
        password: String,
        useDevServers: Boolean,
        readings: List<GlucoseReading>,
        treatments: List<TreatmentEntry>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val user = email.trim()
            require(user.isNotBlank() && password.isNotBlank()) { "Tidepool Zugangsdaten fehlen" }
            val payload = buildPayloadJson(readings, treatments)
            if (payload.length() == 0) return@runCatching 0

            val base = if (useDevServers) DEV_BASE else PRODUCTION_BASE
            val creds = Base64.encodeToString("$user:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // 1. Login
            val loginConn = (URL("$base/auth/login").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Authorization", "Basic $creds")
                setRequestProperty("X-Tidepool-Client-Name", "dev.xdripyou.app")
                setRequestProperty("X-Tidepool-Client-Version", "1.0.0")
            }
            val token: String
            val userId: String
            try {
                OutputStreamWriter(loginConn.outputStream, Charsets.UTF_8).use { it.write("") }
                val code = loginConn.responseCode
                if (code !in 200..299) error("Tidepool Login HTTP $code")
                token = loginConn.getHeaderField(SESSION_HEADER)
                    ?: error("Tidepool Session-Token fehlt")
                val body = loginConn.inputStream.bufferedReader().use { it.readText() }
                userId = JSONObject(body).optString("userid", "")
                require(userId.isNotBlank()) { "Tidepool User-ID fehlt" }
            } finally {
                loginConn.disconnect()
            }

            // 2. Open/create dataset
            val openBody = JSONObject().apply {
                put("type", "upload")
                put("client", JSONObject().apply {
                    put("name", "dev.xdripyou.app")
                    put("version", "1.0.0")
                })
                put("dataSetType", "continuous")
                put("deviceManufacturers", JSONArray().apply { put("xDrip 2 You") })
                put("timeProcessing", "across-the-board-timezone")
                put("timezone", TimeZone.getDefault().id)
            }.toString()

            val dsResp = requestJson("POST", "$base/v1/users/$userId/data_sets", token, openBody)
            val dsJson = JSONObject(dsResp)
            val uploadId = dsJson.optString("uploadId", dsJson.optString("id", ""))
            require(uploadId.isNotBlank()) { "Tidepool Dataset-ID fehlt" }

            // 3. Upload records
            requestJson("POST", "$base/v1/datasets/$uploadId/data", token, payload.toString())

            // 4. Close dataset
            val closeBody = JSONObject().apply { put("dataSetType", "continuous") }.toString()
            requestJson("PUT", "$base/v1/datasets/$uploadId", token, closeBody)

            payload.length()
        }
    }

    private fun requestJson(method: String, urlStr: String, token: String, body: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty(SESSION_HEADER, token)
        }
        return try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Tidepool HTTP $code")
            text
        } finally {
            conn.disconnect()
        }
    }
}
