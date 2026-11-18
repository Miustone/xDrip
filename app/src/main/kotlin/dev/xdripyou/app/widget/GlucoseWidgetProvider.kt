package dev.xdripyou.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.xdripyou.app.MainActivity
import dev.xdripyou.app.R
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.domain.GlucoseUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GlucoseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GlucoseWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgets(context, manager, ids)
            }
        }

        private suspend fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            val app = context.applicationContext as XdripYouApplication
            val settings = app.repository.settingsStore.current()
            val latest = app.repository.getLatestReadings(2)
            val current = latest.firstOrNull()
            val prev = latest.getOrNull(1)
            val nowMs = System.currentTimeMillis()

            val bgText: String
            val trendText: String
            val deltaText: String
            val subText: String

            if (current == null) {
                bgText = "---"
                trendText = "↔"
                deltaText = ""
                subText = "xDrip 2 You · Warte…"
            } else {
                bgText = GlucoseUnit.formatValue(current.mgdl, settings.useMgdl)
                trendText = current.trend.symbol
                deltaText = if (prev != null && (current.timestampMs - prev.timestampMs) <= 1_200_000L) {
                    GlucoseUnit.formatDelta(current.mgdl - prev.mgdl, settings.useMgdl)
                } else {
                    ""
                }
                val mins = current.minutesAgoAt(nowMs)
                val unit = GlucoseUnit.unitLabel(settings.useMgdl)
                subText = if (mins == 0L) "$unit · gerade eben" else "$unit · vor $mins Min."
            }

            val launchIntent = PendingIntent.getActivity(
                context,
                99,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_glucose).apply {
                    setTextViewText(R.id.widget_bg_value, bgText)
                    setTextViewText(R.id.widget_trend, trendText)
                    setTextViewText(R.id.widget_delta, deltaText)
                    setTextViewText(R.id.widget_subtitle, subText)
                    setOnClickPendingIntent(R.id.widget_root, launchIntent)
                }
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }
}
