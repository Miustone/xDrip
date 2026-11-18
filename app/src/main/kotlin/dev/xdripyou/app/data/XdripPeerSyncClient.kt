package dev.xdripyou.app.data

import dev.xdripyou.app.domain.TreatmentEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class PeerSyncBundle(
    val entries: List<NightscoutEntryDto>,
    val treatments: List<TreatmentEntry>,
)

/**
 * Direct xDrip-to-xDrip peer follower client (complementing xDrip's `DesertSync` / `XdripWebService`
 * master-follower architecture) pulling `/sgv.json` and `/treatments.json` from a master device on port 17580.
 */
object XdripPeerSyncClient {

    fun normalizePeerUrl(rawHostOrUrl: String): String {
        var trimmed = rawHostOrUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        val afterScheme = trimmed.substringAfter("://")
        if (!afterScheme.contains(':')) {
            trimmed = "$trimmed:17580"
        }
        return trimmed
    }

    suspend fun fetchFromMaster(
        masterHostOrUrl: String,
        apiSecret: String,
        count: Int = 48,
    ): Result<PeerSyncBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizePeerUrl(masterHostOrUrl)
            require(base.isNotBlank()) { "Master-Adresse fehlt" }
            val sgvJson = getUrl("$base/sgv.json?count=$count", apiSecret)
            val entries = NightscoutClient.parseEntriesJson(sgvJson)
            val treatmentsJson = runCatching { getUrl("$base/treatments.json?count=36", apiSecret) }.getOrNull()
            val treatments = if (!treatmentsJson.isNullOrBlank()) {
                runCatching { NightscoutClient.parseTreatmentsJson(treatmentsJson) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            PeerSyncBundle(entries = entries, treatments = treatments)
        }
    }

    private fun getUrl(urlStr: String, apiSecret: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            if (apiSecret.isNotBlank()) {
                setRequestProperty("api-secret", NightscoutClient.sha1Hex(apiSecret.trim()))
            }
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) error("xDrip Peer HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
