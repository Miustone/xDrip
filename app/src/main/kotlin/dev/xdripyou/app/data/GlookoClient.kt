package dev.xdripyou.app.data

import dev.xdripyou.app.domain.BasalSchedule
import dev.xdripyou.app.domain.MiniJson
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import kotlin.math.abs

enum class GlookoRegion(
    val wireValue: String,
    val labelDe: String,
    val apiHost: String,
    val webHost: String,
) {
    EU_DE("eu_de", "DE / FR (de-fr)", "de-fr.api.glooko.com", "de-fr.my.glooko.com"),
    EU("eu", "EU (eu.my)", "eu.api.glooko.com", "eu.my.glooko.com"),
    US("us", "USA (my.glooko)", "api.glooko.com", "my.glooko.com"),
    GB("gb", "UK (gb.my)", "gb.api.glooko.com", "gb.my.glooko.com"),
    CA("ca", "Kanada (ca.my)", "ca.api.glooko.com", "ca.my.glooko.com"),
    AU("au", "Australien (au.my)", "au.api.glooko.com", "au.my.glooko.com");

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
    val basalHourlyRates: List<Double>? = null,
    val profileDiaMinutes: Int? = null,
    val profileIsfMgdl: Double? = null,
    val targetLowMgdl: Double? = null,
    val targetHighMgdl: Double? = null,
    val iobSnapshotUnits: Double? = null,
    val iobSnapshotTimestampMs: Long? = null,
)

private data class GlookoSession(
    val webOrigin: String,
    val apiBase: String,
    val cookies: String,
    val patientCode: String?,
)

/**
 * Port of `GlucoAhead`'s battle-tested Nightscout-connect-compatible Glooko Cloud client:
 * - Web-first CSRF auth (`/users/sign_in?id=login_form&locale=en-GB`) with automatic cluster failover (`eu.my.glooko.com` <-> `de-fr.my.glooko.com`)
 *   and JSON API fallback (`/api/v3/users/sign_in`, `/api/v2/users/sign_in`)
 * - Cursor-paginated v2 endpoints (`/api/v2/cgm/egvs`, `normal_boluses`, `extended_boluses`, `injection_boluses`,
 *   `cgm/insulin_events`, `foods`, `cgm/carbs_events`, `readings`) + pump settings profile (`/api/v2/external/pumps/settings`)
 * - v3 Graph telemetry (`/api/v3/graph/data`) with Fake-UTC vs True-UTC timestamp handling
 * - Uses pure-Kotlin `MiniJson` parsing so JVM unit tests and Android runtime execute identically.
 */
object GlookoClient {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    private const val NULL_GUID = "00000000-0000-0000-0000-000000000000"
    private const val MGDL_PER_MMOL = 18.0182

    internal fun signInApiUrl(region: GlookoRegion): String = "https://${region.apiHost}/api/v2/users/sign_in"

    internal fun extractCsrfToken(html: String): String? {
        val metaMatcher = Pattern.compile("<meta[^>]+name=[\"']csrf-token[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        if (metaMatcher.find()) return metaMatcher.group(1)
        val metaRevMatcher = Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']csrf-token[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        if (metaRevMatcher.find()) return metaRevMatcher.group(1)
        val inputMatcher = Pattern.compile("name=[\"']authenticity_token[\"'][^>]+value=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        if (inputMatcher.find()) return inputMatcher.group(1)
        return null
    }

    internal fun clusterCandidates(region: GlookoRegion): List<Pair<String, String>> = when (region) {
        GlookoRegion.EU_DE -> listOf(
            "https://de-fr.my.glooko.com" to "https://de-fr.api.glooko.com",
            "https://eu.my.glooko.com" to "https://eu.api.glooko.com",
        )
        GlookoRegion.EU -> listOf(
            "https://eu.my.glooko.com" to "https://eu.api.glooko.com",
            "https://de-fr.my.glooko.com" to "https://de-fr.api.glooko.com",
        )
        GlookoRegion.US -> listOf(
            "https://my.glooko.com" to "https://api.glooko.com",
            "https://my.glooko.com" to "https://us.api.glooko.com",
        )
        else -> listOf(
            "https://${region.webHost}" to "https://${region.apiHost}",
            "https://eu.my.glooko.com" to "https://eu.api.glooko.com",
        )
    }

    suspend fun fetchData(
        region: GlookoRegion,
        email: String,
        password: String,
        lookbackHours: Int = 48,
    ): Result<GlookoPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val user = email.trim()
            require(user.isNotBlank() && password.isNotBlank()) { "Glooko E-Mail oder Passwort fehlt" }

            val (ok, authMsg, session) = authenticate(user, password, region)
            if (!ok || session == null) {
                error(authMsg)
            }

            val nowMs = System.currentTimeMillis()
            val futureCutoffMs = nowMs + 180_000L
            val clampedSince = nowMs - lookbackHours.coerceIn(1, 24 * 30) * 3_600_000L
            val recentSince = maxOf(clampedSince, nowMs - 2L * 86_400_000L)
            val sinceIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(clampedSince))
            val recentSinceIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(recentSince))
            val graphSinceIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(recentSince))
            val endIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(nowMs))

            val base = session.apiBase
            val patientQuery = session.patientCode?.let { "&patient=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
            val encodedGraphSince = URLEncoder.encode(graphSinceIso, "UTF-8")
            val encodedEnd = URLEncoder.encode(endIso, "UTF-8")

            val readings = mutableListOf<GlookoReading>()
            val treatments = mutableListOf<TreatmentEntry>()
            var latestIobSnapshot: Pair<Long, Double>? = null
            var lastErrorCode = 0
            var hadAnySuccess = false

            val endpoints = listOf(
                Triple("/api/v2/cgm/egvs", "egvs", "egvs"),
                Triple("/api/v2/pumps/normal_boluses", "normalBoluses", "bolus"),
                Triple("/api/v2/pumps/extended_boluses", "extendedBoluses", "extended_bolus"),
                Triple("/api/v2/pumps/injection_boluses", "injectionBoluses", "bolus"),
                Triple("/api/v2/cgm/insulin_events", "insulinEvents", "insulin_events"),
                Triple("/api/v2/foods", "foods", "foods"),
                Triple("/api/v2/cgm/carbs_events", "carbsEvents", "foods"),
                Triple("/api/v2/readings", "readings", "readings"),
            )

            for ((path, propertyKey, kind) in endpoints) {
                val (code, hitPageCap, rows) = fetchPaginatedRows(
                    base = base,
                    path = path,
                    propertyKey = propertyKey,
                    initialUpdatedAtIso = sinceIso,
                    patientQuery = patientQuery,
                    session = session,
                    maxPages = 15,
                )
                val finalRows = if (hitPageCap && recentSince > clampedSince) {
                    val (_, _, recentRows) = fetchPaginatedRows(
                        base = base,
                        path = path,
                        propertyKey = propertyKey,
                        initialUpdatedAtIso = recentSinceIso,
                        patientQuery = patientQuery,
                        session = session,
                        maxPages = 8,
                    )
                    mergeUniqueRows(rows, recentRows)
                } else {
                    rows
                }

                if (code in 200..299) {
                    hadAnySuccess = true
                    if (finalRows.isNotEmpty()) {
                        when (kind) {
                            "egvs" -> parseEgvsRows(finalRows, futureCutoffMs, readings)
                            "bolus" -> {
                                val snap = parseBolusRows(finalRows, extended = false, futureCutoffMs = futureCutoffMs, treatmentsOut = treatments)
                                if (snap != null && (latestIobSnapshot == null || snap.first > latestIobSnapshot!!.first)) {
                                    latestIobSnapshot = snap
                                }
                            }
                            "extended_bolus" -> parseBolusRows(finalRows, extended = true, futureCutoffMs = futureCutoffMs, treatmentsOut = treatments)
                            "insulin_events" -> parseInsulinEventRows(finalRows, futureCutoffMs, treatments)
                            "foods" -> parseFoodRows(finalRows, futureCutoffMs, treatments)
                            "readings" -> parseV2ReadingRows(finalRows, futureCutoffMs, readings)
                        }
                    }
                } else if (code != 404 && code != 422 && code != 0) {
                    lastErrorCode = code
                }
            }

            // Pump settings profile (DIA, ISF, target range, basal schedule)
            val profileUrl = "$base/api/v2/external/pumps/settings?lastUpdatedAt=${URLEncoder.encode(sinceIso, "UTF-8")}&limit=20&lastGuid=$NULL_GUID$patientQuery"
            val (profCode, profBody) = apiGet(profileUrl, session)
            val parsedProfile = if (profCode in 200..299 && profBody.isNotBlank()) {
                parsePumpProfile(profBody, nowMs)
            } else {
                null
            }

            // v3 Graph data (recent 48h fallback + CamAPS FX deliveredBolus / gkCarb)
            val patientParam = session.patientCode?.let { "patient=${URLEncoder.encode(it, "UTF-8")}&" }.orEmpty()
            val graphSeries = listOf(
                "cgmHigh", "cgmNormal", "cgmLow",
                "bgHigh", "bgNormal", "bgLow",
                "deliveredBolus", "gkCarb", "carbNonManual", "gkInsulinBolus",
            ).joinToString("&") { "series[]=$it" }
            val graphUrl = "$base/api/v3/graph/data?${patientParam}startDate=$encodedGraphSince&endDate=$encodedEnd" +
                "&$graphSeries&locale=en-GB&insulinTooltips=true&filterBgReadings=false&splitByDay=false"
            val (graphCode, graphBody) = apiGet(graphUrl, session)
            if (graphCode in 200..299 && graphBody.isNotBlank()) {
                hadAnySuccess = true
                val graphParsed = parseGraphDataJson(graphBody, futureCutoffMs)
                if (readings.none { it.timestampMs >= recentSince }) {
                    readings += graphParsed.readings
                }
                for (t in graphParsed.treatments) {
                    treatments += t
                }
            } else if (!hadAnySuccess && lastErrorCode == 0) {
                lastErrorCode = graphCode
            }

            if (!hadAnySuccess && readings.isEmpty() && treatments.isEmpty()) {
                error("Glooko Login OK, aber Datenabruf fehlgeschlagen (HTTP ${if (graphCode !in 200..299) graphCode else lastErrorCode}).")
            }

            GlookoPayload(
                readings = readings
                    .filter { it.mgdl in 20.0..600.0 && it.timestampMs > 0L }
                    .distinctBy { it.timestampMs / 60_000L }
                    .sortedBy { it.timestampMs },
                treatments = deduplicateTreatments(treatments).sortedBy { it.timestampMs },
                glookoCode = session.patientCode,
                basalHourlyRates = parsedProfile?.basalHourlyRates,
                profileDiaMinutes = parsedProfile?.diaMinutes,
                profileIsfMgdl = parsedProfile?.currentIsfMgdl,
                targetLowMgdl = parsedProfile?.targetLowMgdl,
                targetHighMgdl = parsedProfile?.targetHighMgdl,
                iobSnapshotUnits = latestIobSnapshot?.second,
                iobSnapshotTimestampMs = latestIobSnapshot?.first,
            )
        }
    }

    private fun authenticate(email: String, pass: String, region: GlookoRegion): Triple<Boolean, String, GlookoSession?> {
        var lastError = "Glooko Login fehlgeschlagen"
        for ((webOrigin, candidateApi) in clusterCandidates(region)) {
            val webAttempt = authenticateWebSession(email, pass, webOrigin, candidateApi)
            if (webAttempt.first && webAttempt.third != null) {
                return webAttempt
            }
            if (webAttempt.second.contains("2FA", ignoreCase = true) ||
                webAttempt.second.contains("CAPTCHA", ignoreCase = true)
            ) {
                return webAttempt
            }
            lastError = webAttempt.second

            val apiAttempt = authenticateJsonApi(email, pass, webOrigin, candidateApi)
            if (apiAttempt.first && apiAttempt.third != null) {
                return apiAttempt
            }
            if (apiAttempt.second.contains("2FA", ignoreCase = true)) {
                return apiAttempt
            }
        }
        return Triple(false, lastError, null)
    }

    private fun authenticateWebSession(
        email: String,
        pass: String,
        initialWebOrigin: String,
        fallbackApiBase: String,
    ): Triple<Boolean, String, GlookoSession?> {
        return try {
            val cookieJar = linkedMapOf<String, String>()
            val startUrl = "$initialWebOrigin/users/sign_in?id=login_form&locale=en-GB"

            val (getCode, finalLoginUrl, html) = executeWithRedirects("GET", startUrl, null, null, cookieJar)
            if (html.contains("captcha", ignoreCase = true) || html.contains("cf-turnstile", ignoreCase = true) || getCode == 403) {
                return Triple(false, "Glooko Web-Login durch Captcha blockiert (HTTP $getCode). Nutze ggf. ZIP/CSV-Import.", null)
            }

            val token = extractCsrfToken(html)
                ?: return Triple(false, "Glooko CSRF-Token nicht gefunden (HTTP $getCode)", null)

            val loginOrigin = originOf(finalLoginUrl) ?: initialWebOrigin
            val postUrl = "$loginOrigin/users/sign_in?id=login_form&locale=en-GB"
            val formBody = buildString {
                append("utf8=").append(URLEncoder.encode("✓", "UTF-8"))
                append("&authenticity_token=").append(URLEncoder.encode(token, "UTF-8"))
                append("&user[email]=").append(URLEncoder.encode(email.trim(), "UTF-8"))
                append("&user[password]=").append(URLEncoder.encode(pass, "UTF-8"))
                append("&redirect_to=").append(URLEncoder.encode("/", "UTF-8"))
                append("&commit=").append(URLEncoder.encode("Log in", "UTF-8"))
            }

            val postHeaders = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Origin" to loginOrigin,
                "Referer" to finalLoginUrl,
            )
            val (postCode, dashboardUrl, dashboardHtml) = executeWithRedirects("POST", postUrl, formBody, postHeaders, cookieJar)

            if (dashboardHtml.contains("twoFaRequired", ignoreCase = true) ||
                dashboardHtml.contains("two_fa", ignoreCase = true) ||
                dashboardUrl.contains("two_factor", ignoreCase = true)
            ) {
                return Triple(false, "Glooko 2FA ist aktiv – nutze alternativ den Glooko ZIP/CSV-Import.", null)
            }

            if (postCode in listOf(401, 422) || dashboardUrl.contains("sign_in")) {
                return Triple(false, "Glooko Login abgelehnt (HTTP $postCode – Zugangsdaten/Region prüfen)", null)
            }

            val finalOrigin = originOf(dashboardUrl) ?: loginOrigin
            val patientId = extractRegexGroup(dashboardHtml, """window\.patient\s*=\s*["']([^"']+)["']""")
            val extractedApi = extractRegexGroup(dashboardHtml, """apiUrl\s*:\s*["']([^"']+)["']""")
            val apiBase = extractedApi?.ifBlank { null } ?: deriveApiBase(finalOrigin, fallbackApiBase)
            val cookieHeader = formatCookieHeader(cookieJar)

            if (cookieHeader.isBlank()) {
                return Triple(false, "Glooko Login ohne gültiges Session-Cookie (HTTP $postCode)", null)
            }

            var resolvedPatient = patientId
            if (resolvedPatient.isNullOrBlank()) {
                resolvedPatient = fetchPatientCode(GlookoSession(finalOrigin, apiBase, cookieHeader, null))
            }
            if (resolvedPatient.isNullOrBlank()) {
                return Triple(false, "Glooko Login auf $finalOrigin ohne Patienten-ID (HTTP $postCode)", null)
            }

            Triple(true, "Verbunden mit $finalOrigin", GlookoSession(finalOrigin, apiBase, cookieHeader, resolvedPatient))
        } catch (e: Exception) {
            Triple(false, "Glooko Web-Fehler ($initialWebOrigin): ${e.message}", null)
        }
    }

    private fun authenticateJsonApi(
        email: String,
        pass: String,
        webOrigin: String,
        apiBase: String,
    ): Triple<Boolean, String, GlookoSession?> {
        val escEmail = escapeJson(email.trim())
        val escPass = escapeJson(pass)
        val payloads = listOf(
            "$apiBase/api/v3/users/sign_in" to """{"user":{"email":"$escEmail","password":"$escPass"}}""",
            signInUrlForBase(apiBase) to """{"userLogin":{"email":"$escEmail","password":"$escPass"},"deviceInformation":{"applicationType":"logbook","os":"android","osVersion":"34","device":"Android","deviceManufacturer":"Google","deviceModel":"Android","serialNumber":"xdripyou261118","clinicalResearch":false,"deviceId":"xdripyou-android","applicationVersion":"6.1.3","buildNumber":"0","gitHash":"g4fbed2011b"}}""",
        )

        for ((url, jsonBody) in payloads) {
            try {
                val cookieJar = linkedMapOf<String, String>()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                    setRequestProperty("Origin", webOrigin)
                    setRequestProperty("Referer", "$webOrigin/")
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = 7000
                    readTimeout = 8000
                    doOutput = true
                }
                OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }
                val code = conn.responseCode
                collectCookies(conn, cookieJar)
                val respBody = runCatching {
                    (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                if (code in 200..299) {
                    val obj = MiniJson.asObject(MiniJson.parse(respBody))
                    if (obj != null && (MiniJson.optBoolean(obj, "twoFaRequired") || MiniJson.optBoolean(obj, "twoFactorRequired"))) {
                        return Triple(false, "Glooko 2FA ist aktiv – nutze alternativ den Glooko ZIP/CSV-Import.", null)
                    }
                    val cookieHeader = formatCookieHeader(cookieJar)
                    val patientFromJson = obj?.let { extractGlookoCodeFromMap(it) }
                    if (cookieHeader.isNotBlank()) {
                        val baseSession = GlookoSession(webOrigin, apiBase, cookieHeader, patientFromJson)
                        val patient = patientFromJson ?: fetchPatientCode(baseSession)
                        if (!patient.isNullOrBlank()) {
                            return Triple(true, "Verbunden ($apiBase)", baseSession.copy(patientCode = patient))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return Triple(false, "Glooko API-Login fehlgeschlagen", null)
    }

    private fun signInUrlForBase(apiBase: String): String = "$apiBase/api/v2/users/sign_in"

    private fun fetchPatientCode(session: GlookoSession): String? {
        for (path in listOf("/api/v3/session/users", "/api/v2/users/current")) {
            val (code, body) = apiGet("${session.apiBase}$path", session)
            if (code in 200..299 && body.isNotBlank()) {
                extractGlookoCode(body)?.let { return it }
            }
        }
        return null
    }

    private fun fetchPaginatedRows(
        base: String,
        path: String,
        propertyKey: String,
        initialUpdatedAtIso: String,
        patientQuery: String,
        session: GlookoSession,
        maxPages: Int = 15,
    ): Triple<Int, Boolean, List<Map<String, Any?>>> {
        val merged = mutableListOf<Map<String, Any?>>()
        val seenKeys = hashSetOf<String>()
        val seenCursors = hashSetOf<String>()
        var cursorUpdatedAt = initialUpdatedAtIso
        var cursorGuid = NULL_GUID
        var lastHttpCode = 0
        var hitPageCap = false

        for (page in 0 until maxPages) {
            val encodedUpdated = URLEncoder.encode(cursorUpdatedAt, "UTF-8")
            val encodedGuid = URLEncoder.encode(cursorGuid, "UTF-8")
            val url = "$base$path?lastUpdatedAt=$encodedUpdated&limit=500&lastGuid=$encodedGuid$patientQuery"
            val (code, body) = apiGet(url, session)
            lastHttpCode = code
            if (code !in 200..299 || body.isBlank()) {
                return Triple(code, false, merged)
            }
            val parsed = MiniJson.parse(body)
            val rootObj = MiniJson.asObject(parsed)
            val batch = if (rootObj != null) {
                extractFirstObjArray(rootObj, propertyKey, "data")
            } else {
                MiniJson.asArray(parsed)?.mapNotNull { MiniJson.asObject(it) } ?: emptyList()
            }
            if (batch.isEmpty()) return Triple(code, false, merged)

            for (obj in batch) {
                val key = rowKey(obj)
                if (seenKeys.add(key)) merged += obj
            }

            val isLastPage = rootObj?.let { MiniJson.optBoolean(it, "lastPage", batch.size < 500) } ?: true
            if (isLastPage) return Triple(code, false, merged)

            val nextUpdated = rootObj?.let { MiniJson.optString(it, "lastUpdatedAt") }?.takeIf { it.isNotBlank() }
            val nextGuid = rootObj?.let { MiniJson.optString(it, "lastGuid") }?.takeIf { it.isNotBlank() }
            if (nextUpdated == null || nextGuid == null) return Triple(code, false, merged)

            val cursorKey = "$nextUpdated|$nextGuid"
            if (!seenCursors.add(cursorKey) || (nextUpdated == cursorUpdatedAt && nextGuid == cursorGuid)) {
                return Triple(code, false, merged)
            }
            cursorUpdatedAt = nextUpdated
            cursorGuid = nextGuid
            if (page == maxPages - 1) hitPageCap = true
        }
        return Triple(lastHttpCode, hitPageCap, merged)
    }

    private fun mergeUniqueRows(
        first: List<Map<String, Any?>>,
        second: List<Map<String, Any?>>,
    ): List<Map<String, Any?>> {
        val merged = mutableListOf<Map<String, Any?>>()
        val seen = hashSetOf<String>()
        for (list in listOf(first, second)) {
            for (obj in list) {
                if (seen.add(rowKey(obj))) merged += obj
            }
        }
        return merged
    }

    private fun rowKey(obj: Map<String, Any?>): String {
        val guid = MiniJson.optString(obj, "guid")
        if (!guid.isNullOrBlank()) return guid
        val v = MiniJson.optDouble(obj, "rate")
            ?: MiniJson.optDouble(obj, "insulinDelivered")
            ?: MiniJson.optDouble(obj, "glucoseValue")
            ?: MiniJson.optDouble(obj, "value")
            ?: 0.0
        return "${parseAnyTimestamp(obj, false)}_$v"
    }

    internal fun extractGlookoCode(jsonText: String): String? {
        val parsed = MiniJson.parse(jsonText) ?: return null
        val obj = MiniJson.asObject(parsed)
            ?: MiniJson.asArray(parsed)?.firstOrNull()?.let { MiniJson.asObject(it) }
            ?: return null
        return extractGlookoCodeFromMap(obj)
    }

    private fun extractGlookoCodeFromMap(obj: Map<String, Any?>): String? {
        val candidates = listOfNotNull(
            MiniJson.asObject(obj["currentUser"]),
            MiniJson.asObject(obj["currentPatient"]),
            MiniJson.asObject(obj["user"]),
            MiniJson.asObject(obj["userLogin"]),
            MiniJson.objArray(obj, "users").firstOrNull(),
            obj,
        )
        for (c in candidates) {
            MiniJson.optString(c, "glookoCode")?.takeIf { it.isNotBlank() }?.let { return it }
            MiniJson.optString(c, "patientId")?.takeIf { it.isNotBlank() }?.let { return it }
            MiniJson.optString(c, "id")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    fun parseGraphDataJson(
        rawJson: String,
        futureCutoffMs: Long = Long.MAX_VALUE,
    ): GlookoPayload {
        val root = MiniJson.asObject(MiniJson.parse(rawJson)) ?: return GlookoPayload()
        val series = MiniJson.asObject(root["series"]) ?: root
        val isMmol = (MiniJson.optString(root, "units") ?: "").contains("mmol", ignoreCase = true)

        val readings = mutableListOf<GlookoReading>()
        val treatments = mutableListOf<TreatmentEntry>()

        for (k in listOf("cgmHigh", "cgmNormal", "cgmLow", "cgmReadings", "cgm", "readings")) {
            val rows = MiniJson.objArray(series, k)
            if (rows.isNotEmpty()) {
                parseGlucoseRows(rows, treatGraphXAsWallClock = true, futureCutoffMs = futureCutoffMs, isMmolDefault = isMmol, out = readings)
            }
        }

        for (k in listOf("deliveredBolus", "gkInsulinBolus", "bolus", "normalBolus", "manualInsulin")) {
            val rows = MiniJson.objArray(series, k)
            if (rows.isNotEmpty()) {
                parseBolusRows(rows, extended = false, futureCutoffMs = futureCutoffMs, treatmentsOut = treatments)
            }
        }

        for (k in listOf("gkCarb", "carbNonManual", "carbs", "food", "foods", "meals")) {
            val rows = MiniJson.objArray(series, k)
            if (rows.isNotEmpty()) {
                parseFoodRows(rows, futureCutoffMs = futureCutoffMs, out = treatments)
            }
        }

        for (k in listOf("bgHigh", "bgLow", "bgNormal", "bg")) {
            val rows = MiniJson.objArray(series, k)
            for (obj in rows) {
                if (!isRowActive(obj)) continue
                val ts = parseAnyTimestamp(obj, treatBareZAsWallClock = true)
                if (ts <= 0L || ts > futureCutoffMs) continue
                val raw = optValidDouble(obj, "y", "value", "bg") ?: continue
                val mgdl = normalizeBgValue(raw, obj, isMmol) ?: continue
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
        val parsed = MiniJson.parse(rawJson) ?: return emptyList()
        val rows = MiniJson.asObject(parsed)?.let {
            extractFirstObjArray(it, "egvs", "readings", "values", "cgmReadings", "data")
        } ?: MiniJson.asArray(parsed)?.mapNotNull { MiniJson.asObject(it) } ?: emptyList()
        val out = mutableListOf<GlookoReading>()
        parseEgvsRows(rows, Long.MAX_VALUE, out)
        return out
    }

    fun parseV2TreatmentsJson(rawJson: String, kind: String): List<TreatmentEntry> {
        val parsed = MiniJson.parse(rawJson) ?: return emptyList()
        val rows = MiniJson.asObject(parsed)?.let {
            extractFirstObjArray(it, "normalBoluses", "extendedBoluses", "injectionBoluses", "boluses", "foods", "carbsEvents", "carbs", "insulinEvents", "insulins", "data")
        } ?: MiniJson.asArray(parsed)?.mapNotNull { MiniJson.asObject(it) } ?: emptyList()
        val out = mutableListOf<TreatmentEntry>()
        when (kind) {
            "bolus" -> parseBolusRows(rows, extended = false, futureCutoffMs = Long.MAX_VALUE, treatmentsOut = out)
            "carbs" -> parseFoodRows(rows, futureCutoffMs = Long.MAX_VALUE, out = out)
        }
        return out
    }

    private fun parseEgvsRows(
        rows: List<Map<String, Any?>>,
        futureCutoffMs: Long,
        out: MutableList<GlookoReading>,
    ) {
        for (obj in rows) {
            if (!isRowActive(obj) || MiniJson.optBoolean(obj, "calculated")) continue
            val ts = parseAnyTimestamp(obj, treatBareZAsWallClock = false)
            if (ts <= 0L || ts > futureCutoffMs) continue
            val raw = optValidDouble(obj, "glucoseValue", "value", "sgv", "y", "mgdl", "reading") ?: continue
            val valMgDl = normalizeBgValue(raw, obj, defaultMmol = false) ?: continue
            val trendStr = MiniJson.optString(obj, "trendArrow") ?: MiniJson.optString(obj, "trend")
            out += GlookoReading(
                timestampMs = ts,
                mgdl = valMgDl,
                trend = mapTrend(trendStr),
                source = "Glooko",
            )
        }
    }

    private fun parseV2ReadingRows(
        rows: List<Map<String, Any?>>,
        futureCutoffMs: Long,
        out: MutableList<GlookoReading>,
    ) {
        parseGlucoseRows(rows, treatGraphXAsWallClock = true, futureCutoffMs = futureCutoffMs, isMmolDefault = false, out = out)
    }

    private fun parseGlucoseRows(
        rows: List<Map<String, Any?>>,
        treatGraphXAsWallClock: Boolean,
        futureCutoffMs: Long,
        isMmolDefault: Boolean,
        out: MutableList<GlookoReading>,
    ) {
        for (obj in rows) {
            if (!isRowActive(obj) || MiniJson.optBoolean(obj, "calculated")) continue
            val ts = parseAnyTimestamp(obj, treatBareZAsWallClock = treatGraphXAsWallClock)
            if (ts <= 0L || ts > futureCutoffMs) continue
            val raw = optValidDouble(obj, "value", "glucoseValue", "y", "mgdl", "reading") ?: continue
            val valMgDl = normalizeBgValue(raw, obj, isMmolDefault) ?: continue
            val trendStr = MiniJson.optString(obj, "trendArrow") ?: MiniJson.optString(obj, "trend")
            out += GlookoReading(
                timestampMs = ts,
                mgdl = valMgDl,
                trend = mapTrend(trendStr),
                source = "Glooko",
            )
        }
    }

    private fun parseBolusRows(
        rows: List<Map<String, Any?>>,
        extended: Boolean,
        futureCutoffMs: Long,
        treatmentsOut: MutableList<TreatmentEntry>,
    ): Pair<Long, Double>? {
        var latestIob: Pair<Long, Double>? = null
        for (obj in rows) {
            if (!isRowActive(obj)) continue
            val ts = parseAnyTimestamp(obj, treatBareZAsWallClock = false)
            if (ts <= 0L || ts > futureCutoffMs) continue

            val iobField = optValidDouble(obj, "insulinOnBoard")
            if (iobField != null && iobField >= 0.0 && (latestIob == null || ts >= latestIob.first)) {
                latestIob = ts to iobField
            }

            val units = optValidDouble(obj, "insulinDelivered", "deliveredUnits", "units", "y", "value", "insulin") ?: 0.0
            val carbs = optValidDouble(obj, "carbsInput", "carbs") ?: 0.0
            if (units > 0.0 || carbs > 0.0) {
                val label = MiniJson.optString(obj, "bolusType")
                    ?: MiniJson.optString(obj, "label")
                    ?: if (extended) "Verzögerter Bolus" else "Glooko"
                treatmentsOut += TreatmentEntry(
                    timestampMs = ts,
                    insulinUnits = units.coerceAtLeast(0.0),
                    carbsGrams = carbs.coerceAtLeast(0.0),
                    note = label.ifBlank { "Glooko" },
                    eventType = if (carbs > 0.0) "Meal Bolus" else "Correction Bolus",
                )
            }
        }
        return latestIob
    }

    private fun parseInsulinEventRows(
        rows: List<Map<String, Any?>>,
        futureCutoffMs: Long,
        out: MutableList<TreatmentEntry>,
    ) {
        for (obj in rows) {
            if (!isRowActive(obj)) continue
            val ts = parseAnyTimestamp(obj, treatBareZAsWallClock = false)
            if (ts <= 0L || ts > futureCutoffMs) continue
            val units = optValidDouble(obj, "units", "insulin", "value", "y") ?: continue
            if (units <= 0.0) continue
            val kind = (MiniJson.optString(obj, "insulinType") ?: MiniJson.optString(obj, "type")).orEmpty()
            // Avoid counting automated basal micro-boluses in xDrip Bolus IOB calculator
            if (kind.contains("basal", ignoreCase = true)) continue
            out += TreatmentEntry(
                timestampMs = ts,
                insulinUnits = units,
                note = "Glooko Bolus",
                eventType = "Correction Bolus",
            )
        }
    }

    private fun parseFoodRows(
        rows: List<Map<String, Any?>>,
        futureCutoffMs: Long,
        out: MutableList<TreatmentEntry>,
    ) {
        for (obj in rows) {
            if (!isRowActive(obj)) continue
            val ts = parseAnyTimestamp(obj, treatBareZAsWallClock = false)
            if (ts <= 0L || ts > futureCutoffMs) continue
            val grams = optValidDouble(obj, "carbs", "carbohydrateGrams", "grams", "value", "y") ?: continue
            if (grams <= 0.0) continue
            val label = (MiniJson.optString(obj, "name") ?: MiniJson.optString(obj, "label")).orEmpty().ifBlank { "Glooko" }
            out += TreatmentEntry(
                timestampMs = ts,
                carbsGrams = grams,
                note = label,
                eventType = "Meal Bolus",
            )
        }
    }

    private data class ParsedPumpProfile(
        val diaMinutes: Int?,
        val currentIsfMgdl: Double?,
        val targetLowMgdl: Double?,
        val targetHighMgdl: Double?,
        val basalHourlyRates: List<Double>?,
    )

    private fun parsePumpProfile(body: String, nowMs: Long): ParsedPumpProfile? {
        val root = MiniJson.asObject(MiniJson.parse(body)) ?: return null
        val rows = extractFirstObjArray(root, "settings", "pumpSettings", "data")
        if (rows.isEmpty()) return null

        var newestObj: Map<String, Any?>? = null
        var newestTs = Long.MIN_VALUE
        for (obj in rows) {
            if (!isRowActive(obj)) continue
            val ts = parseAnyTimestamp(obj, treatBareZAsWallClock = false)
            if (newestObj == null || ts >= newestTs) {
                newestTs = ts
                newestObj = obj
            }
        }
        val row = newestObj ?: return null

        val diaSec = MiniJson.optDouble(row, "active_insulin_time")
        val diaMinutes = diaSec?.takeIf { it > 0.0 }?.let { (it / 60.0).toInt().coerceIn(120, 480) }

        val basalPrograms = MiniJson.objArray(row, "basal_settings")
        val selectedBasal = basalPrograms.firstOrNull { MiniJson.optBoolean(it, "is_current") } ?: basalPrograms.firstOrNull()
        val basalSegments = selectedBasal?.let { MiniJson.objArray(it, "segments") }.orEmpty()
        val basalRates = if (basalSegments.isNotEmpty()) {
            val secRates = basalSegments.mapNotNull { seg ->
                val start = MiniJson.optLong(seg, "start")?.toInt()
                val rate = MiniJson.optDouble(seg, "rate")
                if (start != null && start in 0..86399 && rate != null && rate >= 0.0) start to rate else null
            }.sortedBy { it.first }
            if (secRates.isNotEmpty()) {
                List(24) { hr ->
                    val sec = hr * 3600
                    (secRates.lastOrNull { it.first <= sec } ?: secRates.first()).second
                }
            } else {
                null
            }
        } else {
            null
        }

        val bolusPrograms = MiniJson.objArray(row, "bolus_settings")
        val selectedBolus = bolusPrograms.firstOrNull { MiniJson.optBoolean(it, "current") } ?: bolusPrograms.firstOrNull()

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val secOfDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)

        var currentIsf: Double? = null
        var bestIsfStart = -1
        for (seg in selectedBolus?.let { MiniJson.objArray(it, "isf_segments") }.orEmpty()) {
            val start = MiniJson.optLong(seg, "start")?.toInt() ?: -1
            val rawIsf = MiniJson.optDouble(seg, "insulin_sensitivity_factor") ?: continue
            if (start in 0..secOfDay && start >= bestIsfStart && rawIsf > 0.0) {
                bestIsfStart = start
                currentIsf = (if (rawIsf > 300.0) rawIsf / 100.0 else rawIsf).takeIf { it in 5.0..250.0 }
            }
        }

        var targetLow: Double? = null
        var targetHigh: Double? = null
        var bestTargetStart = -1
        for (seg in selectedBolus?.let { MiniJson.objArray(it, "target_bg_segments") }.orEmpty()) {
            val start = MiniJson.optLong(seg, "start")?.toInt() ?: -1
            if (start in 0..secOfDay && start >= bestTargetStart) {
                val lowRaw = optValidDouble(seg, "target_bg_low", "target_bg")
                val highRaw = optValidDouble(seg, "target_bg_high", "target_bg")
                val lowVal = lowRaw?.let { if (it > 500.0) it / 100.0 else it }
                val highVal = highRaw?.let { if (it > 500.0) it / 100.0 else it }
                if (lowVal != null && lowVal in 60.0..180.0 && highVal != null && highVal in 100.0..260.0) {
                    bestTargetStart = start
                    targetLow = lowVal
                    targetHigh = highVal
                }
            }
        }

        return ParsedPumpProfile(diaMinutes, currentIsf, targetLow, targetHigh, basalRates)
    }

    private fun parseAnyTimestamp(obj: Map<String, Any?>, treatBareZAsWallClock: Boolean): Long {
        val nowPlusTolerance = System.currentTimeMillis() + 180_000L

        for (k in listOf("systemTime", "utcTimestamp")) {
            val num = MiniJson.optLong(obj, k) ?: 0L
            if (num > 1_000_000_000_000L) return num
            if (num > 1_000_000_000L) return num * 1000L
            MiniJson.optString(obj, k)?.let { parseTrueUtcIso(it)?.let { ms -> return ms } }
        }

        for (k in listOf("timestamp", "x")) {
            val rawNum = obj[k] as? Number
            if (rawNum != null) {
                val num = rawNum.toLong()
                val rawMs = when {
                    num > 1_000_000_000_000L -> num
                    num > 1_000_000_000L -> num * 1000L
                    else -> 0L
                }
                if (rawMs > 0L) {
                    return if (k == "x" || treatBareZAsWallClock || rawMs > nowPlusTolerance) {
                        wallClockToTrueUtc(rawMs)
                    } else {
                        rawMs
                    }
                }
            }
        }

        MiniJson.optString(obj, "timestamp")?.trim()?.takeIf { it.isNotBlank() }?.let { iso ->
            if (hasExplicitNonZeroOffset(iso)) {
                runCatching { return OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            }
            parseTrueUtcIso(iso)?.let { rawMs ->
                return if (treatBareZAsWallClock || rawMs > nowPlusTolerance) {
                    wallClockToTrueUtc(rawMs)
                } else {
                    rawMs
                }
            }
        }

        for (k in listOf("pumpTimestamp", "pump_timestamp", "displayTime", "display_time", "date", "eventTime", "dateTime", "updatedAt", "createdAt")) {
            val iso = MiniJson.optString(obj, k)?.trim() ?: continue
            if (iso.isBlank()) continue
            if (hasExplicitNonZeroOffset(iso)) {
                runCatching { return OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            }
            parseTrueUtcIso(iso)?.let { rawMs ->
                return if (k == "updatedAt" || k == "createdAt") rawMs else wallClockToTrueUtc(rawMs)
            }
        }
        return 0L
    }

    private fun hasExplicitNonZeroOffset(iso: String): Boolean =
        Regex("""[+-]\d{2}:?\d{2}$""").containsMatchIn(iso)

    private fun parseTrueUtcIso(iso: String): Long? {
        if (iso.isBlank() || iso == "null") return null
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun wallClockToTrueUtc(wallUtcMs: Long): Long {
        val tz = TimeZone.getDefault()
        val off1 = tz.getOffset(wallUtcMs)
        var resolved = wallUtcMs - off1
        val off2 = tz.getOffset(resolved)
        if (off2 != off1) resolved = wallUtcMs - off2
        return resolved
    }

    private fun isRowActive(obj: Map<String, Any?>): Boolean =
        !MiniJson.optBoolean(obj, "softDeleted") &&
            !MiniJson.optBoolean(obj, "soft_deleted") &&
            !MiniJson.optBoolean(obj, "duplicate")

    private fun optValidDouble(obj: Map<String, Any?>, vararg keys: String): Double? {
        for (k in keys) {
            val d = MiniJson.optDouble(obj, k)
            if (d != null && !d.isNaN()) return d
        }
        return null
    }

    private fun normalizeBgValue(raw: Double, obj: Map<String, Any?>, defaultMmol: Boolean): Double? {
        val scaled = if (raw > 1000.0) raw / 100.0 else raw
        val unitStr = (MiniJson.optString(obj, "units") ?: MiniJson.optString(obj, "unit") ?: "").lowercase()
        val isMmol = unitStr.contains("mmol") || (defaultMmol && !unitStr.contains("mg"))
        val mgdl = if (isMmol || (!unitStr.contains("mg") && scaled in 1.0..33.0)) scaled * MGDL_PER_MMOL else scaled
        return mgdl.takeIf { it in 20.0..600.0 }
    }

    private fun mapTrend(raw: String?): TrendArrow = when (raw?.replace(" ", "")?.replace("_", "")?.replace("-", "")?.lowercase()) {
        "doubleup", "updouble", "2" -> TrendArrow.DOUBLE_UP
        "singleup", "up", "1" -> TrendArrow.SINGLE_UP
        "fortyfiveup", "up45", "slightlyup" -> TrendArrow.FORTY_FIVE_UP
        "flat", "stable", "0" -> TrendArrow.FLAT
        "fortyfivedown", "down45", "slightlydown" -> TrendArrow.FORTY_FIVE_DOWN
        "singledown", "down", "-1" -> TrendArrow.SINGLE_DOWN
        "doubledown", "downdouble", "-2" -> TrendArrow.DOUBLE_DOWN
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

    private fun extractFirstObjArray(root: Map<String, Any?>, vararg keys: String): List<Map<String, Any?>> {
        for (k in keys) {
            val rows = MiniJson.objArray(root, k)
            if (rows.isNotEmpty()) return rows
        }
        return emptyList()
    }

    private fun apiGet(urlStr: String, session: GlookoSession): Pair<Int, String> = try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Origin", session.webOrigin)
            setRequestProperty("Referer", "${session.webOrigin}/")
            setRequestProperty("Cookie", session.cookies)
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 8000
            readTimeout = 12000
        }
        val code = conn.responseCode
        val body = runCatching {
            (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        conn.disconnect()
        code to body
    } catch (_: Exception) {
        0 to ""
    }

    private fun executeWithRedirects(
        method: String,
        startUrl: String,
        body: String?,
        extraHeaders: Map<String, String>?,
        cookieJar: MutableMap<String, String>,
    ): Triple<Int, String, String> {
        var currentUrl = startUrl
        var currentMethod = method
        var currentBody = body
        var lastCode = 0
        var lastHtml = ""

        for (hop in 0 until 6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = currentMethod
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json")
                val cookieStr = formatCookieHeader(cookieJar)
                if (cookieStr.isNotBlank()) setRequestProperty("Cookie", cookieStr)
                extraHeaders?.forEach { (k, v) ->
                    if (hop == 0 || k.equals("Accept", true)) setRequestProperty(k, v)
                }
                connectTimeout = 9000
                readTimeout = 10000
                if (currentMethod == "POST" && currentBody != null) doOutput = true
            }
            if (currentMethod == "POST" && currentBody != null) {
                OutputStreamWriter(conn.outputStream).use { it.write(currentBody) }
            }
            lastCode = conn.responseCode
            collectCookies(conn, cookieJar)

            val location = conn.getHeaderField("Location")
            if (lastCode in 300..399 && !location.isNullOrBlank()) {
                runCatching { conn.disconnect() }
                currentUrl = URL(URL(currentUrl), location).toString()
                if (lastCode in listOf(301, 302, 303)) {
                    currentMethod = "GET"
                    currentBody = null
                }
                continue
            }

            lastHtml = runCatching {
                (if (lastCode in 200..399) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            conn.disconnect()
            break
        }
        return Triple(lastCode, currentUrl, lastHtml)
    }

    private fun collectCookies(conn: HttpURLConnection, cookieJar: MutableMap<String, String>) {
        val headers = conn.headerFields ?: return
        for ((key, values) in headers) {
            if (key != null && key.equals("Set-Cookie", ignoreCase = true) && values != null) {
                for (rawCookie in values) {
                    val pair = rawCookie.substringBefore(';').trim()
                    val eqIdx = pair.indexOf('=')
                    if (eqIdx > 0) {
                        val name = pair.substring(0, eqIdx).trim()
                        val value = pair.substring(eqIdx + 1).trim()
                        if (name.isNotBlank()) cookieJar[name] = value
                    }
                }
            }
        }
    }

    private fun formatCookieHeader(cookieJar: Map<String, String>): String =
        cookieJar.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun originOf(urlStr: String): String? = runCatching {
        val u = URL(urlStr)
        "${u.protocol}://${u.host}"
    }.getOrNull()

    private fun deriveApiBase(webOrigin: String, fallback: String): String = when {
        webOrigin.contains("://de-fr.my.") -> webOrigin.replace("://de-fr.my.", "://de-fr.api.")
        webOrigin.contains("://eu.my.") -> webOrigin.replace("://eu.my.", "://eu.api.")
        webOrigin.contains("://ca.my.") -> webOrigin.replace("://ca.my.", "://ca.api.")
        webOrigin.contains("://gb.my.") -> webOrigin.replace("://gb.my.", "://gb.api.")
        webOrigin.contains("://au.my.") -> webOrigin.replace("://au.my.", "://au.api.")
        webOrigin.contains("://my.") -> webOrigin.replace("://my.", "://api.")
        else -> fallback
    }

    private fun extractRegexGroup(text: String, regex: String): String? {
        val matcher = Pattern.compile(regex).matcher(text)
        return if (matcher.find()) matcher.group(1) else null
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
}
