package dev.xdripyou.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.data.InsertOutcome
import dev.xdripyou.app.domain.CamApsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CamApsNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            val app = application as XdripYouApplication
            if (!app.repository.settingsStore.current().collectorEnabled) return@launch
            val watched = app.repository.getWatchedPackages()
            try {
                activeNotifications?.forEach { sbn ->
                    if (watched.contains(sbn.packageName)) {
                        processStatusBarNotification(sbn)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        serviceScope.launch {
            val app = application as XdripYouApplication
            val settings = app.repository.settingsStore.current()
            if (!settings.collectorEnabled) return@launch
            val pkg = sbn.packageName ?: return@launch
            if (!app.repository.getWatchedPackages().contains(pkg)) return@launch

            if (!sbn.isOngoing && CamApsParser.defaultCamApsPackages.contains(pkg)) {
                app.repository.recordCollectorEvent(
                    packageName = pkg,
                    status = "ignored_transient",
                    rawTexts = sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                    parsedMgdl = null,
                    message = "Nicht-dauerhafte Benachrichtigung übersprungen",
                )
                return@launch
            }
            processStatusBarNotification(sbn)
        }
    }

    private suspend fun processStatusBarNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val pkg = sbn.packageName ?: "unknown"
        val app = application as XdripYouApplication
        val repo = app.repository
        val texts = extractTexts(notification)
        val rawSummary = texts.joinToString(" | ").take(300)

        if (texts.isEmpty()) {
            repo.recordCollectorEvent(
                packageName = pkg,
                status = "empty",
                rawTexts = "",
                parsedMgdl = null,
                message = "Keine sichtbaren Textfelder gefunden",
            )
            return
        }

        val unitMode = repo.settingsStore.current().sourceUnitMode
        val candidate = CamApsParser.tryExtractGlucose(texts, unitMode)
        if (candidate == null) {
            repo.recordCollectorEvent(
                packageName = pkg,
                status = "unparsed",
                rawTexts = rawSummary,
                parsedMgdl = null,
                message = "Kein eindeutiger Glukosewert (40–405 mg/dL) erkannt",
            )
            return
        }

        val timestamp = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()
        when (
            val outcome = repo.insertReading(
                mgdl = candidate.mgdl,
                timestampMs = timestamp,
                source = "CamAPS FX",
                rawText = candidate.rawText,
                applyDeduplication = true,
            )
        ) {
            is InsertOutcome.Inserted -> {
                repo.recordCollectorEvent(
                    packageName = pkg,
                    status = "accepted",
                    rawTexts = rawSummary,
                    parsedMgdl = candidate.mgdl,
                    message = "Wert übernommen (${candidate.detectedUnit})",
                )
                app.alertEngine.refreshOngoingNotification()
                app.alertEngine.evaluateOnNewReading(outcome.reading)
                val currentSettings = repo.settingsStore.current()
                if (currentSettings.nightscoutUploadEnabled &&
                    (!currentSettings.nightscoutWifiOnly || SystemPermissionsHelper.isOnWifi(this))
                ) {
                    repo.syncNightscout(SystemPermissionsHelper.getBatteryPercent(this))
                }
                if (currentSettings.healthConnectEnabled && currentSettings.healthConnectSend) {
                    dev.xdripyou.app.data.HealthConnectSyncManager.writeReadings(this, listOf(outcome.reading))
                }
            }
            is InsertOutcome.Deduplicated -> {
                repo.recordCollectorEvent(
                    packageName = pkg,
                    status = "deduplicated",
                    rawTexts = rawSummary,
                    parsedMgdl = candidate.mgdl,
                    message = "Duplikat innerhalb des Messintervalls übersprungen",
                )
            }
            is InsertOutcome.Jammed -> {
                repo.recordCollectorEvent(
                    packageName = pkg,
                    status = "jammed",
                    rawTexts = rawSummary,
                    parsedMgdl = candidate.mgdl,
                    message = "Wert ${outcome.valueMgdl.toInt()} mg/dL > 6x identisch (Jam-Schutz)",
                )
            }
        }
    }

    private fun extractTexts(notification: Notification): List<String> {
        val result = mutableListOf<String>()
        val contentView: RemoteViews? =
            @Suppress("DEPRECATION")
            notification.contentView ?: @Suppress("DEPRECATION") notification.bigContentView

        if (contentView != null) {
            try {
                val root = contentView.apply(this, null)?.rootView
                if (root is ViewGroup) {
                    collectTextViews(root, result)
                } else if (root is TextView && root.visibility == View.VISIBLE) {
                    root.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { result.add(it) }
                }
            } catch (_: Exception) {
            }
        }

        val extras = notification.extras
        if (extras != null) {
            listOf(
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
            ).forEach { key ->
                extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                    if (!result.contains(text)) {
                        result.add(text)
                    }
                }
            }
        }
        return result
    }

    private fun collectTextViews(group: ViewGroup, out: MutableList<String>) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                if (child is TextView) {
                    child.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
                } else if (child is ViewGroup) {
                    collectTextViews(child, out)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
