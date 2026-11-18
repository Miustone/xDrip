package dev.xdripyou.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val ONGOING = "xdrip_you_ongoing"
    const val BG_ALERTS = "xdrip_you_bg_alerts"
    const val URGENT_ALERTS = "xdrip_you_urgent_alerts"
    const val MISSED_ALERTS = "xdrip_you_missed_alerts"

    fun ensureAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val ongoing = NotificationChannel(
            ONGOING,
            "Laufende Blutzucker-Anzeige",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Dauerhafte Statusanzeige des aktuellen CamAPS-Werts"
            setShowBadge(false)
        }

        val bgAlerts = NotificationChannel(
            BG_ALERTS,
            "Blutzucker-Alarme",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Hoch-, Niedrig- und schnelle Trend-Alarme"
            enableVibration(true)
            setSound(null, null)
        }

        val urgentAlerts = NotificationChannel(
            URGENT_ALERTS,
            "Dringende Alarme (Lautlos umgehen)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Kritische Unterzuckerungs-Alarme"
            enableVibration(true)
            setBypassDnd(true)
            setSound(null, null)
        }

        val missedAlerts = NotificationChannel(
            MISSED_ALERTS,
            "Verpasste Messwerte",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Warnung wenn länger keine Daten von CamAPS FX eingehen"
            enableVibration(true)
            setSound(null, null)
        }

        nm.createNotificationChannel(ongoing)
        nm.createNotificationChannel(bgAlerts)
        nm.createNotificationChannel(urgentAlerts)
        nm.createNotificationChannel(missedAlerts)
    }
}
