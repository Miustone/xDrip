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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.39f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.activeAlert?.let { alert ->
                    ActiveAlertBanner(
                        alert = alert,
                        useMgdl = settings.useMgdl,
                        nowMs = state.nowMs,
                        onSnooze = onSnoozeAlert,
                        onStop = onStopAlert,
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
                    stats = state.windowStats,
                    stats24h = state.stats24h,
                    windowLabel = state.chartWindow.label,
                    useMgdl = settings.useMgdl,
                    targetLowMgdl = settings.targetLowMgdl,
                    targetHighMgdl = settings.targetHighMgdl,
                    showExtraStatusLine = false,
                )
            }

            ChartPanelCard(
                state = state,
                onSelectWindow = onSelectWindow,
                modifier = Modifier
                    .weight(0.61f)
                    .fillMaxHeight(),
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.activeAlert?.let { alert ->
                ActiveAlertBanner(
                    alert = alert,
                    useMgdl = settings.useMgdl,
                    nowMs = state.nowMs,
                    onSnooze = onSnoozeAlert,
                    onStop = onStopAlert,
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

            ChartPanelCard(
                state = state,
                onSelectWindow = onSelectWindow,
                modifier = Modifier.weight(1f),
            )

            CompactStatsCard(
                stats = state.windowStats,
                stats24h = state.stats24h,
                windowLabel = state.chartWindow.label,
                useMgdl = settings.useMgdl,
                targetLowMgdl = settings.targetLowMgdl,
                targetHighMgdl = settings.targetHighMgdl,
                showExtraStatusLine = settings.showExtraStatusLine,
            )
        }
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = scheme.secondaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = scheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = latest?.source ?: "CamAPS FX",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (latest != null) {
                    val mins = latest.minutesAgoAt(state.nowMs)
                    val ageLabel = if (mins == 0L) "jetzt" else "vor ${mins}m"
                    Surface(
                        color = if (isStale) scheme.errorContainer else scheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = if (isStale) "Veraltet · $ageLabel" else ageLabel,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isStale) scheme.onErrorContainer else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (latest == null) {
                Text(
                    text = "---",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = "Warte auf Messwerte…",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onSeedDemo,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Rounded.Timeline, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Demo", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onOpenSettingsTab,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Rounded.Science, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Quelle", maxLines = 1)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = GlucoseUnit.formatValue(latest.mgdl, settings.useMgdl),
                        fontSize = if (compactMode) 48.sp else 54.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                        color = bgColor,
                        textDecoration = if (isStale) TextDecoration.LineThrough else null,
                    )
                    Column {
                        Text(
                            text = latest.trend.symbol,
                            fontSize = if (compactMode) 26.sp else 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bgColor,
                        )
                        Text(
                            text = GlucoseUnit.unitLabel(settings.useMgdl),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (deltaMgdl != null) {
                        Surface(
                            color = bgColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    text = GlucoseUnit.formatDelta(deltaMgdl, settings.useMgdl),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = bgColor,
                                )
                                Text(
                                    text = latest.trend.labelDe,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // Compact meta + IOB/COB + "+ Eintrag" row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val iobCob = state.iobCob
                        val basalRate = state.currentBasalRate
                        val pump = state.externalPumpStatus
                        val pumpFresh = pump != null && (state.nowMs - pump.timestampMs) <= 3_600_000L
                        if (iobCob.hasActiveIob || iobCob.hasActiveCob || basalRate != null || (pumpFresh && pump?.reservoirUnits != null)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (iobCob.hasActiveIob) {
                                    SmallPill(
                                        text = "IOB ${String.format(Locale.US, "%.2f", iobCob.iobUnits)} IE",
                                        container = scheme.primaryContainer,
                                        content = scheme.onPrimaryContainer,
                                    )
                                }
                                if (iobCob.hasActiveCob) {
                                    SmallPill(
                                        text = "COB ${iobCob.cobGrams.toInt()} g",
                                        container = scheme.secondaryContainer,
                                        content = scheme.onSecondaryContainer,
                                    )
                                }
                                if (basalRate != null) {
                                    SmallPill(
                                        text = "Basal ${String.format(Locale.US, "%.2f", basalRate)}",
                                        container = scheme.tertiaryContainer,
                                        content = scheme.onTertiaryContainer,
                                    )
                                }
                                if (pumpFresh && pump?.reservoirUnits != null) {
                                    SmallPill(
                                        text = "${pump.reservoirUnits.toInt()} E",
                                        container = scheme.surfaceContainerHigh,
                                        content = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        val metaParts = buildList {
                            state.predictions.lastOrNull()?.let { pred ->
                                add("30m ~${GlucoseUnit.formatValue(pred.mgdl, settings.useMgdl)}")
                            }
                            if (settings.sensorStartedAtMs > 0L) {
                                val ageHours = ((state.nowMs - settings.sensorStartedAtMs) / 3_600_000L).coerceAtLeast(0L)
                                add("Sensor ${ageHours / 24}T ${ageHours % 24}h")
                            }
                        }
                        if (metaParts.isNotEmpty()) {
                            Text(
                                text = metaParts.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onAddTreatmentClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Eintrag", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartPanelCard(
    state: MainUiState,
    onSelectWindow: (ChartWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ChartWindow.entries.forEachIndexed { idx, window ->
                    SegmentedButton(
                        selected = state.chartWindow == window,
                        onClick = { onSelectWindow(window) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = ChartWindow.entries.size,
                        ),
                    ) {
                        Text(window.label, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            GlucoseChart(
                readings = state.readingsInWindow,
                treatments = if (settings.showChartTreatments) state.treatmentsInWindow else emptyList(),
                predictions = state.predictions,
                windowMs = state.chartWindow.durationMs,
                nowMs = state.nowMs,
                useMgdl = settings.useMgdl,
                targetLowMgdl = settings.targetLowMgdl,
                targetHighMgdl = settings.targetHighMgdl,
                pointsOnly = settings.chartPointsOnly,
                showGrid = settings.showChartGrid,
                showAverageLine = settings.showChartAverageLine,
                chartMaxMgdl = settings.chartMaxMgdl,
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
