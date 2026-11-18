package dev.xdripyou.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.xdripyou.app.MainActivity
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.domain.GlucoseUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Port of xDrip's `AlwaysOnDisplayService` / Floating Glucose Bubble:
 * Displays a draggable Material-tinted floating pill showing live BG + trend over other apps
 * when `SYSTEM_ALERT_WINDOW` permission is granted.
 */
class FloatingOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var containerView: LinearLayout? = null
    private var valueView: TextView? = null
    private var metaView: TextView? = null
    private var refreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 220
        }

        val bg = GradientDrawable().apply {
            cornerRadius = 48f
            setColor(0xE61E242B.toInt())
            setStroke(2, 0xFF8AB4F8.toInt())
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(28, 14, 28, 14)
        }
        val tvVal = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            paint.isFakeBoldText = true
            text = "---"
        }
        val tvMeta = TextView(this).apply {
            textSize = 10.5f
            setTextColor(0xFFBDC1C6.toInt())
            text = "xDrip 2 You"
        }
        box.addView(tvVal)
        box.addView(tvMeta)

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        box.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager?.updateViewLayout(box, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )
                    }
                    true
                }
                else -> false
            }
        }

        containerView = box
        valueView = tvVal
        metaView = tvMeta
        windowManager?.addView(box, params)

        refreshJob = scope.launch {
            val app = application as XdripYouApplication
            while (isActive) {
                val settings = app.repository.settingsStore.current()
                val latest = app.repository.getLatestReadings(1).firstOrNull()
                if (latest == null) {
                    valueView?.text = "---"
                    metaView?.text = "Warte..."
                } else {
                    val valStr = GlucoseUnit.formatValue(latest.mgdl, settings.useMgdl)
                    valueView?.text = "$valStr ${latest.trend.symbol}"
                    val mins = latest.minutesAgoAt(System.currentTimeMillis())
                    metaView?.text = if (mins == 0L) "jetzt" else "vor ${mins}m"
                }
                delay(15_000L)
            }
        }
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        containerView?.let { v -> runCatching { windowManager?.removeView(v) } }
        containerView = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                context.startService(Intent(context, FloatingOverlayService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }
}
