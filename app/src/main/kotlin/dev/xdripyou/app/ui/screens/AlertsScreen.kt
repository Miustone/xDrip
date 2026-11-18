package dev.xdripyou.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.AlertProfile
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.ui.MainUiState
import dev.xdripyou.app.ui.components.ActiveAlertBanner
import dev.xdripyou.app.ui.components.AlertEditorSheet
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsScreen(
    state: MainUiState,
    onSnoozeActive: (Int) -> Unit,
    onStopActive: () -> Unit,
    onSilenceScope: (String, Int) -> Unit,
    onUpsertRule: (AlertRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onTriggerTestAlert: (String?) -> Unit,
    onSetProfile: (AlertProfile) -> Unit,
    onSetSmartAlerting: (Boolean) -> Unit,
    onSetSmartSnoozing: (Boolean) -> Unit,
    onSetMissedReading: (Boolean, Int, Int) -> Unit,
    onSetRisingAlert: (Boolean, Double) -> Unit,
    onSetFallingAlert: (Boolean, Double) -> Unit,
    onSetLowPrediction: (Boolean, Double, Int) -> Unit,
    onSetPersistentHigh: (Boolean, Double, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    val settings = state.settings

    var editingRule by remember { mutableStateOf<AlertRule?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Neuer Alarm")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.activeAlert?.let { alert ->
                item {
                    ActiveAlertBanner(
                        alert = alert,
                        useMgdl = settings.useMgdl,
                        nowMs = state.nowMs,
                        onSnooze = onSnoozeActive,
                        onStop = onStopActive,
                    )
                }
            }

            // Global Silence / Snooze Controls
            item {
                val allRem = ((settings.alertsDisabledUntilMs - state.nowMs) / 60_000L).coerceAtLeast(0L)
                val lowRem = ((settings.lowAlertsDisabledUntilMs - state.nowMs) / 60_000L).coerceAtLeast(0L)
                val highRem = ((settings.highAlertsDisabledUntilMs - state.nowMs) / 60_000L).coerceAtLeast(0L)

                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsOff, null, tint = scheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Alarme stummschalten",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        SilenceRow("Alle Alarme", allRem) { onSilenceScope("all", it) }
                        SilenceRow("Nur Niedrig-Alarme", lowRem) { onSilenceScope("low", it) }
                        SilenceRow("Nur Hoch-Alarme", highRem) { onSilenceScope("high", it) }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Schwellwert-Alarme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedButton(onClick = { onTriggerTestAlert(null) }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Alarm testen")
                    }
                }
            }

            items(state.alertRules, key = { it.uuid }) { rule ->
                val accent = if (rule.isHigh) glucoseColors.high else glucoseColors.low
                Card(shape = RoundedCornerShape(22.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = rule.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (rule.overrideSilent) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = scheme.errorContainer,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.VolumeOff,
                                                null,
                                                Modifier.size(12.dp),
                                                tint = scheme.onErrorContainer,
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                "Umgeht Lautlos",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onErrorContainer,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            val cmp = if (rule.isHigh) "≥" else "≤"
                            val threshStr = GlucoseUnit.formatValue(rule.thresholdMgdl, settings.useMgdl)
                            val unit = GlucoseUnit.unitLabel(settings.useMgdl)
                            val timeLabel = if (rule.allDay) {
                                "Ganztägig"
                            } else {
                                "${rule.startMinutes / 60}:00–${rule.endMinutes / 60}:00"
                            }
                            Text(
                                text = "$cmp $threshStr $unit · alle ${rule.minutesBetween} Min. · $timeLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = accent,
                            )
                        }
                        IconButton(onClick = { onTriggerTestAlert(rule.uuid) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Testen")
                        }
                        IconButton(onClick = { editingRule = rule }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Bearbeiten")
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { enabled -> onUpsertRule(rule.copy(enabled = enabled)) },
                        )
                    }
                }
            }

            // Missed Reading Card
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Verpasste Messwerte",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Warnt nach ${settings.missedReadingMinutes} Min. ohne neuen Wert",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.missedReadingEnabled,
                                onCheckedChange = { enabled ->
                                    onSetMissedReading(
                                        enabled,
                                        settings.missedReadingMinutes,
                                        settings.missedReadingSnoozeMinutes,
                                    )
                                },
                            )
                        }
                        if (settings.missedReadingEnabled) {
                            Slider(
                                value = settings.missedReadingMinutes.toFloat(),
                                onValueChange = {
                                    onSetMissedReading(
                                        true,
                                        it.roundToInt(),
                                        settings.missedReadingSnoozeMinutes,
                                    )
                                },
                                valueRange = 10f..60f,
                                steps = 9,
                            )
                        }
                    }
                }
            }

            // Prediction & Persistent High Card
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Vorhersage & Anhaltend Hoch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Niedrig-Vorhersage")
                                Text(
                                    "Warnt wenn in ≤ ${settings.lowPredictionHorizonMinutes} Min. unter ${GlucoseUnit.formatValue(settings.lowPredictionThresholdMgdl, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.lowPredictionEnabled,
                                onCheckedChange = { enabled ->
                                    onSetLowPrediction(
                                        enabled,
                                        settings.lowPredictionThresholdMgdl,
                                        settings.lowPredictionHorizonMinutes,
                                    )
                                },
                            )
                        }
                        if (settings.lowPredictionEnabled) {
                            Slider(
                                value = settings.lowPredictionHorizonMinutes.toFloat(),
                                onValueChange = {
                                    onSetLowPrediction(
                                        true,
                                        settings.lowPredictionThresholdMgdl,
                                        it.roundToInt(),
                                    )
                                },
                                valueRange = 10f..35f,
                                steps = 4,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Anhaltend Hoch")
                                Text(
                                    "Warnt nach ${settings.persistentHighMinutes} Min. über ${GlucoseUnit.formatValue(settings.persistentHighThresholdMgdl, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.persistentHighEnabled,
                                onCheckedChange = { enabled ->
                                    onSetPersistentHigh(
                                        enabled,
                                        settings.persistentHighThresholdMgdl,
                                        settings.persistentHighMinutes,
                                    )
                                },
                            )
                        }
                        if (settings.persistentHighEnabled) {
                            Slider(
                                value = settings.persistentHighMinutes.toFloat(),
                                onValueChange = {
                                    onSetPersistentHigh(
                                        true,
                                        settings.persistentHighThresholdMgdl,
                                        it.roundToInt(),
                                    )
                                },
                                valueRange = 30f..180f,
                                steps = 4,
                            )
                        }
                    }
                }
            }

            // Rapid Change & Profile Card
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Trend-Alarme & Verhalten",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Schneller Abfall (≥ ${settings.fallingRateMgdlPerMin} mg/dL/Min.)")
                                Text(
                                    "Frühwarnung bei stark fallendem Glukosewert",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.fallingAlertEnabled,
                                onCheckedChange = { onSetFallingAlert(it, settings.fallingRateMgdlPerMin) },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Schneller Anstieg (≥ ${settings.risingRateMgdlPerMin} mg/dL/Min.)")
                                Text(
                                    "Frühwarnung bei stark steigendem Glukosewert",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.risingAlertEnabled,
                                onCheckedChange = { onSetRisingAlert(it, settings.risingRateMgdlPerMin) },
                            )
                        }

                        Text(
                            "Lautstärke-Profil",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AlertProfile.entries.forEach { profile ->
                                FilterChip(
                                    selected = settings.alertProfile == profile,
                                    onClick = { onSetProfile(profile) },
                                    label = { Text(profile.labelDe) },
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Smart Alerting")
                                Text(
                                    "Unterdrückt neuen Alarm, wenn Trend bereits zum Ziel zurückläuft",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = settings.smartAlerting, onCheckedChange = onSetSmartAlerting)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Smart Snoozing")
                                Text(
                                    "Wiederholt geschlummerten Alarm nicht bei eindeutiger Besserung",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = settings.smartSnoozing, onCheckedChange = onSetSmartSnoozing)
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet || editingRule != null) {
        AlertEditorSheet(
            existing = editingRule,
            useMgdl = settings.useMgdl,
            onDismiss = {
                showCreateSheet = false
                editingRule = null
            },
            onSave = { rule ->
                onUpsertRule(rule)
                showCreateSheet = false
                editingRule = null
            },
            onDelete = editingRule?.let { existing ->
                {
                    onDeleteRule(existing.uuid)
                    editingRule = null
                }
            },
        )
    }
}

@Composable
private fun SilenceRow(
    label: String,
    remainingMins: Long,
    onSilence: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (remainingMins > 0) {
                Text(
                    "Stumm für noch $remainingMins Min.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (remainingMins > 0) {
                AssistChip(onClick = { onSilence(0) }, label = { Text("Aktivieren") })
            } else {
                AssistChip(onClick = { onSilence(30) }, label = { Text("30m") })
                AssistChip(onClick = { onSilence(60) }, label = { Text("1h") })
                AssistChip(onClick = { onSilence(120) }, label = { Text("2h") })
            }
        }
    }
}
