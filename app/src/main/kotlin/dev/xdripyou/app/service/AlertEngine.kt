package dev.xdripyou.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import dev.xdripyou.app.MainActivity
import dev.xdripyou.app.R
import dev.xdripyou.app.data.GlucoseRepository
import dev.xdripyou.app.domain.ActiveAlertState
import dev.xdripyou.app.domain.AlertEvaluator
import dev.xdripyou.app.domain.AlertProfile
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.AppSettings
import dev.xdripyou.app.domain.GlucosePredictor
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.widget.GlucoseWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AlertEngine(
    private val context: Context,
    private val repository: GlucoseRepository,
    private val speechHelper: SpeechHelper,
) {
    private val mutex = Mutex()

    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousVolume: Int? = null
    private var appliedVolume: Int? = null
    private var appliedStream: Int? = null
    private var lastSensorExpiryNoticeMs: Long = 0L

    suspend fun buildOngoingNotification(): Notification {
        NotificationChannels.ensureAll(context)
        val settings = repository.settingsStore.current()
        val latest = repository.getLatestReadings(2)
        val current = latest.firstOrNull()
        val prev = latest.getOrNull(1)

        val title: String
        val body: String
        if (current == null) {
            title = "xDrip 2 You · Warte auf Daten"
            body = "Noch kein Messwert empfangen"
        } else {
            val formatted = GlucoseUnit.formatValue(current.mgdl, settings.useMgdl)
            val unitLabel = GlucoseUnit.unitLabel(settings.useMgdl)
            val minsAgo = current.minutesAgoAt(System.currentTimeMillis())
            val deltaSuffix = if (prev != null && (current.timestampMs - prev.timestampMs) <= 1_200_000L) {
                " (${GlucoseUnit.formatDelta(current.mgdl - prev.mgdl, settings.useMgdl)})"
            } else {
                ""
            }
            title = if (settings.compactOngoingNotification) {
                "$formatted ${current.trend.symbol}$deltaSuffix"
            } else {
                "$formatted $unitLabel ${current.trend.symbol}$deltaSuffix"
            }
            val baseBody = if (settings.compactOngoingNotification) {
                if (minsAgo == 0L) "jetzt" else "vor ${minsAgo}m"
            } else {
                if (minsAgo == 0L) "Gerade eben · ${current.source}" else "vor $minsAgo Min. · ${current.source}"
            }
            val iobCob = if (settings.showIobCob) repository.currentIobCob() else null
            val iobSuffix = buildList {
                if (iobCob != null && iobCob.hasActiveIob) {
                    add("IOB ${String.format(java.util.Locale.US, "%.2f", iobCob.iobUnits)} IE")
                }
                if (iobCob != null && iobCob.hasActiveCob) {
                    add("COB ${iobCob.cobGrams.roundToInt()} g")
                }
            }.joinToString(" · ")
            body = if (iobSuffix.isNotEmpty() && !settings.compactOngoingNotification) {
                "$baseBody · $iobSuffix"
            } else {
                baseBody
            }
        }

        val launchIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, NotificationChannels.ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    suspend fun refreshOngoingNotification() {
        GlucoseWidgetProvider.refreshAll(context)
        val settings = repository.settingsStore.current()
        if (!settings.foregroundServiceEnabled) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_ONGOING, buildOngoingNotification())
    }

    suspend fun evaluateOnNewReading(latest: GlucoseReading) = mutex.withLock {
        NotificationChannels.ensureAll(context)
        val settings = repository.settingsStore.current()
        val rules = repository.getAlertRules()
        val now = System.currentTimeMillis()

        if (settings.localBroadcastEnabled) {
            XdripBroadcastSender.sendReading(
                context = context,
                reading = latest,
                useMgdl = settings.useMgdl,
                targetPackage = settings.broadcastTargetPackage,
            )
        }

        if (settings.speakReadingsEnabled) {
            speechHelper.speakReading(
                reading = latest,
                useMgdl = settings.useMgdl,
                includeTrend = settings.speakTrendEnabled,
                speed = settings.speechSpeed.toFloat(),
            )
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_MISSED)

        if (settings.alertsDisabledUntilMs > now) {
            stopAudioLocked()
            return@withLock
        }

        if (settings.disableAlertsStaleData &&
            (now - latest.timestampMs) > settings.disableAlertsStaleMinutes * 60_000L
        ) {
            stopAudioLocked()
            return@withLock
        }

        val matched = AlertEvaluator.findHighestActiveAlert(
            mgdl = latest.mgdl,
            rules = rules,
            settings = settings,
            nowMs = now,
        )

        if (matched == null) {
            val active = repository.getActiveAlert()
            if (active != null && active.kind == "threshold") {
                if (!active.isSnoozed || now > active.nextAlertAtMs) {
                    stopAlertLocked(clearActive = true)
                }
            }
            evaluateSecondaryAlertsLocked(settings, latest, now)
            return@withLock
        }

        val active = repository.getActiveAlert()
        val recent = repository.getLatestReadings(4)

        if (active == null || active.kind != "threshold") {
            if (settings.smartAlerting && AlertEvaluator.isTrendingToAlertEnd(recent, matched.isHigh, now)) {
                return@withLock
            }
            if (settings.startAlertsSnoozed) {
                val nextAt = now + matched.minutesBetween.coerceAtLeast(1) * 60_000L
                repository.saveActiveAlert(
                    ActiveAlertState(
                        ruleUuid = matched.uuid,
                        ruleName = matched.name,
                        isHigh = matched.isHigh,
                        isSnoozed = true,
                        alertStartedAtMs = now,
                        lastAlertedAtMs = 0L,
                        nextAlertAtMs = nextAt,
                        triggerMgdl = latest.mgdl,
                        kind = "threshold",
                    )
                )
                return@withLock
            }
            triggerRuleAlertLocked(settings, matched, latest.mgdl, now, minutesAlerting = 0)
            return@withLock
        }

        if (active.ruleUuid == matched.uuid) {
            if (now >= active.nextAlertAtMs) {
                if (settings.smartSnoozing && AlertEvaluator.isTrendingToAlertEnd(recent, matched.isHigh, now)) {
                    return@withLock
                }
                val minsAlerting = if (active.isSnoozed) 0 else ((now - active.alertStartedAtMs) / 60_000L).toInt()
                triggerRuleAlertLocked(
                    settings = settings,
                    rule = matched,
                    mgdl = latest.mgdl,
                    now = now,
                    minutesAlerting = minsAlerting,
                    existingStartedAt = if (active.isSnoozed) now else active.alertStartedAtMs,
                )
            }
            return@withLock
        }

        val activeRule = rules.firstOrNull { it.uuid == active.ruleUuid }
        val snoozeOver = now >= active.nextAlertAtMs
        val shouldEscalate = snoozeOver || activeRule == null || AlertEvaluator.isHigherPriority(matched, activeRule)
        if (shouldEscalate) {
            stopAudioLocked()
            triggerRuleAlertLocked(settings, matched, latest.mgdl, now, minutesAlerting = 0)
        }
    }

    suspend fun evaluatePeriodicChecks() {
        val latest: GlucoseReading?
        val settings: AppSettings
        mutex.withLock {
            NotificationChannels.ensureAll(context)
            GlucoseWidgetProvider.refreshAll(context)
            settings = repository.settingsStore.current()
            if (settings.foregroundServiceEnabled) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID_ONGOING, buildOngoingNotification())
            }
            if (settings.alertsDisabledUntilMs > System.currentTimeMillis()) return
            latest = repository.getLatestReadings(1).firstOrNull()
        }

        val now = System.currentTimeMillis()
        val active = repository.getActiveAlert()
        if (active != null && active.kind == "threshold" && now >= active.nextAlertAtMs && latest != null) {
            if (now - latest.timestampMs <= 900_000L) {
                evaluateOnNewReading(latest)
            }
        }

        if (settings.missedReadingEnabled && latest != null) {
            val missedMins = settings.missedReadingMinutes.coerceAtLeast(5)
            val elapsedMs = now - latest.timestampMs
            if (elapsedMs >= missedMins * 60_000L && elapsedMs < 21_600_000L) {
                val cooldowns = repository.settingsStore.getAlertCooldowns()
                val snoozeMins = settings.missedReadingSnoozeMinutes.coerceAtLeast(5)
                if (now - cooldowns.lastMissedMs >= snoozeMins * 60_000L) {
                    repository.settingsStore.setLastMissedAlertMs(now)
                    postMissedReadingNotification((elapsedMs / 60_000L).toInt())
                }
            }
        }

        if (settings.sensorExpiryAlertEnabled && settings.sensorStartedAtMs > 0L) {
            val totalMs = settings.sensorExpectedDays * 86_400_000L
            val remainingMs = (settings.sensorStartedAtMs + totalMs) - now
            if (remainingMs <= 12 * 3_600_000L && now - lastSensorExpiryNoticeMs >= 6 * 3_600_000L) {
                lastSensorExpiryNoticeMs = now
                postSensorExpiryNotification((remainingMs / 3_600_000L).toInt())
            }
        }
    }

    private fun postSensorExpiryNotification(hoursRemaining: Int) {
        val body = if (hoursRemaining <= 0) {
            "Dein Sensor hat das geplante Laufzeitende erreicht."
        } else {
            "Dein Sensor läuft in ca. $hoursRemaining Std. ab."
        }
        val notif = NotificationCompat.Builder(context, NotificationChannels.MISSED_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sensor-Wechsel Erinnerung")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_SENSOR_EXPIRY, notif)
    }

    private suspend fun evaluateSecondaryAlertsLocked(
        settings: AppSettings,
        latest: GlucoseReading,
        now: Long,
    ) {
        val recent24 = repository.getLatestReadings(40)
        if (recent24.isEmpty()) return
        val cooldowns = repository.settingsStore.getAlertCooldowns()
        val formatted = GlucoseUnit.formatValue(latest.mgdl, settings.useMgdl)
        val unitLabel = GlucoseUnit.unitLabel(settings.useMgdl)

        // 1. Rapid rise / fall
        if (recent24.size >= 4 && now - cooldowns.lastRapidMs >= 900_000L) {
            if (settings.fallingAlertEnabled &&
                AlertEvaluator.isRapidRateTriggered(recent24, settings.fallingRateMgdlPerMin, drop = true, nowMs = now)
            ) {
                repository.settingsStore.setLastRapidAlertMs(now)
                postAlertNotification(
                    title = "Schneller Abfall · $formatted $unitLabel",
                    body = "Blutzucker fällt schneller als ${String.format(java.util.Locale.US, "%.1f", settings.fallingRateMgdlPerMin)} mg/dL/Min.",
                    overrideSilent = false,
                    vibrate = true,
                    volumeFrac = 0.7f,
                    defaultSnoozeMins = 20,
                )
                return
            }

            if (settings.risingAlertEnabled &&
                AlertEvaluator.isRapidRateTriggered(recent24, settings.risingRateMgdlPerMin, drop = false, nowMs = now)
            ) {
                repository.settingsStore.setLastRapidAlertMs(now)
                postAlertNotification(
                    title = "Schneller Anstieg · $formatted $unitLabel",
                    body = "Blutzucker steigt schneller als ${String.format(java.util.Locale.US, "%.1f", settings.risingRateMgdlPerMin)} mg/dL/Min.",
                    overrideSilent = false,
                    vibrate = true,
                    volumeFrac = 0.7f,
                    defaultSnoozeMins = 20,
                )
                return
            }
        }

        // 2. Low prediction alarm
        if (settings.lowPredictionEnabled &&
            settings.lowAlertsDisabledUntilMs <= now &&
            now - cooldowns.lastLowPredMs >= 900_000L &&
            latest.mgdl > settings.lowPredictionThresholdMgdl
        ) {
            val preds = GlucosePredictor.predict(recent24, nowMs = now)
            val minsUntil = GlucosePredictor.minutesUntilBelow(
                predictions = preds,
                baseTimestampMs = latest.timestampMs,
                thresholdMgdl = settings.lowPredictionThresholdMgdl,
                horizonMinutes = settings.lowPredictionHorizonMinutes,
            )
            if (minsUntil != null) {
                repository.settingsStore.setLastLowPredAlertMs(now)
                val threshStr = GlucoseUnit.formatValue(settings.lowPredictionThresholdMgdl, settings.useMgdl)
                postAlertNotification(
                    title = "Niedrig-Vorhersage · $formatted $unitLabel",
                    body = "Voraussichtlich unter $threshStr $unitLabel in ca. $minsUntil Min.",
                    overrideSilent = false,
                    vibrate = true,
                    volumeFrac = 0.75f,
                    defaultSnoozeMins = 20,
                )
                return
            }
        }

        // 2b. High prediction alarm
        if (settings.highPredictionEnabled &&
            settings.highAlertsDisabledUntilMs <= now &&
            now - cooldowns.lastHighPredMs >= 900_000L &&
            latest.mgdl < settings.highPredictionThresholdMgdl
        ) {
            val preds = GlucosePredictor.predict(recent24, nowMs = now)
            val minsUntil = GlucosePredictor.minutesUntilAbove(
                predictions = preds,
                baseTimestampMs = latest.timestampMs,
                thresholdMgdl = settings.highPredictionThresholdMgdl,
                horizonMinutes = settings.highPredictionHorizonMinutes,
            )
            if (minsUntil != null) {
                repository.settingsStore.setLastHighPredAlertMs(now)
                val threshStr = GlucoseUnit.formatValue(settings.highPredictionThresholdMgdl, settings.useMgdl)
                postAlertNotification(
                    title = "Hoch-Vorhersage · $formatted $unitLabel",
                    body = "Voraussichtlich über $threshStr $unitLabel in ca. $minsUntil Min.",
                    overrideSilent = false,
                    vibrate = true,
                    volumeFrac = 0.7f,
                    defaultSnoozeMins = 30,
                )
                return
            }
        }

        // 3. Persistent high alarm
        if (settings.persistentHighEnabled &&
            settings.highAlertsDisabledUntilMs <= now &&
            now - cooldowns.lastPersistentHighMs >= 1_800_000L &&
            AlertEvaluator.isPersistentHighTriggered(
                readingsDesc = recent24,
                thresholdMgdl = settings.persistentHighThresholdMgdl,
                durationMinutes = settings.persistentHighMinutes,
                nowMs = now,
            )
        ) {
            repository.settingsStore.setLastPersistentHighAlertMs(now)
            val threshStr = GlucoseUnit.formatValue(settings.persistentHighThresholdMgdl, settings.useMgdl)
            postAlertNotification(
                title = "Anhaltend Hoch · $formatted $unitLabel",
                body = "Seit über ${settings.persistentHighMinutes} Min. über $threshStr $unitLabel",
                overrideSilent = false,
                vibrate = true,
                volumeFrac = 0.7f,
                defaultSnoozeMins = 45,
            )
        }
    }

    private suspend fun triggerRuleAlertLocked(
        settings: AppSettings,
        rule: AlertRule,
        mgdl: Double,
        now: Long,
        minutesAlerting: Int,
        existingStartedAt: Long = now,
    ) {
        val effectiveProfile = if (rule.uuid == URGENT_LOW_UUID && settings.alertProfile == AlertProfile.VIBRATE) {
            AlertProfile.ASCENDING
        } else {
            settings.alertProfile
        }
        val effectiveMinutes = if (effectiveProfile == AlertProfile.ASCENDING) minutesAlerting else 5
        val minutesBetween = if (effectiveProfile == AlertProfile.ASCENDING) 1 else rule.minutesBetween.coerceAtLeast(1)
        val nextAlertAt = now + minutesBetween * 60_000L

        repository.saveActiveAlert(
            ActiveAlertState(
                ruleUuid = rule.uuid,
                ruleName = rule.name,
                isHigh = rule.isHigh,
                isSnoozed = false,
                alertStartedAtMs = existingStartedAt,
                lastAlertedAtMs = now,
                nextAlertAtMs = nextAlertAt,
                triggerMgdl = mgdl,
                kind = "threshold",
            )
        )

        val formatted = GlucoseUnit.formatValue(mgdl, settings.useMgdl)
        val unitLabel = GlucoseUnit.unitLabel(settings.useMgdl)
        val thresholdFormatted = GlucoseUnit.formatValue(rule.thresholdMgdl, settings.useMgdl)
        val directionLabel = if (rule.isHigh) "HOCH" else "NIEDRIG"
        val title = "$formatted $unitLabel · ${rule.name}"
        val body = "Blutzucker $directionLabel (Schwelle: $thresholdFormatted $unitLabel)"

        val rawAscending = if (settings.delayAscending3Min) {
            ((effectiveMinutes - 2) / 3.0f).coerceIn(0.0f, 1.0f)
        } else {
            ((effectiveMinutes * 0.1667f) + 0.3333f).coerceIn(0.0f, 1.0f)
        }
        val cappedAscending = if (settings.ascendingToMedium) min(rawAscending, 0.7f) else rawAscending

        val volumeFrac = when (effectiveProfile) {
            AlertProfile.HIGH -> 1.0f
            AlertProfile.MEDIUM -> 0.7f
            AlertProfile.ASCENDING -> cappedAscending
            AlertProfile.VIBRATE, AlertProfile.SILENT -> 0.0f
        }

        if (settings.wakePhoneDuringAlerts) {
            wakeScreenBriefly()
        }
        if (settings.flashTorchOnAlert) {
            pulseTorch()
        }
        if (settings.speakAlertsEnabled) {
            speechHelper.speakText(
                "$directionLabel, $formatted ${if (settings.useMgdl) "mg dL" else "mmol"}",
                settings.speechSpeed.toFloat(),
            )
        }

        postAlertNotification(
            title = title,
            body = body,
            overrideSilent = rule.overrideSilent,
            vibrate = rule.vibrate && effectiveProfile != AlertProfile.SILENT,
            volumeFrac = volumeFrac,
            defaultSnoozeMins = rule.defaultSnoozeMinutes,
            soundUri = rule.soundUri ?: settings.defaultAlertSoundUri.takeIf { it.isNotBlank() },
            noAlarmsDuringCalls = settings.noAlarmsDuringCalls,
            showButtons = settings.showButtonsInAlerts,
        )
    }

    suspend fun triggerTestAlert(ruleUuid: String? = null) = mutex.withLock {
        NotificationChannels.ensureAll(context)
        val settings = repository.settingsStore.current()
        val rules = repository.getAlertRules()
        val target = (ruleUuid?.let { id -> rules.firstOrNull { it.uuid == id } } ?: rules.firstOrNull())
            ?: AlertRule(
                uuid = "test-alert",
                name = "Test-Alarm",
                isHigh = false,
                thresholdMgdl = 70.0,
                enabled = true,
                overrideSilent = true,
            )

        val now = System.currentTimeMillis()
        repository.saveActiveAlert(
            ActiveAlertState(
                ruleUuid = target.uuid,
                ruleName = "${target.name} (Test)",
                isHigh = target.isHigh,
                isSnoozed = false,
                alertStartedAtMs = now,
                lastAlertedAtMs = now,
                nextAlertAtMs = now + target.minutesBetween.coerceAtLeast(1) * 60_000L,
                triggerMgdl = target.thresholdMgdl,
                kind = "threshold",
            )
        )
        val formatted = GlucoseUnit.formatValue(target.thresholdMgdl, settings.useMgdl)
        val unitLabel = GlucoseUnit.unitLabel(settings.useMgdl)
        postAlertNotification(
            title = "Test-Alarm · ${target.name}",
            body = "Simulierter Alarm ($formatted $unitLabel)",
            overrideSilent = target.overrideSilent,
            vibrate = target.vibrate,
            volumeFrac = 0.75f,
            defaultSnoozeMins = target.defaultSnoozeMinutes,
            soundUri = target.soundUri,
        )
    }

    suspend fun snoozeActiveAlert(minutes: Int) = mutex.withLock {
        stopAudioLocked()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_ALERT)
        val active = repository.getActiveAlert() ?: return@withLock
        val effectiveMins = if (minutes > 0) {
            minutes
        } else {
            repository.getAlertRule(active.ruleUuid)?.defaultSnoozeMinutes ?: 20
        }
        val nextAt = System.currentTimeMillis() + effectiveMins * 60_000L
        repository.saveActiveAlert(
            active.copy(
                isSnoozed = true,
                nextAlertAtMs = nextAt,
            )
        )
        if (repository.settingsStore.current().broadcastSnoozeEnabled) {
            XdripBroadcastReceiver.sendSnooze(context)
        }
    }

    suspend fun stopAlert(clearActive: Boolean) = mutex.withLock {
        stopAlertLocked(clearActive)
    }

    private suspend fun stopAlertLocked(clearActive: Boolean) {
        stopAudioLocked()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_ALERT)
        if (clearActive) {
            repository.clearActiveAlert()
        }
    }

    private fun postMissedReadingNotification(minsSince: Int) {
        val openIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, NotificationChannels.MISSED_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Keine neuen Messwerte")
            .setContentText("Letzter Messwert vor $minsSince Min. Prüfe CamAPS FX oder Datenquelle.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_MISSED, notif)
        playTone(volumeFrac = 0.6f, overrideSilent = false, customSoundUri = null)
        triggerVibration()
    }

    private fun postAlertNotification(
        title: String,
        body: String,
        overrideSilent: Boolean,
        vibrate: Boolean,
        volumeFrac: Float,
        defaultSnoozeMins: Int,
        soundUri: String? = null,
        noAlarmsDuringCalls: Boolean = true,
        showButtons: Boolean = true,
    ) {
        val openIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, AlertActionReceiver::class.java).apply {
                action = AlertActionReceiver.ACTION_SNOOZE
                putExtra(AlertActionReceiver.EXTRA_SNOOZE_MINUTES, defaultSnoozeMins)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getBroadcast(
            context,
            12,
            Intent(context, AlertActionReceiver::class.java).apply {
                action = AlertActionReceiver.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = if (overrideSilent) NotificationChannels.URGENT_ALERTS else NotificationChannels.BG_ALERTS
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setContentIntent(openIntent)

        if (showButtons) {
            builder
                .addAction(0, "Schlummern (${defaultSnoozeMins}m)", snoozeIntent)
                .addAction(0, "Stopp", stopIntent)
        }

        if (overrideSilent) {
            builder.setFullScreenIntent(openIntent, true)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_ALERT, builder.build())

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inCall = audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
        if (noAlarmsDuringCalls && inCall) {
            return
        }

        if (vibrate) {
            triggerVibration()
        }
        val isLoud = audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
        if (volumeFrac > 0.0f && (overrideSilent || isLoud)) {
            playTone(volumeFrac = volumeFrac, overrideSilent = overrideSilent, customSoundUri = soundUri)
        }
    }

    private fun playTone(volumeFrac: Float, overrideSilent: Boolean, customSoundUri: String?) {
        stopAudioLocked()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streamType = if (overrideSilent) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
        val usage = if (overrideSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION

        try {
            val maxVol = audioManager.getStreamMaxVolume(streamType)
            if (maxVol > 0) {
                val targetVol = (maxVol * volumeFrac).roundToInt().coerceIn(1, maxVol)
                val currVol = audioManager.getStreamVolume(streamType)
                if (overrideSilent || currVol != targetVol) {
                    previousVolume = currVol
                    appliedVolume = targetVol
                    appliedStream = streamType
                    try {
                        audioManager.setStreamVolume(streamType, targetVol, 0)
                    } catch (_: SecurityException) {
                    }
                }
            }

            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (!customSoundUri.isNullOrBlank()) {
                    setDataSource(context, Uri.parse(customSoundUri))
                } else {
                    val afd = context.resources.openRawResourceFd(R.raw.default_alert)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
                isLooping = false
                setOnCompletionListener { stopAudioLocked() }
                setOnErrorListener { _, _, _ ->
                    stopAudioLocked()
                    true
                }
                prepareAsync()
                setOnPreparedListener { mp -> mp.start() }
            }
            mediaPlayer = player
        } catch (_: Exception) {
            stopAudioLocked()
        }
    }

    private fun stopAudioLocked() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = appliedStream
        val prev = previousVolume
        val applied = appliedVolume
        if (stream != null && prev != null && applied != null) {
            try {
                if (audioManager.getStreamVolume(stream) == applied) {
                    audioManager.setStreamVolume(stream, prev, 0)
                }
            } catch (_: SecurityException) {
            }
        }
        previousVolume = null
        appliedVolume = null
        appliedStream = null

        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun triggerVibration() {
        val pattern = longArrayOf(0, 700, 250, 700, 250, 700)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {
        }
    }

    private fun wakeScreenBriefly() {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "xdrip2you:alert_wake",
            )
            wl.acquire(6_000L)
        }
    }

    private suspend fun pulseTorch() {
        runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            repeat(2) {
                cm.setTorchMode(cameraId, true)
                delay(220L)
                cm.setTorchMode(cameraId, false)
                delay(180L)
            }
        }
    }

    companion object {
        const val NOTIF_ID_ONGOING = 1001
        const val NOTIF_ID_ALERT = 1002
        const val NOTIF_ID_MISSED = 1003
        const val NOTIF_ID_SENSOR_EXPIRY = 1004
        private const val URGENT_LOW_UUID = "c5f1999c-4ec5-449e-adad-3980b172b920"
    }
}
