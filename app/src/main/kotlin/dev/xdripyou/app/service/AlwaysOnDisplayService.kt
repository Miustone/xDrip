package dev.xdripyou.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.domain.GlucoseUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Port of xDrip's `AlwaysOnDisplayService`:
 * Uses `TYPE_ACCESSIBILITY_OVERLAY` (`WindowManager.LayoutParams`) on Android Accessibility Service
 * to render an OLED-friendly, burn-in-shifting live BG readout on the Lockscreen / Always-On Display
 * whenever the screen turns off (`ACTION_SCREEN_OFF`) and removes it cleanly on `ACTION_SCREEN_ON`.
 */
class AlwaysOnDisplayService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var bgView: TextView? = null
    private var metaView: TextView? = null
    private var screenOff = false
    private var burnInTick = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOff = true
                    refreshOverlay()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenOff = false
                    removeOverlay()
                }
                Intent.ACTION_TIME_TICK, ACTION_UPDATE_AOD -> {
                    if (screenOff) {
                        burnInTick++
                        refreshOverlay()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(ACTION_UPDATE_AOD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("RtlHardcoded")
    private fun refreshOverlay() {
        val app = application as? XdripYouApplication ?: return
        scope.launch {
            val settings = app.repository.settingsStore.current()
            if (!settings.aodEnabled || !screenOff) {
                removeOverlay()
                return@launch
            }
            val latest = app.repository.getLatestReadings(1).firstOrNull()
            val iob = if (settings.showIobCob) app.repository.currentIobCob() else null

            val alpha = ((settings.aodOpacityPercent.coerceIn(30, 100) * 255) / 100)
            val valText = if (latest == null) {
                "---"
            } else {
                "${GlucoseUnit.formatValue(latest.mgdl, settings.useMgdl)} ${latest.trend.symbol}"
            }
            val timeStr = latest?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestampMs))
            } ?: ""
            val metaText = buildList {
                if (timeStr.isNotEmpty()) add(timeStr)
                if (iob != null && iob.hasActiveIob) {
                    add("IOB ${String.format(Locale.US, "%.1f", iob.iobUnits)}")
                }
            }.joinToString(" · ")

            val shiftX = if (settings.aodBurnInShift) ((burnInTick % 5) - 2) * 28 else 0
            val shiftY = if (settings.aodBurnInShift) ((burnInTick % 4) * 32) else 40

            if (overlayView == null) {
                val bgDrawable = GradientDrawable().apply {
                    cornerRadius = 36f
                    setColor(Color.argb(170, 0, 0, 0))
                    setStroke(1, Color.argb(80, 160, 180, 210))
                }
                val box = LinearLayout(this@AlwaysOnDisplayService).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = bgDrawable
                    setPadding(36, 18, 36, 18)
                }
                val tvMain = TextView(this@AlwaysOnDisplayService).apply {
                    textSize = 26f
                    paint.isFakeBoldText = true
                }
                val tvSub = TextView(this@AlwaysOnDisplayService).apply {
                    textSize = 12f
                }
                box.addView(tvMain)
                box.addView(tvSub)
                overlayView = box
                bgView = tvMain
                metaView = tvSub

                val params = createParams(shiftX, 240 + shiftY)
                runCatching { windowManager?.addView(box, params) }
            } else {
                val params = createParams(shiftX, 240 + shiftY)
                runCatching { windowManager?.updateViewLayout(overlayView, params) }
            }

            bgView?.apply {
                text = valText
                setTextColor(Color.argb(alpha, 240, 245, 250))
            }
            metaView?.apply {
                text = metaText
                setTextColor(Color.argb((alpha * 0.7f).toInt(), 180, 192, 205))
            }
        }
    }

    private fun createParams(xOffset: Int, yOffset: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = xOffset
            y = yOffset
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "xDrip 2 You AOD"
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        bgView = null
        metaView = null
        runCatching { windowManager?.removeView(view) }
    }

    companion object {
        const val ACTION_UPDATE_AOD = "dev.xdripyou.app.ACTION_UPDATE_AOD"

        fun notifyUpdate(context: Context) {
            context.sendBroadcast(Intent(ACTION_UPDATE_AOD).setPackage(context.packageName))
        }
    }
}
