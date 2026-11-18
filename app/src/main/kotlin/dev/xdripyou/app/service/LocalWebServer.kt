package dev.xdripyou.app.service

import dev.xdripyou.app.data.GlucoseRepository
import dev.xdripyou.app.data.NightscoutClient
import dev.xdripyou.app.domain.AppSettings
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.IobCobSnapshot
import dev.xdripyou.app.domain.TreatmentEntry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Pure JSON payload builders for xDrip's local web service endpoints (`/sgv.json`, `/status.json`,
 * `/treatments.json`, `/pebble`).
 */
object LocalWebPayloads {
    fun buildSgvJson(
        readingsDesc: List<GlucoseReading>,
        settings: AppSettings,
        iobCob: IobCobSnapshot,
        briefMode: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val arr = JSONArray()
        readingsDesc.forEachIndexed { index, r ->
            val prev = readingsDesc.getOrNull(index + 1)
            val deltaMgdl = if (prev != null && (r.timestampMs - prev.timestampMs) <= 1_200_000L) {
                r.mgdl - prev.mgdl
            } else {
                0.0
            }
            val obj = JSONObject().apply {
                put("sgv", r.mgdl.roundToInt())
                put("date", r.timestampMs)
                put("dateString", Instant.ofEpochMilli(r.timestampMs).toString())
                put("trend", r.trend.ordinal)
                put("direction", r.trend.wireName)
                put("delta", (deltaMgdl * 10.0).roundToInt() / 10.0)
                if (!briefMode) {
                    put("type", "sgv")
                    put("device", "xDrip2You-${r.source}")
                    put("units_hint", if (settings.useMgdl) "mgdl" else "mmol")
                    if (index == 0 && settings.showIobCob) {
                        put("iob", String.format(Locale.US, "%.2f", iobCob.iobUnits).toDouble())
                        put("cob", iobCob.cobGrams.roundToInt())
                    }
                }
                if (index == 0) {
                    put("now", nowMs)
                }
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun buildStatusJson(settings: AppSettings, nowMs: Long = System.currentTimeMillis()): String {
        val thresholds = JSONObject().apply {
            put("bgHigh", settings.targetHighMgdl.roundToInt())
            put("bgTargetTop", settings.targetHighMgdl.roundToInt())
            put("bgTargetBottom", settings.targetLowMgdl.roundToInt())
            put("bgLow", settings.targetLowMgdl.roundToInt())
        }
        val settingsJson = JSONObject().apply {
            put("units", if (settings.useMgdl) "mg/dl" else "mmol")
            put("thresholds", thresholds)
        }
        return JSONObject().apply {
            put("status", "ok")
            put("name", "xDrip 2 You")
            put("apiEnabled", true)
            put("serverTime", nowMs)
            put("serverTimeEpoch", nowMs)
            put("settings", settingsJson)
        }.toString()
    }

    fun buildTreatmentsJson(treatmentsDesc: List<TreatmentEntry>): String {
        val arr = JSONArray()
        for (t in treatmentsDesc) {
            arr.put(NightscoutClient.treatmentToNightscoutJson(t))
        }
        return arr.toString()
    }

    fun buildPebbleJson(
        readingsDesc: List<GlucoseReading>,
        settings: AppSettings,
        iobCob: IobCobSnapshot,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val latest = readingsDesc.firstOrNull()
        val prev = readingsDesc.getOrNull(1)
        val statusArr = JSONArray().apply {
            put(JSONObject().apply { put("now", nowMs) })
        }
        val bgsArr = JSONArray()
        if (latest != null) {
            val deltaMgdl = if (prev != null && (latest.timestampMs - prev.timestampMs) <= 1_200_000L) {
                latest.mgdl - prev.mgdl
            } else {
                0.0
            }
            val bgObj = JSONObject().apply {
                put("sgv", GlucoseUnit.formatValue(latest.mgdl, settings.useMgdl))
                put("trend", latest.trend.ordinal)
                put("direction", latest.trend.wireName)
                put("datetime", latest.timestampMs)
                put(
                    "bgdelta",
                    if (settings.useMgdl) {
                        deltaMgdl.roundToInt().toString()
                    } else {
                        String.format(Locale.US, "%.1f", deltaMgdl / GlucoseUnit.MMOL_TO_MGDL)
                    }
                )
                put("iob", String.format(Locale.US, "%.2f", iobCob.iobUnits))
                put("cob", "${iobCob.cobGrams.roundToInt()}")
            }
            bgsArr.put(bgObj)
        }
        return JSONObject().apply {
            put("status", statusArr)
            put("bgs", bgsArr)
        }.toString()
    }
}

/**
 * Port of xDrip's `XdripWebService` listening on port `17580` for local consumers
 * (AAPS, watchfaces, Nightwatch, Tasker, or local dashboards).
 */
class LocalWebServer(
    private val repository: GlucoseRepository,
    private val onTaskerCommand: (suspend (String) -> String)? = null,
) {
    @Volatile
    private var running = false

    @Volatile
    private var boundOpenNetwork = false

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val workerPool = Executors.newFixedThreadPool(6)

    @Synchronized
    fun syncWithSettings(settings: AppSettings) {
        if (!settings.localWebServiceEnabled) {
            stop()
            return
        }
        if (running && boundOpenNetwork == settings.localWebServiceOpenNetwork) {
            return
        }
        stop()
        start(openNetwork = settings.localWebServiceOpenNetwork)
    }

    @Synchronized
    fun start(openNetwork: Boolean) {
        if (running) return
        running = true
        boundOpenNetwork = openNetwork
        acceptThread = Thread({
            try {
                val bindAddr = if (openNetwork) null else InetAddress.getByName("127.0.0.1")
                val ss = ServerSocket(PORT, 16, bindAddr)
                serverSocket = ss
                while (running) {
                    val client = try {
                        ss.accept()
                    } catch (_: SocketException) {
                        break
                    }
                    workerPool.execute { handleClient(client) }
                }
            } catch (_: Exception) {
                running = false
            }
        }, "xDripLocalWebServer").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        } finally {
            serverSocket = null
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                writeResponse(sock.getOutputStream(), 400, """{"error":"Bad Request"}""")
                return
            }
            val method = parts[0].uppercase(Locale.US)
            val rawTarget = parts[1]

            var headerSecret: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase(Locale.US)
                    if (key == "api-secret") {
                        headerSecret = line.substring(colon + 1).trim()
                    }
                }
            }

            if (method != "GET") {
                writeResponse(sock.getOutputStream(), 405, """{"error":"Method Not Allowed"}""")
                return
            }

            val settings = runBlocking { repository.settingsStore.current() }
            if (settings.localWebServiceSecret.isNotBlank()) {
                val expectedSha1 = NightscoutClient.sha1Hex(settings.localWebServiceSecret.trim())
                val provided = headerSecret?.trim().orEmpty()
                if (provided != expectedSha1 && provided != settings.localWebServiceSecret.trim()) {
                    writeResponse(sock.getOutputStream(), 401, """{"error":"Unauthorized"}""")
                    return
                }
            }

            val path = rawTarget.substringBefore('?').lowercase(Locale.US)
            val query = rawTarget.substringAfter('?', "")
            val params = parseQuery(query)

            val responseBody = when (path) {
                "/sgv.json", "/api/v1/entries/sgv.json", "/api/v1/entries.json" -> {
                    val count = (params["count"]?.toIntOrNull() ?: 24).coerceIn(1, 500)
                    val brief = params.containsKey("brief_mode")
                    runBlocking {
                        val readings = repository.getLatestReadings(count)
                        val iobCob = repository.currentIobCob()
                        LocalWebPayloads.buildSgvJson(readings, settings, iobCob, brief)
                    }
                }
                "/status.json", "/api/v1/status.json" -> {
                    LocalWebPayloads.buildStatusJson(settings)
                }
                "/treatments.json", "/api/v1/treatments.json" -> {
                    val count = (params["count"]?.toIntOrNull() ?: 50).coerceIn(1, 300)
                    runBlocking {
                        val treatments = repository.getTreatmentsSince(System.currentTimeMillis() - 7 * 86_400_000L, count)
                        LocalWebPayloads.buildTreatmentsJson(treatments)
                    }
                }
                "/pebble" -> {
                    runBlocking {
                        val readings = repository.getLatestReadings(2)
                        val iobCob = repository.currentIobCob()
                        LocalWebPayloads.buildPebbleJson(readings, settings, iobCob)
                    }
                }
                else -> {
                    if (path.startsWith("/tasker/")) {
                        val cmd = java.net.URLDecoder.decode(rawTarget.substringBefore('?').substring(8), "UTF-8")
                        val msg = runBlocking { onTaskerCommand?.invoke(cmd) ?: "Tasker nicht aktiv" }
                        JSONObject().apply { put("result", msg) }.toString()
                    } else {
                        writeResponse(sock.getOutputStream(), 404, """{"error":"Not Found"}""")
                        return
                    }
                }
            }
            writeResponse(sock.getOutputStream(), 200, responseBody)
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            val idx = pair.indexOf('=')
            if (idx >= 0) {
                out[pair.substring(0, idx)] = pair.substring(idx + 1)
            } else if (pair.isNotBlank()) {
                out[pair] = ""
            }
        }
        return out
    }

    private fun writeResponse(out: OutputStream, statusCode: Int, jsonBody: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)
        out.write(headers)
        out.write(bytes)
        out.flush()
    }

    companion object {
        const val PORT = 17580
    }
}
