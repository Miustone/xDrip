package dev.xdripyou.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.data.InsertOutcome
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives standard xDrip local broadcasts (`BgEstimate` from Juggluco/AAPS/other collectors and
 * `Snooze` from companion apps or wearables) with sender loop-prevention.
 */
class XdripBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? XdripYouApplication ?: return
        val pendingResult = goAsync()

        scope.launch {
            try {
                val settings = app.repository.settingsStore.current()
                when (action) {
                    ACTION_SNOOZE -> {
                        val sender = intent.getStringExtra(XdripBroadcastSender.EXTRA_SENDER).orEmpty()
                        if (sender == SENDER_ID_SNOOZE || sender == SENDER_ID_BG) return@launch
                        if (!settings.acceptBroadcastSnooze) return@launch
                        app.alertEngine.snoozeActiveAlert(0)
                    }
                    XdripBroadcastSender.ACTION_NEW_BG_ESTIMATE -> {
                        val sender = intent.getStringExtra(XdripBroadcastSender.EXTRA_SENDER).orEmpty()
                        if (sender == SENDER_ID_BG) return@launch
                        if (!settings.acceptLocalBroadcastReadings) return@launch

                        val bgMgdl = intent.getDoubleExtra(XdripBroadcastSender.EXTRA_BG_ESTIMATE, Double.NaN)
                        if (bgMgdl.isNaN() || bgMgdl < 20.0 || bgMgdl > 600.0) return@launch
                        val timestamp = intent.getLongExtra(
                            XdripBroadcastSender.EXTRA_TIMESTAMP,
                            System.currentTimeMillis(),
                        ).takeIf { it > 0L } ?: System.currentTimeMillis()
                        val slopeName = intent.getStringExtra(XdripBroadcastSender.EXTRA_BG_SLOPE_NAME)
                        val overrideTrend = slopeName?.let { TrendArrow.fromWireName(it) }
                        val sourceLabel = sender.ifBlank { "Broadcast" }

                        val outcome = app.repository.insertReading(
                            mgdl = bgMgdl,
                            timestampMs = timestamp,
                            source = sourceLabel,
                            rawText = null,
                            applyDeduplication = true,
                            overrideTrend = overrideTrend,
                        )
                        if (outcome is InsertOutcome.Inserted) {
                            app.alertEngine.refreshOngoingNotification()
                            app.alertEngine.evaluateOnNewReading(outcome.reading)
                        }
                    }
                    ACTION_LOCALE_FIRE -> {
                        val bundle = intent.getBundleExtra(EXTRA_LOCALE_BUNDLE)
                        val cmd = bundle?.getString(EXTRA_TASKER_MESSAGE)
                            ?: intent.getStringExtra(EXTRA_TASKER_MESSAGE)
                        if (!cmd.isNullOrBlank()) {
                            TaskerCommandHandler.execute(app, cmd)
                        }
                    }
                    ACTION_TASKER_COMMAND -> {
                        val cmd = intent.getStringExtra("command")
                            ?: intent.getStringExtra(EXTRA_TASKER_MESSAGE)
                        if (!cmd.isNullOrBlank()) {
                            TaskerCommandHandler.execute(app, cmd)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.eveningoutpost.dexdrip.Snooze"
        const val ACTION_LOCALE_FIRE = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
        const val ACTION_TASKER_COMMAND = "dev.xdripyou.app.TASKER_COMMAND"
        const val EXTRA_LOCALE_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
        const val EXTRA_TASKER_MESSAGE = "com.eveningoutpost.dexdrip.localetasker.extra.STRING_MESSAGE"
        const val SENDER_ID_BG = "xDrip 2 You"
        const val SENDER_ID_SNOOZE = "dev.xdripyou.app"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun sendSnooze(context: Context) {
            val intent = Intent(ACTION_SNOOZE).apply {
                putExtra(XdripBroadcastSender.EXTRA_SENDER, SENDER_ID_SNOOZE)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        }

        const val ACTION_EXTERNAL_STATUS = "com.eveningoutpost.dexdrip.ExternalStatus"

        fun sendWearExternalStatus(
            context: Context,
            reading: dev.xdripyou.app.domain.GlucoseReading,
            iobUnits: Double,
            cobGrams: Double,
            useMgdl: Boolean,
        ) {
            val formattedBg = dev.xdripyou.app.domain.GlucoseUnit.formatValue(reading.mgdl, useMgdl)
            val statusLine = buildString {
                append("$formattedBg ${reading.trend.symbol}")
                if (iobUnits >= 0.05) append(" · IOB ${String.format(java.util.Locale.US, "%.2f", iobUnits)}U")
                if (cobGrams >= 1.0) append(" · COB ${cobGrams.toInt()}g")
            }
            val intent = Intent(ACTION_EXTERNAL_STATUS).apply {
                putExtra("com.eveningoutpost.dexdrip.Extras.Statusline", statusLine)
                putExtra("com.eveningoutpost.dexdrip.Extras.BgEstimate", reading.mgdl)
                putExtra("com.eveningoutpost.dexdrip.Extras.Time", reading.timestampMs)
                putExtra("com.eveningoutpost.dexdrip.Extras.Iob", iobUnits)
                putExtra("com.eveningoutpost.dexdrip.Extras.Cob", cobGrams)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        }
    }
}
