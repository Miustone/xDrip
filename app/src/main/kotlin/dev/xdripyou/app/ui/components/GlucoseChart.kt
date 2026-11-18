package dev.xdripyou.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.PredictionPoint
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    treatments: List<TreatmentEntry> = emptyList(),
    predictions: List<PredictionPoint> = emptyList(),
    windowMs: Long,
    nowMs: Long,
    useMgdl: Boolean,
    targetLowMgdl: Double,
    targetHighMgdl: Double,
    pointsOnly: Boolean = false,
    showGrid: Boolean = true,
    showAverageLine: Boolean = false,
    chartMaxMgdl: Int = 0,
    chartHeight: Dp? = 210.dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    var selected by remember { mutableStateOf<GlucoseReading?>(null) }

    val futureHorizonMs = if (predictions.isNotEmpty()) 30 * 60_000L else 0L
    val startMs = nowMs - windowMs
    val endMs = nowMs + futureHorizonMs
    val totalSpanMs = (endMs - startMs).coerceAtLeast(1L)

    val density = LocalDensity.current
    val leftPadPx = with(density) { 42.dp.toPx() }
    val rightPadPx = with(density) { 12.dp.toPx() }
    val topPadPx = with(density) { 10.dp.toPx() }
    val bottomPadPx = with(density) { 26.dp.toPx() }
    val labelTextSizePx = with(density) { 10.5f * density.density }

    fun pickNearest(touchX: Float, widthPx: Float) {
        if (readings.isEmpty()) return
        val chartW = (widthPx - leftPadPx - rightPadPx).coerceAtLeast(1f)
        val fraction = ((touchX - leftPadPx) / chartW).coerceIn(0f, 1f)
        val targetMs = startMs + (fraction * totalSpanMs).toLong()
        selected = readings.minByOrNull { abs(it.timestampMs - targetMs) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (selected != null) {
            val r = selected!!
            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val valueStr = GlucoseUnit.formatValue(r.mgdl, useMgdl)
            val unitStr = GlucoseUnit.unitLabel(useMgdl)
            val color = glucoseColors.colorForMgdl(r.mgdl, targetLowMgdl, targetHighMgdl)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$valueStr $unitStr ${r.trend.symbol}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${timeFormat.format(Date(r.timestampMs))} Uhr · ${r.source}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(glucoseColors.inRange, "Ziel")
                    LegendDot(glucoseColors.low, "Niedrig")
                    LegendDot(glucoseColors.high, "Hoch")
                    if (predictions.isNotEmpty()) {
                        LegendDot(scheme.tertiary, "Trend")
                    }
                }
                Text(
                    text = "Tippen / Ziehen",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val canvasSizeMod = if (chartHeight != null) {
            Modifier.fillMaxWidth().height(chartHeight)
        } else {
            Modifier.fillMaxWidth().weight(1f)
        }
        Canvas(
            modifier = canvasSizeMod
                .pointerInput(readings, nowMs, windowMs, futureHorizonMs) {
                    detectTapGestures { offset ->
                        pickNearest(offset.x, size.width.toFloat())
                    }
                }
                .pointerInput(readings, nowMs, windowMs, futureHorizonMs) {
                    detectDragGestures(
                        onDragStart = { offset -> pickNearest(offset.x, size.width.toFloat()) },
                        onDrag = { change, _ -> pickNearest(change.position.x, size.width.toFloat()) },
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                    )
                },
        ) {
            val chartLeft = leftPadPx
            val chartRight = (size.width - rightPadPx).coerceAtLeast(chartLeft + 1f)
            val chartTop = topPadPx
            val chartBottom = (size.height - bottomPadPx).coerceAtLeast(chartTop + 1f)
            val chartW = chartRight - chartLeft
            val chartH = chartBottom - chartTop

            val minY = 40.0
            val maxObserved = max(
                readings.maxOfOrNull { it.mgdl } ?: 220.0,
                predictions.maxOfOrNull { it.mgdl } ?: 220.0,
            )
            val maxY = if (chartMaxMgdl in 200..400) {
                chartMaxMgdl.toDouble()
            } else {
                max(240.0, (ceil(maxObserved / 40.0) * 40.0).coerceIn(240.0, 400.0))
            }

            fun xForMs(ms: Long): Float {
                val t = ((ms - startMs).toDouble() / totalSpanMs.toDouble()).coerceIn(0.0, 1.0)
                return (chartLeft + t * chartW).toFloat()
            }

            fun yForMgdl(mgdl: Double): Float {
                val clamped = mgdl.coerceIn(minY, maxY)
                val t = (clamped - minY) / (maxY - minY)
                return (chartBottom - t * chartH).toFloat()
            }

            // 1. Target band
            val highY = yForMgdl(targetHighMgdl)
            val lowY = yForMgdl(targetLowMgdl)
            drawRect(
                color = glucoseColors.inRange.copy(alpha = 0.11f),
                topLeft = Offset(chartLeft, highY),
                size = androidx.compose.ui.geometry.Size(chartW, lowY - highY),
            )

            // 2. "Now" vertical marker when prediction horizon is shown
            if (futureHorizonMs > 0L) {
                val nowX = xForMs(nowMs)
                drawLine(
                    color = scheme.outlineVariant.copy(alpha = 0.55f),
                    start = Offset(nowX, chartTop),
                    end = Offset(nowX, chartBottom),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                )
            }

            // 3. Grid lines + Y labels
            val labelPaint = Paint().apply {
                color = scheme.onSurfaceVariant.toArgb()
                textSize = labelTextSizePx
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            val ticks = linkedSetOf(targetLowMgdl, 120.0, targetHighMgdl).apply {
                if (maxY >= 260.0) add(250.0)
                add(maxY)
            }
            for (tick in ticks) {
                if (tick < minY || tick > maxY) continue;
                val y = yForMgdl(tick)
                val isThreshold = tick == targetLowMgdl || tick == targetHighMgdl
                if (isThreshold) {
                    drawLine(
                        color = (if (tick == targetLowMgdl) glucoseColors.low else glucoseColors.high)
                            .copy(alpha = 0.55f),
                        start = Offset(chartLeft, y),
                        end = Offset(chartRight, y),
                        strokeWidth = 2.5f,
                        pathEffect = dashEffect,
                    )
                } else if (showGrid) {
                    drawLine(
                        color = scheme.outlineVariant.copy(alpha = 0.45f),
                        start = Offset(chartLeft, y),
                        end = Offset(chartRight, y),
                        strokeWidth = 1.5f,
                    )
                }
                drawContext.canvas.nativeCanvas.drawText(
                    GlucoseUnit.formatValue(tick, useMgdl),
                    chartLeft - 10f,
                    y + labelTextSizePx * 0.35f,
                    labelPaint,
                )
            }

            // 4. Time axis labels
            val xLabelPaint = Paint().apply {
                color = scheme.onSurfaceVariant.toArgb()
                textSize = labelTextSizePx
                isAntiAlias = true
            }
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            for (i in 0..4) {
                val frac = i / 4.0
                val ms = startMs + (totalSpanMs * frac).toLong()
                val text = if (i == 4 && futureHorizonMs == 0L) "Jetzt" else timeFmt.format(Date(ms))
                xLabelPaint.textAlign = when (i) {
                    0 -> Paint.Align.LEFT
                    4 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                val x = (chartLeft + frac * chartW).toFloat()
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    x,
                    chartBottom + labelTextSizePx * 1.4f,
                    xLabelPaint,
                )
            }

            if (readings.isEmpty()) {
                val emptyPaint = Paint().apply {
                    color = scheme.onSurfaceVariant.toArgb()
                    textSize = labelTextSizePx * 1.2f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "Noch keine Messwerte im gewählten Zeitraum",
                    chartLeft + chartW / 2f,
                    chartTop + chartH / 2f,
                    emptyPaint,
                )
                return@Canvas
            }

            // 4b. Average line if enabled
            if (showAverageLine && readings.isNotEmpty()) {
                val avgMgdl = readings.map { it.mgdl }.average()
                val avgY = yForMgdl(avgMgdl)
                drawLine(
                    color = scheme.secondary.copy(alpha = 0.7f),
                    start = Offset(chartLeft, avgY),
                    end = Offset(chartRight, avgY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f),
                )
            }

            // 5. Treatment markers along bottom of chart
            val treatLabelPaint = Paint().apply {
                color = scheme.primary.toArgb()
                textSize = labelTextSizePx * 0.9f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            for (t in treatments) {
                if (t.timestampMs < startMs || t.timestampMs > endMs) continue
                val tx = xForMs(t.timestampMs)
                val markerY = chartBottom - 16f
                if (t.hasInsulin) {
                    drawCircle(color = scheme.primary, radius = 8f, center = Offset(tx, markerY))
                    treatLabelPaint.color = scheme.primary.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        "${String.format(Locale.US, "%.1f", t.insulinUnits)}E",
                        tx,
                        markerY - 12f,
                        treatLabelPaint,
                    )
                }
                if (t.hasCarbs) {
                    val cy = if (t.hasInsulin) markerY - 28f else markerY
                    drawCircle(color = glucoseColors.high, radius = 8f, center = Offset(tx, cy))
                    treatLabelPaint.color = glucoseColors.high.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        "${t.carbsGrams.toInt()}g",
                        tx,
                        cy - 12f,
                        treatLabelPaint,
                    )
                }
                val bgMgdl = t.bloodGlucoseMgdl
                if (bgMgdl != null && bgMgdl > 0.0) {
                    val by = yForMgdl(bgMgdl)
                    drawCircle(color = glucoseColors.low, radius = 10f, center = Offset(tx, by))
                    drawCircle(color = scheme.surface, radius = 5.5f, center = Offset(tx, by))
                }
            }

            // 6. Curve + points (chronological)
            val sorted = readings.asReversed()
            if (!pointsOnly) {
                val path = Path()
                var started = false
                for (i in sorted.indices) {
                    val r = sorted[i]
                    val pt = Offset(xForMs(r.timestampMs), yForMgdl(r.mgdl))
                    if (!started || (i > 0 && r.timestampMs - sorted[i - 1].timestampMs > 1_200_000L)) {
                        path.moveTo(pt.x, pt.y)
                        started = true
                    } else {
                        path.lineTo(pt.x, pt.y)
                    }
                }
                drawPath(
                    path = path,
                    color = scheme.primary.copy(alpha = 0.55f),
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round),
                )
            }

            // 7. Prediction dashed curve
            if (predictions.isNotEmpty() && sorted.isNotEmpty()) {
                val last = sorted.last()
                val predPath = Path().apply {
                    moveTo(xForMs(last.timestampMs), yForMgdl(last.mgdl))
                    for (p in predictions) {
                        lineTo(xForMs(p.timestampMs), yForMgdl(p.mgdl))
                    }
                }
                drawPath(
                    path = predPath,
                    color = scheme.tertiary.copy(alpha = 0.75f),
                    style = Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                    ),
                )
                for (p in predictions) {
                    drawCircle(
                        color = scheme.tertiary.copy(alpha = 0.7f),
                        radius = 4.5f,
                        center = Offset(xForMs(p.timestampMs), yForMgdl(p.mgdl)),
                    )
                }
            }

            val dotRadius = if (sorted.size > 100) 5.5f else 7.5f
            for (r in sorted) {
                val pt = Offset(xForMs(r.timestampMs), yForMgdl(r.mgdl))
                val c = glucoseColors.colorForMgdl(r.mgdl, targetLowMgdl, targetHighMgdl)
                drawCircle(color = c, radius = dotRadius, center = pt)
            }

            selected?.let { sel ->
                val pt = Offset(xForMs(sel.timestampMs), yForMgdl(sel.mgdl))
                drawLine(
                    color = scheme.primary.copy(alpha = 0.5f),
                    start = Offset(pt.x, chartTop),
                    end = Offset(pt.x, chartBottom),
                    strokeWidth = 2.5f,
                )
                drawCircle(color = scheme.surface, radius = 14f, center = pt)
                drawCircle(
                    color = glucoseColors.colorForMgdl(sel.mgdl, targetLowMgdl, targetHighMgdl),
                    radius = 10f,
                    center = pt,
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
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
