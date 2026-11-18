package dev.xdripyou.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xdripyou.app.domain.ChartWindow
import dev.xdripyou.app.domain.GlucoseStats
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.ui.MainUiState
import dev.xdripyou.app.ui.components.ActiveAlertBanner
import dev.xdripyou.app.ui.components.GlucoseChart
import dev.xdripyou.app.ui.components.SnoozePickerSheet
import dev.xdripyou.app.ui.components.TreatmentEditorSheet
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import java.util.Locale

@Composable
fun OverviewScreen(
    state: MainUiState,
    onSelectWindow: (ChartWindow) -> Unit,
    onSnoozeAlert: (Int) -> Unit,
    onStopAlert: () -> Unit,
    onAddTreatment: (TreatmentEntry) -> Unit,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onSeedDemo: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val settings = state.settings
    var showTreatmentSheet by remember { mutableStateOf(false) }
    var showSnoozeSheet by remember { mutableStateOf(false) }
    var zoomWindowMs by remember { mutableLongStateOf(6 * 3_600_000L) }
    var panOffsetMs by remember { mutableLongStateOf(0L) }

    val anchorNowMs = state.nowMs - panOffsetMs
    val visibleStartMs = anchorNowMs - zoomWindowMs
    val visibleReadings = remember(state.readings24h, visibleStartMs, anchorNowMs) {
        state.readings24h.filter { it.timestampMs in visibleStartMs..anchorNowMs }
    }
    val visibleTreatments = remember(state.treatments24h, visibleStartMs, anchorNowMs) {
        state.treatments24h.filter { it.timestampMs in visibleStartMs..anchorNowMs }
    }
    val zoomStats = remember(visibleReadings, settings.targetLowMgdl, settings.targetHighMgdl) {
        GlucoseStats.compute(
            readings = visibleReadings,
            targetLowMgdl = settings.targetLowMgdl,
            targetHighMgdl = settings.targetHighMgdl,
        )
    }
    val zoomLabel = remember(zoomWindowMs, panOffsetMs) {
        val totalMins = (zoomWindowMs / 60_000L).toInt()
        val hrs = totalMins / 60
        val mins = totalMins % 60
        val durStr = if (mins == 0) "$hrs Std." else "${hrs}h ${mins}m"
        if (panOffsetMs > 60_000L) "$durStr (-${(panOffsetMs / 60_000L)}m)" else durStr
    }

    if (isLandscape) {
        Row(
            modifier = modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.activeAlert?.let { alert ->
                    ActiveAlertBanner(
                        alert = alert,
                        useMgdl = settings.useMgdl,
                        nowMs = state.nowMs,
                        onSnooze = onSnoozeAlert,
                        onStop = onStopAlert,
                        onOpenCustomSnooze = { showSnoozeSheet = true },
                        compactMode = settings.compactAlertBanner,
                    )
                }
                if (!state.permissions.coreReady || !state.permissions.ignoringBatteryOptimizations) {
                    CompactSetupBanner(
                        notificationListenerGranted = state.permissions.notificationListenerEnabled,
                        postNotificationsGranted = state.permissions.postNotificationsGranted,
                        batteryIgnored = state.permissions.ignoringBatteryOptimizations,
                        onOpenNotificationListener = onOpenNotificationListener,
                        onRequestPostNotifications = onRequestPostNotifications,
                        onRequestBatteryExemption = onRequestBatteryExemption,
                    )
                }
                HeroGlucoseCard(
                    state = state,
                    compactMode = true,
                    onAddTreatmentClick = { showTreatmentSheet = true },
                    onSeedDemo = onSeedDemo,
                    onOpenSettingsTab = onOpenSettingsTab,
                    modifier = Modifier.weight(1f),
                )
                CompactStatsCard(
                    stats = zoomStats,
                    stats24h = state.stats24h,
                    windowLabel = zoomLabel,
                    useMgdl = settings.useMgdl,
                    targetLowMgdl = settings.targetLowMgdl,
                    targetHighMgdl = settings.targetHighMgdl,
                    showExtraStatusLine = false,
                )
            }

            FullBleedChartPanel(
                state = state,
                visibleReadings = visibleReadings,
                visibleTreatments = visibleTreatments,
                zoomWindowMs = zoomWindowMs,
                panOffsetMs = panOffsetMs,
                windowLabel = zoomLabel,
                onZoomPanChange = { w, p ->
                    zoomWindowMs = w
                    panOffsetMs = p
                },
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.activeAlert?.let { alert ->
                    ActiveAlertBanner(
                        alert = alert,
                        useMgdl = settings.useMgdl,
                        nowMs = state.nowMs,
                        onSnooze = onSnoozeAlert,
                        onStop = onStopAlert,
                        onOpenCustomSnooze = { showSnoozeSheet = true },
                        compactMode = settings.compactAlertBanner,
                    )
                }

                if (!state.permissions.coreReady || !state.permissions.ignoringBatteryOptimizations) {
                    CompactSetupBanner(
                        notificationListenerGranted = state.permissions.notificationListenerEnabled,
                        postNotificationsGranted = state.permissions.postNotificationsGranted,
                        batteryIgnored = state.permissions.ignoringBatteryOptimizations,
                        onOpenNotificationListener = onOpenNotificationListener,
                        onRequestPostNotifications = onRequestPostNotifications,
                        onRequestBatteryExemption = onRequestBatteryExemption,
                    )
                }

                HeroGlucoseCard(
                    state = state,
                    compactMode = false,
                    onAddTreatmentClick = { showTreatmentSheet = true },
                    onSeedDemo = onSeedDemo,
                    onOpenSettingsTab = onOpenSettingsTab,
                )

                CompactStatsCard(
                    stats = zoomStats,
                    stats24h = state.stats24h,
                    windowLabel = zoomLabel,
                    useMgdl = settings.useMgdl,
                    targetLowMgdl = settings.targetLowMgdl,
                    targetHighMgdl = settings.targetHighMgdl,
                    showExtraStatusLine = settings.showExtraStatusLine,
                )
            }

            FullBleedChartPanel(
                state = state,
                visibleReadings = visibleReadings,
                visibleTreatments = visibleTreatments,
                zoomWindowMs = zoomWindowMs,
                panOffsetMs = panOffsetMs,
                windowLabel = zoomLabel,
                onZoomPanChange = { w, p ->
                    zoomWindowMs = w
                    panOffsetMs = p
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    if (showSnoozeSheet) {
        SnoozePickerSheet(
            onDismiss = { showSnoozeSheet = false },
            onSnoozeActive = onSnoozeAlert,
            onSilenceScope = { _, mins -> onSnoozeAlert(mins) },
        )
    }

    if (showTreatmentSheet) {
        TreatmentEditorSheet(
            useMgdl = settings.useMgdl,
            bolusSettings = settings,
            latestBgMgdl = state.latestReading?.mgdl,
            activeIobUnits = state.iobCob.iobUnits,
            onDismiss = { showTreatmentSheet = false },
            onSave = { entry ->
                onAddTreatment(entry)
                showTreatmentSheet = false
            },
        )
    }
}

@Composable
private fun HeroGlucoseCard(
    state: MainUiState,
    compactMode: Boolean,
    onAddTreatmentClick: () -> Unit,
    onSeedDemo: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    val settings = state.settings
    val latest = state.latestReading
    val deltaMgdl = state.latestDeltaMgdl
    val staleLimitMs = settings.staleThresholdMinutes * 60_000L
    val isStale = latest?.let { (state.nowMs - it.timestampMs) > staleLimitMs } ?: false
    val bgColor = if (latest == null) {
        scheme.onSurfaceVariant
    } else if (isStale) {
        glucoseColors.stale
    } else {
        glucoseColors.colorForMgdl(
            latest.mgdl,
            targetLow = settings.targetLowMgdl,
            targetHigh = settings.targetHighMgdl,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (latest == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("---", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                        Text("Warte auf Messwerte…", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = onSeedDemo, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                            Text("Demo", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = onOpenSettingsTab, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                            Text("Quelle", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                val mins = latest.minutesAgoAt(state.nowMs)
                val ageLabel = if (mins == 0L) "jetzt" else "vor ${mins}m"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = GlucoseUnit.formatValue(latest.mgdl, settings.useMgdl),
                        fontSize = if (compactMode) 34.sp else 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.0).sp,
                        color = bgColor,
                        textDecoration = if (isStale) TextDecoration.LineThrough else null,
                    )
                    Text(
                        text = latest.trend.symbol,
                        fontSize = if (compactMode) 20.sp else 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = bgColor,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = GlucoseUnit.unitLabel(settings.useMgdl),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            if (deltaMgdl != null) {
                                Text(
                                    text = GlucoseUnit.formatDelta(deltaMgdl, settings.useMgdl),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = bgColor,
                                )
                            }
                        }
                        Text(
                            text = (if (isStale) "Veraltet · $ageLabel" else ageLabel) + " · ${latest.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isStale) scheme.error else scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(
                        onClick = onAddTreatmentClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Eintrag", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }

                // Slim status row: IOB, COB, Basal, Schritte & 30m Prediction
                val iobCob = state.iobCob
                val basalRate = state.currentBasalRate
                val pump = state.externalPumpStatus
                val pumpFresh = pump != null && (state.nowMs - pump.timestampMs) <= 3_600_000L
                val steps = state.todaySteps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (iobCob.hasActiveIob) {
                        SmallPill("IOB ${String.format(Locale.US, "%.2f", iobCob.iobUnits)}E", scheme.primaryContainer, scheme.onPrimaryContainer)
                    }
                    if (iobCob.hasActiveCob) {
                        SmallPill("COB ${iobCob.cobGrams.toInt()}g", scheme.secondaryContainer, scheme.onSecondaryContainer)
                    }
                    if (basalRate != null) {
                        SmallPill("B ${String.format(Locale.US, "%.2f", basalRate)}", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                    }
                    if (settings.showStepsCounter && steps > 0) {
                        SmallPill("Schritte $steps", scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
                    }
                    if (pumpFresh && pump?.reservoirUnits != null) {
                        SmallPill("${pump.reservoirUnits.toInt()}E", scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    state.predictions.lastOrNull()?.let { pred ->
                        Text(
                            text = "30m ~${GlucoseUnit.formatValue(pred.mgdl, settings.useMgdl)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullBleedChartPanel(
    state: MainUiState,
    visibleReadings: List<dev.xdripyou.app.domain.GlucoseReading>,
    visibleTreatments: List<TreatmentEntry>,
    zoomWindowMs: Long,
    panOffsetMs: Long,
    windowLabel: String,
    onZoomPanChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = scheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp, bottom = 2.dp),
        ) {
            val preset = dev.xdripyou.app.domain.InsulinPreset.fromWire(settings.insulinPreset)
            GlucoseChart(
                readings = state.readings24h,
                treatments = if (settings.showChartTreatments) state.treatments24h else emptyList(),
                predictions = if (panOffsetMs <= 0L) state.predictions else emptyList(),
                windowMs = zoomWindowMs,
                nowMs = state.nowMs,
                useMgdl = settings.useMgdl,
                targetLowMgdl = settings.targetLowMgdl,
                targetHighMgdl = settings.targetHighMgdl,
                pointsOnly = settings.chartPointsOnly,
                showGrid = settings.showChartGrid,
                showAverageLine = settings.showChartAverageLine,
                chartMaxMgdl = settings.chartMaxMgdl,
                smoothNoise = settings.smoothChartNoise,
                showIobCurve = settings.showChartIobCurve && settings.showIobCob,
                insulinDurationMinutes = if (preset == dev.xdripyou.app.domain.InsulinPreset.CUSTOM) settings.insulinDurationMinutes else preset.durationMinutes,
                insulinPeakMinutes = if (preset == dev.xdripyou.app.domain.InsulinPreset.CUSTOM) settings.insulinPeakMinutes else preset.peakMinutes,
                insulinOnsetMinutes = if (preset == dev.xdripyou.app.domain.InsulinPreset.CUSTOM) 15 else preset.onsetMinutes,
                activitySamples = state.activitySamples24h,
                showStepsInChart = settings.showStepsInChart,
                showBasalInChart = settings.showBasalInChart && settings.showBasalRate,
                basalSchedule = if (settings.showBasalRate) state.basalSchedule else null,
                dotSizeDp = settings.chartDotSizeDp,
                panOffsetMs = panOffsetMs,
                windowLabel = windowLabel,
                onZoomPanChange = onZoomPanChange,
                fullBleed = true,
                chartHeight = null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompactStatsCard(
    stats: GlucoseStats,
    stats24h: GlucoseStats,
    windowLabel: String,
    useMgdl: Boolean,
    targetLowMgdl: Double,
    targetHighMgdl: Double,
    showExtraStatusLine: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CompactStatMetric(
                    label = "Ziel ($windowLabel)",
                    value = if (stats.count == 0) "--" else "${stats.inRangePercent.toInt()}%",
                    accent = glucoseColors.inRange,
                    modifier = Modifier.weight(1f),
                )
                CompactStatMetric(
                    label = "Ø ${GlucoseUnit.unitLabel(useMgdl)}",
                    value = if (stats.count == 0) "--" else GlucoseUnit.formatValue(stats.meanMgdl, useMgdl),
                    accent = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                CompactStatMetric(
                    label = "GMI",
                    value = if (stats.count == 0) "--" else String.format(Locale.US, "%.1f%%", stats.gmiPercent),
                    accent = scheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                CompactStatMetric(
                    label = "CV (≤36%)",
                    value = if (stats.count == 0) "--" else "${stats.cvPercent.toInt()}%",
                    accent = if (stats.cvPercent > 36.0) glucoseColors.high else glucoseColors.inRange,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showExtraStatusLine && stats24h.count > 0) {
                Text(
                    text = "24h: TIR ${stats24h.inRangePercent.toInt()}% · Ø ${GlucoseUnit.formatValue(stats24h.meanMgdl, useMgdl)} · Ziel ${GlucoseUnit.formatValue(targetLowMgdl, useMgdl)}–${GlucoseUnit.formatValue(targetHighMgdl, useMgdl)} · ${stats24h.count} Werte",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactStatMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactSetupBanner(
    notificationListenerGranted: Boolean,
    postNotificationsGranted: Boolean,
    batteryIgnored: Boolean,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (icon, text, btnLabel, action) = when {
        !notificationListenerGranted -> Quad(Icons.Rounded.NotificationsActive, "CamAPS-Zugriff freigeben", "Freigeben", onOpenNotificationListener)
        !postNotificationsGranted -> Quad(Icons.Rounded.Notifications, "Benachrichtigungen erlauben", "Erlauben", onRequestPostNotifications)
        else -> Quad(Icons.Rounded.BatteryChargingFull, "Akku-Optimierung ausnehmen", "Öffnen", onRequestBatteryExemption)
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = action,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(btnLabel, maxLines = 1)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun SmallPill(text: String, container: Color, content: Color) {
    Surface(
        color = container,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}
