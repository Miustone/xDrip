package dev.xdripyou.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.roundToLong
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.ActivitySample
import dev.xdripyou.app.domain.BasalSchedule
import dev.xdripyou.app.domain.CalibrationEngine
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.IobCobCalculator
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
    smoothNoise: Boolean = false,
    showIobCurve: Boolean = false,
    insulinDurationMinutes: Int = 270,
    insulinPeakMinutes: Int = 65,
    insulinOnsetMinutes: Int = 15,
    activitySamples: List<ActivitySample> = emptyList(),
    showStepsInChart: Boolean = true,
    showBasalInChart: Boolean = false,
    basalSchedule: BasalSchedule? = null,
    dotSizeDp: Int = 4,
    panOffsetMs: Long = 0L,
    windowLabel: String? = null,
    onZoomPanChange: ((newWindowMs: Long, newPanOffsetMs: Long) -> Unit)? = null,
    fullBleed: Boolean = false,
    chartHeight: Dp? = 210.dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    var selected by remember { mutableStateOf<GlucoseReading?>(null) }
    var lastTapMs by remember { mutableLongStateOf(0L) }

    val anchorNowMs = nowMs - panOffsetMs.coerceAtLeast(0L)
    val futureHorizonMs = if (predictions.isNotEmpty() && panOffsetMs <= 0L) 30 * 60_000L else 0L
    val startMs = anchorNowMs - windowMs
    val endMs = anchorNowMs + futureHorizonMs
    val totalSpanMs = (endMs - startMs).coerceAtLeast(1L)

    val density = LocalDensity.current
    val leftPadPx = with(density) { (if (fullBleed) 32.dp else 42.dp).toPx() }
    val rightPadPx = with(density) { (if (fullBleed) 6.dp else 12.dp).toPx() }
    val topPadPx = with(density) { 8.dp.toPx() }
    val bottomPadPx = with(density) { 24.dp.toPx() }
    val labelTextSizePx = with(density) { 10.5f * density.density }

    val latestWindowMs by rememberUpdatedState(windowMs)
    val latestPanMs by rememberUpdatedState(panOffsetMs)
    val latestOnZoomPan by rememberUpdatedState(onZoomPanChange)
    val latestStartMs by rememberUpdatedState(startMs)
    val latestEndMs by rememberUpdatedState(endMs)
    val latestTotalSpanMs by rememberUpdatedState(totalSpanMs)
    val latestReadings by rememberUpdatedState(readings)

    fun pickNearest(touchX: Float, widthPx: Float) {
        val visible = latestReadings.filter { it.timestampMs in latestStartMs..latestEndMs }
        if (visible.isEmpty()) return
        val chartW = (widthPx - leftPadPx - rightPadPx).coerceAtLeast(1f)
        val fraction = ((touchX - leftPadPx) / chartW).coerceIn(0f, 1f)
        val targetMs = latestStartMs + (fraction * latestTotalSpanMs).toLong()
        selected = visible.minByOrNull { abs(it.timestampMs - targetMs) }
    }

    val sortedReadings = remember(readings, smoothNoise) {
        val chronological = readings.asReversed()
        if (smoothNoise) CalibrationEngine.smoothReadings(chronological) else chronological
    }
    val globalMaxIob = remember(treatments, insulinDurationMinutes) {
        // Compute a stable 24h reference scale so the IOB curve never rescales vertically while panning
        treatments.filter { it.insulinUnits > 0.0 }
            .maxOfOrNull { it.insulinUnits }
            ?.coerceAtLeast(3.0) ?: 4.0
    }
    val globalMaxSteps = remember(activitySamples) {
        (activitySamples.maxOfOrNull { it.steps } ?: 100).coerceAtLeast(150)
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
                    .padding(horizontal = if (fullBleed) 10.dp else 0.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (fullBleed) 10.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendDot(glucoseColors.inRange, "Ziel")
                    LegendDot(glucoseColors.low, "Niedrig")
                    LegendDot(glucoseColors.high, "Hoch")
                    if (showStepsInChart && activitySamples.isNotEmpty()) {
                        LegendDot(scheme.tertiary.copy(alpha = 0.7f), "Aktivität")
                    }
                }
                Text(
                    text = windowLabel?.let { "$it · Pinch-Zoom" } ?: "Pinch-Zoom / Tippen",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        val canvasSizeMod = if (chartHeight != null) {
            Modifier.fillMaxWidth().height(chartHeight)
        } else {
            Modifier.fillMaxWidth().weight(1f)
        }
        Canvas(
            modifier = canvasSizeMod
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val startPos = firstDown.position
                        var moved = false
                        val touchSlop = viewConfiguration.touchSlop
                        val chartWidthPx = (size.width - leftPadPx - rightPadPx).coerceAtLeast(1f)
                        // Track gesture-local zoom & pan accumulatively to prevent 120Hz stale-state jitter
                        var gestureWinMs = latestWindowMs
                        var gesturePanMs = latestPanMs

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2 && latestOnZoomPan != null) {
                                val zoom = event.calculateZoom()
                                val panX = event.calculatePan().x
                                if (abs(zoom - 1f) > 0.002f || abs(panX) > 0.5f) {
                                    moved = true
                                    gestureWinMs = (gestureWinMs / zoom).roundToLong().coerceIn(3_600_000L, 86_400_000L)
                                    val maxPan = (86_400_000L - gestureWinMs).coerceAtLeast(0L)
                                    val panDeltaMs = ((panX / chartWidthPx) * gestureWinMs).roundToLong()
                                    gesturePanMs = (gesturePanMs + panDeltaMs).coerceIn(0L, maxPan)
                                    latestOnZoomPan?.invoke(gestureWinMs, gesturePanMs)
                                    event.changes.forEach { it.consume() }
                                }
                            } else if (pressed.size == 1) {
                                val change = pressed.first()
                                val dist = (change.position - startPos).getDistance()
                                if (dist > touchSlop) moved = true
                                if (moved && latestOnZoomPan != null) {
                                    val dx = change.positionChange().x
                                    if (abs(dx) > 0.5f && gestureWinMs < 86_400_000L) {
                                        val maxPan = (86_400_000L - gestureWinMs).coerceAtLeast(0L)
                                        val panDeltaMs = ((dx / chartWidthPx) * gestureWinMs).roundToLong()
                                        gesturePanMs = (gesturePanMs + panDeltaMs).coerceIn(0L, maxPan)
                                        latestOnZoomPan?.invoke(gestureWinMs, gesturePanMs)
                                        change.consume()
                                    } else {
                                        pickNearest(change.position.x, size.width.toFloat())
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (!moved) {
                            val nowTap = System.currentTimeMillis()
                            if (nowTap - lastTapMs <= 280L && latestOnZoomPan != null) {
                                latestOnZoomPan?.invoke(6 * 3_600_000L, 0L)
                                selected = null
                                lastTapMs = 0L
                            } else {
                                pickNearest(firstDown.position.x, size.width.toFloat())
                                lastTapMs = nowTap
                            }
                        }
                    }
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
                val t = (ms - startMs).toDouble() / totalSpanMs.toDouble()
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
                val text = if (i == 4 && futureHorizonMs == 0L && panOffsetMs <= 0L) "Jetzt" else timeFmt.format(Date(ms))
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

            val sorted = sortedReadings.filter {
                it.timestampMs in (startMs - 1_200_000L)..(endMs + 1_200_000L)
            }
            val anyVisibleReading = sorted.any { it.timestampMs in startMs..endMs }

            clipRect(left = chartLeft, top = chartTop, right = chartRight, bottom = chartBottom) {
                // 4b. Average line if enabled
                if (showAverageLine && anyVisibleReading) {
                    val avgMgdl = sorted.filter { it.timestampMs in startMs..endMs }.map { it.mgdl }.average()
                    val avgY = yForMgdl(avgMgdl)
                    drawLine(
                        color = scheme.secondary.copy(alpha = 0.7f),
                        start = Offset(chartLeft, avgY),
                        end = Offset(chartRight, avgY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f),
                    )
                }

                // 4c. Activity / Steps bars along bottom (Schritte, globally scaled to prevent scroll jitter)
                if (showStepsInChart && activitySamples.isNotEmpty()) {
                    val visibleSteps = activitySamples.filter {
                        it.timestampMs in (startMs - 1_800_000L)..(endMs + 1_800_000L) && it.steps > 0
                    }
                    if (visibleSteps.isNotEmpty()) {
                        val maxBarH = chartH * 0.14f
                        val approxBuckets = (totalSpanMs / 3_600_000f).coerceIn(2f, 24f)
                        val barW = (chartW / (approxBuckets * 2.2f)).coerceIn(6f, 22f)
                        for (s in visibleSteps) {
                            val sx = xForMs(s.timestampMs)
                            val barH = ((s.steps.toFloat() / globalMaxSteps) * maxBarH).coerceAtLeast(4f)
                            drawRect(
                                color = scheme.tertiary.copy(alpha = 0.24f),
                                topLeft = Offset(sx - barW / 2f, chartBottom - barH),
                                size = androidx.compose.ui.geometry.Size(barW, barH),
                            )
                        }
                    }
                }

                // 4d. Basal rate step curve along lower chart band
                if (showBasalInChart && basalSchedule != null) {
                    val maxRate = (basalSchedule.hourlyRates.maxOrNull() ?: 1.0).coerceAtLeast(0.5)
                    val basalBandH = chartH * 0.14f
                    val stepCount = 32
                    val basalPath = Path()
                    for (i in 0..stepCount) {
                        val ms = startMs + ((anchorNowMs - startMs) * i) / stepCount
                        val rate = basalSchedule.rateAt(ms)
                        val bx = xForMs(ms)
                        val by = chartBottom - ((rate / maxRate) * basalBandH).toFloat()
                        if (i == 0) basalPath.moveTo(bx, by) else basalPath.lineTo(bx, by)
                    }
                    drawPath(
                        path = basalPath,
                        color = scheme.secondary.copy(alpha = 0.50f),
                        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)),
                    )
                }

                // 5. Subtle IOB activity overlay (smooth continuous fill + contour stroke over all 24h treatments)
                if (showIobCurve && treatments.any { it.insulinUnits > 0.0 }) {
                    val steps = 80
                    val iobMaxHeightPx = chartH * 0.24f
                    val iobFillPath = Path()
                    val iobStrokePath = Path()
                    var hasAnyIob = false
                    for (s in 0..steps) {
                        val ms = startMs + ((endMs - startMs) * s) / steps
                        val iobAt = IobCobCalculator.calculateIob(
                            treatments = treatments,
                            atMs = ms,
                            durationMinutes = insulinDurationMinutes,
                            peakMinutes = insulinPeakMinutes,
                            onsetMinutes = insulinOnsetMinutes,
                        )
                        if (iobAt > 0.02) hasAnyIob = true
                        val px = xForMs(ms)
                        val py = (chartBottom - ((iobAt / globalMaxIob) * iobMaxHeightPx).toFloat())
                            .coerceIn(chartBottom - iobMaxHeightPx * 1.15f, chartBottom)
                        if (s == 0) {
                            iobFillPath.moveTo(px, chartBottom)
                            iobFillPath.lineTo(px, py)
                            iobStrokePath.moveTo(px, py)
                        } else {
                            iobFillPath.lineTo(px, py)
                            iobStrokePath.lineTo(px, py)
                        }
                    }
                    if (hasAnyIob) {
                        iobFillPath.lineTo(xForMs(endMs), chartBottom)
                        iobFillPath.close()
                        drawPath(
                            path = iobFillPath,
                            color = scheme.primary.copy(alpha = 0.14f),
                        )
                        drawPath(
                            path = iobStrokePath,
                            color = scheme.primary.copy(alpha = 0.45f),
                            style = Stroke(width = 2.2f, cap = StrokeCap.Round),
                        )
                    }
                }

                // 5b. Treatment markers along bottom of chart
                val treatLabelPaint = Paint().apply {
                    color = scheme.primary.toArgb()
                    textSize = labelTextSizePx * 0.9f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                for (t in treatments) {
                    if (t.timestampMs < startMs - 900_000L || t.timestampMs > endMs + 900_000L) continue
                    val tx = xForMs(t.timestampMs)
                    val markerY = chartBottom - 16f
                    if (t.hasInsulin) {
                        val bolusH = ((t.insulinUnits / globalMaxIob) * (chartH * 0.24)).toFloat().coerceIn(10f, chartH * 0.28f)
                        drawRect(
                            color = scheme.primary.copy(alpha = 0.28f),
                            topLeft = Offset(tx - 3.5f, chartBottom - bolusH),
                            size = androidx.compose.ui.geometry.Size(7f, bolusH),
                        )
                        drawCircle(color = scheme.primary, radius = 7f, center = Offset(tx, markerY))
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
                        drawCircle(color = glucoseColors.high, radius = 7.5f, center = Offset(tx, cy))
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
                        drawCircle(color = glucoseColors.low, radius = 9.5f, center = Offset(tx, by))
                        drawCircle(color = scheme.surface, radius = 5f, center = Offset(tx, by))
                    }
                }

                // 6. Glucose curve + points
                if (!pointsOnly && sorted.isNotEmpty()) {
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
                if (predictions.isNotEmpty() && sortedReadings.isNotEmpty()) {
                    val last = sortedReadings.last()
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

                val baseDotPx = with(density) { (dotSizeDp.coerceIn(2, 7) * 0.68f).dp.toPx() }
                val dotRadius = if (sorted.size > 120) baseDotPx * 0.78f else baseDotPx
                for (r in sorted) {
                    val pt = Offset(xForMs(r.timestampMs), yForMgdl(r.mgdl))
                    val c = glucoseColors.colorForMgdl(r.mgdl, targetLowMgdl, targetHighMgdl)
                    drawCircle(color = c, radius = dotRadius, center = pt)
                }

                selected?.let { sel ->
                    if (sel.timestampMs in startMs..endMs) {
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

            if (!anyVisibleReading) {
                val emptyPaint = Paint().apply {
                    color = scheme.onSurfaceVariant.toArgb()
                    textSize = labelTextSizePx * 1.15f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "Noch keine Messwerte im gewählten Zeitraum",
                    chartLeft + chartW / 2f,
                    chartTop + chartH / 2f,
                    emptyPaint,
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
