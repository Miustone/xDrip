package dev.xdripyou.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.BuildConfig
import dev.xdripyou.app.domain.CamApsParser
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.SourceUnitMode
import dev.xdripyou.app.domain.ThemeModeOption
import dev.xdripyou.app.ui.MainUiState
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class SettingsSection(val title: String) {
    HUB("Optionen & Quelle"),
    SOURCES("Datenquellen & Sensor"),
    ALARMS("Alarme & Klingeltöne"),
    DISPLAY("Anzeige & Material 3 You"),
    CLOUD("Cloud, Broadcast & Backup"),
    DIAGNOSTICS("Diagnose, Labor & Info"),
}

private data class SettingsCategoryMeta(
    val section: SettingsSection,
    val title: String,
    val subtitle: String,
    val keywords: String,
    val icon: ImageVector,
)

private val settingsCategories = listOf(
    SettingsCategoryMeta(
        section = SettingsSection.SOURCES,
        title = "Datenquellen & Sensor",
        subtitle = "CamAPS FX Companion, Berechtigungen, Jam-Schutz, Sensor-Alter",
        keywords = "camaps companion notification listener benutzerrechte sensor start veraltet stale jam paket",
        icon = Icons.Rounded.Sensors,
    ),
    SettingsCategoryMeta(
        section = SettingsSection.ALARMS,
        title = "Alarme & Klingeltöne",
        subtitle = "Eigener Alarmton, Anruf-Stummschaltung, Stale-Alarm-Sperre",
        keywords = "alarm ton klingelton ringtone anruf telefonat stumm dnd stale veraltet vibration",
        icon = Icons.Rounded.NotificationsActive,
    ),
    SettingsCategoryMeta(
        section = SettingsSection.DISPLAY,
        title = "Anzeige, Chart & Material 3 You",
        subtitle = "Einheit (mg/dL / mmol/L), Zielbereich, Prognose, Nachttisch-Modus",
        keywords = "mgdl mmol einheit zielbereich chart vorhersage treatment dynamic color material you theme hell dunkel bildschirm wach",
        icon = Icons.Rounded.Palette,
    ),
    SettingsCategoryMeta(
        section = SettingsSection.CLOUD,
        title = "Cloud, Broadcast & Export",
        subtitle = "Nightscout Follower & Upload, AAPS-Broadcast, CSV & JSON Backup",
        keywords = "nightscout follower upload secret aaps broadcast widget csv json export import backup",
        icon = Icons.Rounded.CloudSync,
    ),
    SettingsCategoryMeta(
        section = SettingsSection.DIAGNOSTICS,
        title = "Diagnose, Labor & Info",
        subtitle = "Live-Protokoll, CamAPS-Simulator, Demo-Kurve, Version & Changelog",
        keywords = "diagnose log protokoll test simulator demo version changelog fork github",
        icon = Icons.Rounded.BugReport,
    ),
)

@Composable
fun SettingsScreen(
    section: SettingsSection,
    onSelectSection: (SettingsSection) -> Unit,
    state: MainUiState,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenDndPolicy: () -> Unit,
    onSetCollectorEnabled: (Boolean) -> Unit,
    onSetForegroundService: (Boolean) -> Unit,
    onSetCompactOngoing: (Boolean) -> Unit,
    onSetSourceUnitMode: (SourceUnitMode) -> Unit,
    onSetJamProtection: (Boolean) -> Unit,
    onSetCustomPackages: (String) -> Unit,
    onSetStaleThresholdMinutes: (Int) -> Unit,
    onStartSensorSession: () -> Unit,
    onStopSensorSession: () -> Unit,
    onPickAlertRingtone: () -> Unit,
    onResetAlertRingtone: () -> Unit,
    onSetNoAlarmsDuringCalls: (Boolean) -> Unit,
    onSetDisableAlertsStale: (Boolean, Int) -> Unit,
    onSetNightscoutConfig: (String, String, Boolean, Boolean) -> Unit,
    onSyncNightscoutNow: ((String) -> Unit) -> Unit,
    onSetLocalBroadcastEnabled: (Boolean) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onSimulateNotification: (String, (String) -> Unit) -> Unit,
    onInjectManualReading: (Double) -> Unit,
    onSeedDemo: () -> Unit,
    onClearAllReadings: () -> Unit,
    onSetUseMgdl: (Boolean) -> Unit,
    onSetTargetRange: (Double, Double) -> Unit,
    onSetPredictionCurve: (Boolean) -> Unit,
    onSetShowChartTreatments: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetThemeMode: (ThemeModeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        SettingsSection.HUB -> SettingsHubPage(
            state = state,
            onSelectSection = onSelectSection,
            modifier = modifier,
        )
        SettingsSection.SOURCES -> SettingsSourcesPage(
            state = state,
            onOpenNotificationListener = onOpenNotificationListener,
            onRequestPostNotifications = onRequestPostNotifications,
            onRequestBatteryExemption = onRequestBatteryExemption,
            onSetCollectorEnabled = onSetCollectorEnabled,
            onSetForegroundService = onSetForegroundService,
            onSetCompactOngoing = onSetCompactOngoing,
            onSetSourceUnitMode = onSetSourceUnitMode,
            onSetJamProtection = onSetJamProtection,
            onSetCustomPackages = onSetCustomPackages,
            onSetStaleThresholdMinutes = onSetStaleThresholdMinutes,
            onStartSensorSession = onStartSensorSession,
            onStopSensorSession = onStopSensorSession,
            modifier = modifier,
        )
        SettingsSection.ALARMS -> SettingsAlarmsOptionsPage(
            state = state,
            onOpenDndPolicy = onOpenDndPolicy,
            onPickAlertRingtone = onPickAlertRingtone,
            onResetAlertRingtone = onResetAlertRingtone,
            onSetNoAlarmsDuringCalls = onSetNoAlarmsDuringCalls,
            onSetDisableAlertsStale = onSetDisableAlertsStale,
            modifier = modifier,
        )
        SettingsSection.DISPLAY -> SettingsDisplayPage(
            state = state,
            onSetUseMgdl = onSetUseMgdl,
            onSetTargetRange = onSetTargetRange,
            onSetPredictionCurve = onSetPredictionCurve,
            onSetShowChartTreatments = onSetShowChartTreatments,
            onSetKeepScreenOn = onSetKeepScreenOn,
            onSetDynamicColor = onSetDynamicColor,
            onSetThemeMode = onSetThemeMode,
            modifier = modifier,
        )
        SettingsSection.CLOUD -> SettingsCloudPage(
            state = state,
            onSetNightscoutConfig = onSetNightscoutConfig,
            onSyncNightscoutNow = onSyncNightscoutNow,
            onSetLocalBroadcastEnabled = onSetLocalBroadcastEnabled,
            onExportJson = onExportJson,
            onImportJson = onImportJson,
            onExportCsv = onExportCsv,
            modifier = modifier,
        )
        SettingsSection.DIAGNOSTICS -> SettingsDiagnosticsPage(
            state = state,
            onSimulateNotification = onSimulateNotification,
            onInjectManualReading = onInjectManualReading,
            onSeedDemo = onSeedDemo,
            onClearAllReadings = onClearAllReadings,
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsHubPage(
    state: MainUiState,
    onSelectSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) settingsCategories
        else settingsCategories.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.keywords.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Optionen durchsuchen (z. B. Nightscout, Klingelton)…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Leeren")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer.copy(alpha = 0.55f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (state.permissions.coreReady) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (state.permissions.coreReady) glucoseColors.inRange else glucoseColors.high,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.permissions.coreReady) "CamAPS FX Empfang bereit" else "Berechtigungen prüfen",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            val unit = GlucoseUnit.unitLabel(state.settings.useMgdl)
                            val nsState = if (state.settings.nightscoutFollowEnabled || state.settings.nightscoutUploadEnabled) {
                                "Nightscout aktiv"
                            } else {
                                "Lokal"
                            }
                            Text(
                                text = "Einheit: $unit · $nsState · v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        items(filtered, key = { it.section.name }) { cat ->
            Card(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onSelectSection(cat.section) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = null,
                            tint = scheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = cat.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSourcesPage(
    state: MainUiState,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onSetCollectorEnabled: (Boolean) -> Unit,
    onSetForegroundService: (Boolean) -> Unit,
    onSetCompactOngoing: (Boolean) -> Unit,
    onSetSourceUnitMode: (SourceUnitMode) -> Unit,
    onSetJamProtection: (Boolean) -> Unit,
    onSetCustomPackages: (String) -> Unit,
    onSetStaleThresholdMinutes: (Int) -> Unit,
    onStartSensorSession: () -> Unit,
    onStopSensorSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    var customPkgsText by remember(settings.customPackages) { mutableStateOf(settings.customPackages) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Berechtigungen & Hintergrund", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusRow("Benachrichtigungszugriff (CamAPS)", state.permissions.notificationListenerEnabled, "Öffnen", onOpenNotificationListener)
                    StatusRow("App-Benachrichtigungen", state.permissions.postNotificationsGranted, "Erlauben", onRequestPostNotifications)
                    StatusRow("Akku-Optimierung aus", state.permissions.ignoringBatteryOptimizations, "Öffnen", onRequestBatteryExemption)
                    SwitchRow("Dauerhafte Status-Benachrichtigung", "Hält Monitoring & Timer aktiv", settings.foregroundServiceEnabled, onSetForegroundService)
                    SwitchRow("Kompakte Status-Benachrichtigung", "Zeigt nur Wert, Pfeil & Delta", settings.compactOngoingNotification, onSetCompactOngoing)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("CamAPS FX Companion Parser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("CamAPS Collector aktiv", null, settings.collectorEnabled, onSetCollectorEnabled)

                    Text("Quell-Einheit", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceUnitMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.sourceUnitMode == mode,
                                onClick = { onSetSourceUnitMode(mode) },
                                label = { Text(mode.labelDe) },
                            )
                        }
                    }

                    SwitchRow("Jam-Schutz (> 6 identische Werte)", "Blockiert festgefrorene Werte wie in xDrip", settings.jamProtection, onSetJamProtection)

                    Text("„Veraltet“-Schwelle: ${settings.staleThresholdMinutes} Min.", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.staleThresholdMinutes.toFloat(),
                        onValueChange = { onSetStaleThresholdMinutes(it.roundToInt()) },
                        valueRange = 6f..30f,
                        steps = 23,
                    )

                    Text(
                        "Unterstützte CamAPS-Pakete: ${CamApsParser.defaultCamApsPackages.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customPkgsText,
                            onValueChange = { customPkgsText = it },
                            label = { Text("Zusätzliche Package-IDs") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onSetCustomPackages(customPkgsText) }) { Text("OK") }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Sensor-Laufzeit (Session)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (settings.sensorStartedAtMs > 0L) {
                        val hours = ((state.nowMs - settings.sensorStartedAtMs) / 3_600_000L).coerceAtLeast(0L)
                        Text("Aktiver Sensor: ${hours / 24} Tage, ${hours % 24} Stunden", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onStartSensorSession, modifier = Modifier.weight(1f)) { Text("Neu starten") }
                            OutlinedButton(onClick = onStopSensorSession, modifier = Modifier.weight(1f)) { Text("Stoppen") }
                        }
                    } else {
                        Text("Optional: Startzeit deines aktuellen Sensors auf der Übersicht anzeigen.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Button(onClick = onStartSensorSession) { Text("Sensor jetzt als gestartet markieren") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAlarmsOptionsPage(
    state: MainUiState,
    onOpenDndPolicy: () -> Unit,
    onPickAlertRingtone: () -> Unit,
    onResetAlertRingtone: () -> Unit,
    onSetNoAlarmsDuringCalls: (Boolean) -> Unit,
    onSetDisableAlertsStale: (Boolean, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Alarmton & System-Rechte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusRow("Nicht-Stören-Umgehung (DND)", state.permissions.dndPolicyAccessGranted, "Öffnen", onOpenDndPolicy)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Standard-Alarmton", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (settings.defaultAlertSoundUri.isBlank()) "xDrip Standard-Ton" else "Eigener Systemton gewählt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = onPickAlertRingtone) { Text("Wählen") }
                    }
                    if (settings.defaultAlertSoundUri.isNotBlank()) {
                        TextButton(onClick = onResetAlertRingtone) { Text("Auf xDrip Standard-Ton zurücksetzen") }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Schutzregeln", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow(
                        "Während Telefonaten stumm",
                        "Unterdrückt akustische Alarme & Vibration bei aktivem Gespräch",
                        settings.noAlarmsDuringCalls,
                        onSetNoAlarmsDuringCalls,
                    )
                    SwitchRow(
                        "Keine Alarme bei veralteten Daten",
                        "Sperrt Schwellwertalarme, wenn Messwerte älter als ${settings.disableAlertsStaleMinutes} Min. sind",
                        settings.disableAlertsStaleData,
                        { enabled -> onSetDisableAlertsStale(enabled, settings.disableAlertsStaleMinutes) },
                    )
                    if (settings.disableAlertsStaleData) {
                        Slider(
                            value = settings.disableAlertsStaleMinutes.toFloat(),
                            onValueChange = { onSetDisableAlertsStale(true, it.roundToInt()) },
                            valueRange = 10f..45f,
                            steps = 6,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDisplayPage(
    state: MainUiState,
    onSetUseMgdl: (Boolean) -> Unit,
    onSetTargetRange: (Double, Double) -> Unit,
    onSetPredictionCurve: (Boolean) -> Unit,
    onSetShowChartTreatments: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetThemeMode: (ThemeModeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Einheit & Zielbereich", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = settings.useMgdl,
                            onClick = { onSetUseMgdl(true) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("mg/dL") }
                        SegmentedButton(
                            selected = !settings.useMgdl,
                            onClick = { onSetUseMgdl(false) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("mmol/L") }
                    }
                    Text(
                        "Zielbereich: ${GlucoseUnit.formatValue(settings.targetLowMgdl, settings.useMgdl)} – ${GlucoseUnit.formatValue(settings.targetHighMgdl, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    RangeSlider(
                        value = settings.targetLowMgdl.toFloat()..settings.targetHighMgdl.toFloat(),
                        onValueChange = { r -> onSetTargetRange(r.start.roundToInt().toDouble(), r.endInclusive.roundToInt().toDouble()) },
                        valueRange = 55f..250f,
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Chart & Nachttisch-Modus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("30-Min. Vorhersage-Kurve", "Gestrichelte Trend-Prognose im Chart", settings.predictionCurveEnabled, onSetPredictionCurve)
                    SwitchRow("Therapie-Marker im Chart", "Zeigt Insulin (IE) & KH (g) direkt im Verlauf", settings.showChartTreatments, onSetShowChartTreatments)
                    SwitchRow("Bildschirm wach halten", "Verhindert Auto-Sperre solange xDrip 2 You geöffnet ist", settings.keepScreenOn, onSetKeepScreenOn)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Material 3 You Design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("Material You (Dynamische Farben)", "Passt Farben automatisch ans Pixel-Wallpaper an", settings.dynamicColorEnabled, onSetDynamicColor)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeModeOption.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemeModeOption.entries.size),
                            ) { Text(mode.labelDe) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCloudPage(
    state: MainUiState,
    onSetNightscoutConfig: (String, String, Boolean, Boolean) -> Unit,
    onSyncNightscoutNow: ((String) -> Unit) -> Unit,
    onSetLocalBroadcastEnabled: (Boolean) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    var nsUrlText by remember(settings.nightscoutUrl) { mutableStateOf(settings.nightscoutUrl) }
    var nsSecretText by remember(settings.nightscoutApiSecret) { mutableStateOf(settings.nightscoutApiSecret) }
    var nsFeedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Nightscout (Follower & Upload)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = nsUrlText,
                        onValueChange = { nsUrlText = it },
                        label = { Text("Nightscout-URL (z. B. https://dein.ns.site)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = nsSecretText,
                        onValueChange = { nsSecretText = it },
                        label = { Text("API-Secret (wird SHA-1 gehasht)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchRow("Als Follower abrufen (alle 5 Min.)", null, settings.nightscoutFollowEnabled) { follow ->
                        onSetNightscoutConfig(nsUrlText, nsSecretText, follow, settings.nightscoutUploadEnabled)
                    }
                    SwitchRow("Werte & Einträge hochladen", null, settings.nightscoutUploadEnabled) { upload ->
                        onSetNightscoutConfig(nsUrlText, nsSecretText, settings.nightscoutFollowEnabled, upload)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onSetNightscoutConfig(nsUrlText, nsSecretText, settings.nightscoutFollowEnabled, settings.nightscoutUploadEnabled)
                                nsFeedback = "Konfiguration gespeichert"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Speichern") }
                        Button(
                            onClick = {
                                onSetNightscoutConfig(nsUrlText, nsSecretText, settings.nightscoutFollowEnabled, settings.nightscoutUploadEnabled)
                                onSyncNightscoutNow { res -> nsFeedback = res }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Jetzt syncen") }
                    }
                    nsFeedback?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = scheme.primary) }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Inter-App Broadcast & Export", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow(
                        "Lokaler xDrip-Broadcast (AAPS / Watches)",
                        "Sendet com.eveningoutpost.dexdrip.BgEstimate bei neuen Werten",
                        settings.localBroadcastEnabled,
                        onSetLocalBroadcastEnabled,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("JSON Export")
                        }
                        OutlinedButton(onClick = onImportJson, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("JSON Import")
                        }
                    }
                    OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.TableView, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Messwerte als CSV exportieren")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDiagnosticsPage(
    state: MainUiState,
    onSimulateNotification: (String, (String) -> Unit) -> Unit,
    onInjectManualReading: (Double) -> Unit,
    onSeedDemo: () -> Unit,
    onClearAllReadings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    var simText by remember { mutableStateOf("112 mg/dL →") }
    var manualText by remember { mutableStateOf("105") }
    var simFeedback by remember { mutableStateOf<String?>(null) }
    var showChangelog by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("App-Info & Changelog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "xDrip 2 You · Version ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Lokaler Kotlin + Jetpack Compose (Material 3 You) Fork von NightscoutFoundation/xDrip (Branch: kotlin-jetpack-you).",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { showChangelog = true }) {
                        Icon(Icons.Rounded.History, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Changelog anzeigen")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Test- & Simulations-Labor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = simText,
                            onValueChange = { simText = it },
                            label = { Text("CamAPS-Text simulieren") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { onSimulateNotification(simText) { res -> simFeedback = res } }) {
                            Text("Test")
                        }
                    }
                    simFeedback?.let { Text("Ergebnis: $it", style = MaterialTheme.typography.labelMedium, color = scheme.primary) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = manualText,
                            onValueChange = { manualText = it },
                            label = { Text("Manueller Wert (${GlucoseUnit.unitLabel(settings.useMgdl)})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                val parsed = manualText.replace(',', '.').toDoubleOrNull() ?: return@OutlinedButton
                                onInjectManualReading(GlucoseUnit.toMgdl(parsed, fromMgdl = settings.useMgdl))
                            },
                        ) { Text("Einfügen") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onSeedDemo, modifier = Modifier.weight(1f)) { Text("12h Demo-Kurve") }
                        OutlinedButton(onClick = onClearAllReadings, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Daten leeren")
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Live CamAPS Collector-Protokoll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.collectorEvents.isEmpty()) {
                        Text("Noch keine Benachrichtigungen protokolliert.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    } else {
                        state.collectorEvents.take(15).forEach { ev ->
                            Surface(
                                color = scheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("${ev.status} · ${ev.packageName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                        Text(timeFmt.format(Date(ev.timestampMs)), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                    }
                                    Text(ev.message, style = MaterialTheme.typography.bodySmall)
                                    if (ev.rawTexts.isNotBlank()) {
                                        Text("Text: ${ev.rawTexts}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("Changelog · xDrip 2 You") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("[26.11.3] · Optionsmenüs & Sensor-Tools", fontWeight = FontWeight.Bold)
                    Text("• Hierarchischer Material-3-You-Options-Hub mit Suchleiste\n• Eigener Alarm-Klingelton, Anruf-Stummschaltung & Stale-Alarm-Sperre\n• Sensor-Laufzeit-Tracking, Nachttisch-Modus & CSV-Export")
                    Text("[26.11.2] · xDrip 2 You & Ökosystem", fontWeight = FontWeight.Bold)
                    Text("• Rebrand zu xDrip 2 You\n• Therapie-Tagebuch (IE/KH/BZ) + Chart-Marker\n• 30-Min.-Vorhersage, Niedrig-Vorhersage- & Anhaltend-Hoch-Alarm\n• Nightscout Follower/Upload, AAPS-Broadcast, Material-You-Widget & JSON-Backup")
                    Text("[26.11.1] · Kotlin + Jetpack Compose Basis", fontWeight = FontWeight.Bold)
                    Text("• Komplette Neuimplementierung in Kotlin + Jetpack Compose Material 3 You\n• CamAPS FX Companion Collector, Alarm-Engine & Foreground Service")
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) { Text("Schließen") }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val glucoseColors = LocalGlucoseColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (ok) glucoseColors.inRange else glucoseColors.high,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(if (ok) "Prüfen" else actionLabel)
        }
    }
}
