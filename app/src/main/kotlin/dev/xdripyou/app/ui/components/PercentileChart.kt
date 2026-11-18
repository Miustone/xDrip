package dev.xdripyou.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.AgpBucket
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import kotlin.math.max

/**
 * Compose Material 3 You port of xDrip's `PercentileView` (Ambulatory Glucose Profile / AGP).
 * Renders 10–90% outer band, 25–75% IQR inner band, and 50% median curve across 24 hours.
 */
@Composable
fun PercentileChart(
    buckets: List<AgpBucket>,
    useMgdl: Boolean,
    targetLowMgdl: Double,
    targetHighMgdl: Double,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current

    if (buckets.size < 2) {
        Text(
            text = "Für das AGP-Perzentilprofil werden mehr Messwerte über verschiedene Tageszeiten benötigt.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val yMin = 40.0
    val dataMax = buckets.maxOf { it.q90 }
    val yMax = max(260.0, ((dataMax + 30.0) / 50.0).toInt() * 50.0).coerceAtMost(400.0)
    val labelColorArgb = scheme.onSurfaceVariant.toArgb()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(color = scheme.primary.copy(alpha = 0.20f), label = "10–90 %")
            LegendDot(color = scheme.primary.copy(alpha = 0.42f), label = "25–75 %")
            LegendDot(color = scheme.primary, label = "Median 50 %")
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        ) {
            val leftPad = 36.dp.toPx()
            val rightPad = 10.dp.toPx()
            val topPad = 8.dp.toPx()
            val bottomPad = 24.dp.toPx()
            val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            fun xForSlot(slotIndex: Int): Float =
                leftPad + (slotIndex.toFloat() / 47f) * plotW

            fun yForMgdl(mgdl: Double): Float {
                val norm = ((mgdl - yMin) / (yMax - yMin)).coerceIn(0.0, 1.0)
                return topPad + (1.0f - norm.toFloat()) * plotH
            }

            val textPaint = Paint().apply {
                color = labelColorArgb
                textSize = 10.dp.toPx()
                isAntiAlias = true
            }

            // Grid lines
            val gridSteps = listOf(70.0, 140.0, 180.0, 250.0, 300.0).filter { it in yMin..yMax }
            for (g in gridSteps) {
                val y = yForMgdl(g)
                drawLine(
                    color = scheme.outlineVariant.copy(alpha = 0.4f),
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + plotW, y),
                    strokeWidth = 1.dp.toPx(),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    GlucoseUnit.formatValue(g, useMgdl),
                    2.dp.toPx(),
                    y + 3.dp.toPx(),
                    textPaint,
                )
            }

            // Target band bounds
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            val yLow = yForMgdl(targetLowMgdl)
            val yHigh = yForMgdl(targetHighMgdl)
            drawLine(
                color = glucoseColors.low.copy(alpha = 0.75f),
                start = Offset(leftPad, yLow),
                end = Offset(leftPad + plotW, yLow),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect,
            )
            drawLine(
                color = glucoseColors.high.copy(alpha = 0.75f),
                start = Offset(leftPad, yHigh),
                end = Offset(leftPad + plotW, yHigh),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect,
            )

            // Helper to fill band between upper and lower quantile selectors
            fun drawBand(
                lower: (AgpBucket) -> Double,
                upper: (AgpBucket) -> Double,
                fillColor: Color,
            ) {
                val path = Path()
                buckets.forEachIndexed { idx, b ->
                    val x = xForSlot(b.slotIndex)
                    val y = yForMgdl(upper(b))
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                for (idx in buckets.indices.reversed()) {
                    val b = buckets[idx]
                    path.lineTo(xForSlot(b.slotIndex), yForMgdl(lower(b)))
                }
                path.close()
                drawPath(path = path, color = fillColor)
            }

            drawBand({ it.q10 }, { it.q90 }, scheme.primary.copy(alpha = 0.16f))
            drawBand({ it.q25 }, { it.q75 }, scheme.primary.copy(alpha = 0.34f))

            // Median line
            val medianPath = Path()
            buckets.forEachIndexed { idx, b ->
                val x = xForSlot(b.slotIndex)
                val y = yForMgdl(b.q50)
                if (idx == 0) medianPath.moveTo(x, y) else medianPath.lineTo(x, y)
            }
            drawPath(
                path = medianPath,
                color = scheme.primary,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // X-axis hour ticks
            for (hour in listOf(0, 4, 8, 12, 16, 20, 24)) {
                val slot = (hour * 2).coerceAtMost(47)
                val x = if (hour == 24) leftPad + plotW else xForSlot(slot)
                val label = String.format("%02d", hour)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    (x - 6.dp.toPx()).coerceIn(leftPad, leftPad + plotW - 14.dp.toPx()),
                    size.height - 4.dp.toPx(),
                    textPaint,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
