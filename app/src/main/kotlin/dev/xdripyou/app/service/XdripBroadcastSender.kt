package dev.xdripyou.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.xdripyou.app.domain.GlucoseReading

/**
 * Emits standard xDrip-compatible local broadcasts (`com.eveningoutpost.dexdrip.BgEstimate`)
 * for interoperability with AndroidAPS (AAPS), watchfaces, and Tasker.
 */
object XdripBroadcastSender {
    const val ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"
    const val EXTRA_SENDER = "com.eveningoutpost.dexdrip.Extras.Sender"
    const val EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
    const val EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
    const val EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
    const val EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time"
    const val EXTRA_DISPLAY_UNITS = "com.eveningoutpost.dexdrip.Extras.Display.Units"

    fun sendReading(context: Context, reading: GlucoseReading, useMgdl: Boolean) {
        val bundle = Bundle().apply {
            putString(EXTRA_SENDER, "xDrip 2 You")
            putDouble(EXTRA_BG_ESTIMATE, reading.mgdl)
            // xDrip expects slope in mg/dL per millisecond
            putDouble(EXTRA_BG_SLOPE, reading.slopePerMin / 60_000.0)
            putString(EXTRA_BG_SLOPE_NAME, reading.trend.wireName)
            putLong(EXTRA_TIMESTAMP, reading.timestampMs)
            putString(EXTRA_DISPLAY_UNITS, if (useMgdl) "mg/dl" else "mmol")
        }
        val intent = Intent(ACTION_NEW_BG_ESTIMATE).apply {
            putExtras(bundle)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        context.sendBroadcast(intent)
    }
}
