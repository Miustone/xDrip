package dev.xdripyou.app.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import dev.xdripyou.app.domain.AppSettings
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Port of xDrip's `LockScreenWallPaper` + `NumberGraphic` ("Zahlenwand auf dem Sperrbildschirm"):
 * Renders a crisp high-contrast or tiled lockscreen wallpaper bitmap containing live BG,
 * trend arrow, timestamp and optional IOB/steps and applies it strictly to `WallpaperManager.FLAG_LOCK`.
 */
object LockScreenNumberWall {

    suspend fun refreshIfEnabled(
        context: Context,
        latest: GlucoseReading?,
        settings: AppSettings,
        iobUnits: Double = 0.0,
        todaySteps: Int = 0,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!settings.numberWallEnabled) {
                return@runCatching "Zahlenwand deaktiviert"
            }
            val wm = WallpaperManager.getInstance(context.applicationContext)
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels.coerceIn(720, 1440)
            val height = metrics.heightPixels.coerceIn(1280, 3200)

            val bitmap = if (latest == null) {
                renderDarkCleanBitmap(width, height, "Warte auf Messwert", settings)
            } else {
                renderLockscreenBitmap(
                    width = width,
                    height = height,
                    reading = latest,
                    settings = settings,
                    iobUnits = iobUnits,
                    todaySteps = todaySteps,
                )
            }
            try {
                wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK)
            } finally {
                bitmap.recycle()
            }
            "Sperrbildschirm-Zahlenwand aktualisiert"
        }
    }

    suspend fun clearLockscreenWallpaper(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val wm = WallpaperManager.getInstance(context.applicationContext)
            val metrics = context.resources.displayMetrics
            val bmp = Bitmap.createBitmap(
                metrics.widthPixels.coerceIn(720, 1440),
                metrics.heightPixels.coerceIn(1280, 3200),
                Bitmap.Config.ARGB_8888,
            )
            bmp.eraseColor(Color.BLACK)
            try {
                wm.setBitmap(bmp, null, false, WallpaperManager.FLAG_LOCK)
            } finally {
                bmp.recycle()
            }
            "Sperrbildschirm zurückgesetzt"
        }
    }

    private fun renderDarkCleanBitmap(width: Int, height: Int, label: String, settings: AppSettings): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(10, 12, 16))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((settings.numberWallOpacity.coerceIn(30, 100) * 255) / 100, 220, 226, 234)
            textSize = width * 0.055f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, width / 2f, height * 0.56f, paint)
        return bmp
    }

    private fun renderLockscreenBitmap(
        width: Int,
        height: Int,
        reading: GlucoseReading,
        settings: AppSettings,
        iobUnits: Double,
        todaySteps: Int,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(9, 11, 16))

        val alpha = ((settings.numberWallOpacity.coerceIn(30, 100) * 255) / 100).coerceIn(70, 255)
        val baseColor = if (settings.numberWallDynamicColor) {
            when {
                reading.mgdl < settings.targetLowMgdl -> Color.rgb(255, 107, 107)
                reading.mgdl > settings.targetHighMgdl -> Color.rgb(255, 184, 77)
                else -> Color.rgb(110, 231, 183)
            }
        } else {
            Color.WHITE
        }
        val paintColor = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        val bgValueStr = "${GlucoseUnit.formatValue(reading.mgdl, settings.useMgdl)} ${reading.trend.symbol}"
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reading.timestampMs))

        if (settings.numberWallTiled) {
            val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = paintColor
                textSize = width * 0.082f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((alpha * 0.6f).roundToInt(), 210, 218, 228)
                textSize = width * 0.032f
                textAlign = Paint.Align.CENTER
            }
            canvas.save()
            canvas.rotate(-14f, width / 2f, height / 2f)
            val cols = 3
            val rows = 6
            val stepX = width / 2.6f
            val stepY = height / 6.2f
            for (r in 0..rows) {
                for (c in -1..cols) {
                    val offsetX = if (r % 2 == 0) 0f else stepX * 0.5f
                    val x = c * stepX + offsetX
                    val y = height * 0.16f + r * stepY
                    canvas.drawText(bgValueStr, x, y, tilePaint)
                    canvas.drawText(timeStr, x, y + width * 0.042f, subPaint)
                }
            }
            canvas.restore()
        } else {
            // Centered clean card in the middle-lower safe area below typical Android clock
            val cardW = width * 0.78f
            val cardH = height * 0.21f
            val left = (width - cardW) / 2f
            val top = height * 0.52f
            val rect = RectF(left, top, left + cardW, top + cardH)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 22, 27, 36)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.argb(120, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            }
            canvas.drawRoundRect(rect, 44f, 44f, cardPaint)
            canvas.drawRoundRect(rect, 44f, 44f, borderPaint)

            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = paintColor
                textSize = width * 0.17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(bgValueStr, width / 2f, top + cardH * 0.56f, numPaint)

            val metaParts = buildList {
                add("$timeStr · ${GlucoseUnit.unitLabel(settings.useMgdl)}")
                if (settings.showIobCob && iobUnits >= 0.05) {
                    add("IOB ${String.format(Locale.US, "%.2f", iobUnits)} IE")
                }
                if (settings.showStepsCounter && todaySteps > 0) {
                    add("$todaySteps Schritte")
                }
            }.joinToString("  ·  ")

            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 200, 210, 222)
                textSize = width * 0.036f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(metaParts, width / 2f, top + cardH * 0.84f, metaPaint)
        }
        return bmp
    }
}
