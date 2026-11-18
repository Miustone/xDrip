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
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.eveningoutpost.dexdrip.Snooze"
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
    }
}
