package dev.xdripyou.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xdripyou.app.domain.ChartWindow
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
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    val settings = state.settings
    val latest = state.latestReading
    val deltaMgdl = state.latestDeltaMgdl
    val stats = state.windowStats
    var showTreatmentSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.activeAlert?.let { alert ->
            item {
                ActiveAlertBanner(
                    alert = alert,
                    useMgdl = settings.useMgdl,
                    nowMs = state.nowMs,
                    onSnooze = onSnoozeAlert,
                    onStop = onStopAlert,
                )
            }
        }

        if (!state.permissions.coreReady || !state.permissions.ignoringBatteryOptimizations) {
            item {
                SetupChecklistCard(
                    notificationListenerGranted = state.permissions.notificationListenerEnabled,
                    postNotificationsGranted = state.permissions.postNotificationsGranted,
                    batteryIgnored = state.permissions.ignoringBatteryOptimizations,
                    onOpenNotificationListener = onOpenNotificationListener,
                    onRequestPostNotifications = onRequestPostNotifications,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                )
            }
        }

        // Hero Glucose Card
        item {
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
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = scheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = latest?.source ?: "CamAPS FX Companion",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSecondaryContainer,
                                )
                            }
                        }

                        if (latest != null) {
                            val mins = latest.minutesAgoAt(state.nowMs)
                            val ageLabel = if (mins == 0L) "gerade eben" else "vor $mins Min."
                            Surface(
                                color = if (isStale) scheme.errorContainer else scheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    text = if (isStale) "Veraltet · $ageLabel" else ageLabel,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isStale) scheme.onErrorContainer else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (latest == null) {
                        Text(
                            text = "---",
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Warte auf die erste CamAPS FX Benachrichtigung…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = onSeedDemo) {
                                Icon(Icons.Rounded.Timeline, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Demo-Kurve", maxLines = 1)
                            }
                            OutlinedButton(onClick = onOpenSettingsTab) {
                                Icon(Icons.Rounded.Science, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quelle & Test", maxLines = 1)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = GlucoseUnit.formatValue(latest.mgdl, settings.useMgdl),
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1.5).sp,
                                color = bgColor,
                                textDecoration = if (isStale) TextDecoration.LineThrough else null,
                            )
                            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                Text(
                                    text = latest.trend.symbol,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = bgColor,
                                )
                                Text(
                                    text = GlucoseUnit.unitLabel(settings.useMgdl),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (deltaMgdl != null) {
                                Surface(
                                    color = bgColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.End,
                                    ) {
                                        Text(
                                            text = GlucoseUnit.formatDelta(deltaMgdl, settings.useMgdl),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = bgColor,
                                        )
                                        Text(
                                            text = latest.trend.labelDe,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                val lastPred = state.predictions.lastOrNull()
                                if (lastPred != null) {
                                    Text(
                                        text = "Trend 30m: ~${GlucoseUnit.formatValue(lastPred.mgdl, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.tertiary,
                                    )
                                }
                                if (settings.sensorStartedAtMs > 0L) {
                                    val ageHours = ((state.nowMs - settings.sensorStartedAtMs) / 3_600_000L).coerceAtLeast(0L)
                                    val days = ageHours / 24L
                                    val remHours = ageHours % 24L
                                    Text(
                                        text = "Sensor: ${days}T ${remHours}Std.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { showTreatmentSheet = true },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Eintrag", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Chart Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                    Spacer(Modifier.height(14.dp))
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
                    )
                }
            }
        }

        if (settings.showExtraStatusLine && state.stats24h.count > 0) {
            item {
                val s24 = state.stats24h
                Surface(
                    color = scheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buildString {
                            append("24h: TIR ${s24.inRangePercent.toInt()}%")
                            append(" · Ø ${GlucoseUnit.formatValue(s24.meanMgdl, settings.useMgdl)}")
                            append(" · GMI ${String.format(Locale.US, "%.1f", s24.gmiPercent)}%")
                            append(" · CV ${s24.cvPercent.toInt()}%")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // Stats 2x2 Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        title = "Im Ziel (${state.chartWindow.label})",
                        value = if (stats.count == 0) "--" else "${stats.inRangePercent.toInt()} %",
                        subtitle = "${GlucoseUnit.formatValue(settings.targetLowMgdl, settings.useMgdl)}–${GlucoseUnit.formatValue(settings.targetHighMgdl, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)}",
                        accent = glucoseColors.inRange,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        title = "Durchschnitt",
                        value = if (stats.count == 0) "--" else GlucoseUnit.formatValue(stats.meanMgdl, settings.useMgdl),
                        subtitle = GlucoseUnit.unitLabel(settings.useMgdl),
                        accent = scheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        title = "GMI (geschätzt)",
                        value = if (stats.count == 0) "--" else String.format(Locale.US, "%.1f %%", stats.gmiPercent),
                        subtitle = "${stats.count} Messwerte",
                        accent = scheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        title = "Variabilität (CV)",
                        value = if (stats.count == 0) "--" else "${stats.cvPercent.toInt()} %",
                        subtitle = if (stats.cvPercent <= 36.0 && stats.count > 0) "Stabil (≤ 36 %)" else "Ziel: ≤ 36 %",
                        accent = if (stats.cvPercent > 36.0) glucoseColors.high else glucoseColors.inRange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showTreatmentSheet) {
        TreatmentEditorSheet(
            useMgdl = settings.useMgdl,
            onDismiss = { showTreatmentSheet = false },
            onSave = { entry ->
                onAddTreatment(entry)
                showTreatmentSheet = false
            },
        )
    }
}

@Composable
private fun SetupChecklistCard(
    notificationListenerGranted: Boolean,
    postNotificationsGranted: Boolean,
    batteryIgnored: Boolean,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Einrichtung abschließen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onTertiaryContainer,
            )
            Text(
                text = "Für zuverlässigen CamAPS-Empfang und Alarme im Hintergrund:",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onTertiaryContainer,
            )
            PermissionActionRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "CamAPS Benachrichtigungszugriff",
                granted = notificationListenerGranted,
                buttonLabel = "Freigeben",
                onClick = onOpenNotificationListener,
            )
            PermissionActionRow(
                icon = Icons.Rounded.Notifications,
                title = "App-Benachrichtigungen & Alarme",
                granted = postNotificationsGranted,
                buttonLabel = "Erlauben",
                onClick = onRequestPostNotifications,
            )
            PermissionActionRow(
                icon = Icons.Rounded.BatteryChargingFull,
                title = "Akku-Optimierung ignorieren",
                granted = batteryIgnored,
                buttonLabel = "Öffnen",
                onClick = onRequestBatteryExemption,
            )
        }
    }
}

@Composable
private fun PermissionActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
            contentDescription = null,
            tint = if (granted) LocalGlucoseColors.current.inRange else scheme.onTertiaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (!granted) {
            Button(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
