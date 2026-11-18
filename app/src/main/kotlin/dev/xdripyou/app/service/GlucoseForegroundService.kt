package dev.xdripyou.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.xdripyou.app.XdripYouApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GlucoseForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null
    private var tickCount = 0

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAll(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as XdripYouApplication
        serviceScope.launch {
            val notif = app.alertEngine.buildOngoingNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    AlertEngine.NOTIF_ID_ONGOING,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(AlertEngine.NOTIF_ID_ONGOING, notif)
            }
        }

        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                tickCount++
                app.alertEngine.evaluatePeriodicChecks()
                if (tickCount % 5 == 0) {
                    val settings = app.repository.settingsStore.current()
                    val wifiOk = !settings.nightscoutWifiOnly || SystemPermissionsHelper.isOnWifi(this@GlucoseForegroundService)
                    if ((settings.nightscoutFollowEnabled || settings.nightscoutUploadEnabled) &&
                        settings.nightscoutUrl.isNotBlank() &&
                        wifiOk
                    ) {
                        val beforeLatest = app.repository.getLatestReadings(1).firstOrNull()?.timestampMs ?: 0L
                        app.repository.syncNightscout()
                        val afterLatest = app.repository.getLatestReadings(1).firstOrNull()
                        if (afterLatest != null && afterLatest.timestampMs > beforeLatest) {
                            app.alertEngine.refreshOngoingNotification()
                            app.alertEngine.evaluateOnNewReading(afterLatest)
                        }
                    }
                }
                if (tickCount % 60 == 0) {
                    app.repository.pruneExpiredReadings()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, GlucoseForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GlucoseForegroundService::class.java))
        }
    }
}
