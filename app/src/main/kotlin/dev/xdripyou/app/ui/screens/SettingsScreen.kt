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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material.icons.rounded.RecordVoiceOver
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
import androidx.compose.runtime.mutableIntStateOf
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
import dev.xdripyou.app.domain.BasalSchedule
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
    ALARMS("Alarme, Ton & Sprache"),
    DISPLAY("Anzeige & Material 3 You"),
    CLOUD("Cloud, Broadcast & Speicher"),
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
        subtitle = "CamAPS FX, Companion-Apps, Broadcast-Empfang, Sensor-Ablauf",
        keywords = "camaps dexcom libre guardian aidex companion broadcast empfang notification listener benutzerrechte sensor start ablauf veraltet stale jam paket",
        icon = Icons.Rounded.Sensors,
    ),
    SettingsCategoryMeta(
        section = SettingsSection.ALARMS,
        title = "Alarme, Ton & Sprachausgabe",
        subtitle = "Klingelton, Broadcast-Snooze, Ansteigend-Optionen, Blitz, TTS",
        keywords = "alarm ton klingelton ringtone sprachausgabe tts vorlesen broadcast snooze anruf telefonat stumm dnd stale veraltet vibration blitz taschenlampe wake",
        icon = Icons.Rounded.NotificationsActive,
    ),
    SettingsCategoryMeta(
        section = SettingsSection.DISPLAY,
        title = "Anzeige, IOB/COB & Material 3 You",
        subtitle = "Einheit, Zielbereich, IOB/COB-Wirkung, Chart-Stil, Nachttisch-Modus",
        keywords = "mgdl mmol einheit zielbereich iob cob insulin kohlenhydrate dia chart punkte gitter durchschnitt y-achse vorhersage treatment statuszeile dynamic color material you theme hell dunkel bildschirm wach",
        icon = Icons.Rounded.Palette,
    ),
    SettingsCategoryMeta(
        section = SettingsSection.CLOUD,
        title = "Cloud, Web-Service & Backup",
        subtitle = "Nightscout, xDrip Web-Service (17580), AAPS-Broadcast, CSV & JSON",
        keywords = "nightscout follower upload secret wifi wlan treatments aaps broadcast webservice server 17580 sgv pebble paket widget vorhaltung tage csv json export import backup",
        icon = Icons.Rounded.CloudSync,
    ),
    SettingsCategoryMeta(
        section = SettingsSection.DIAGNOSTICS,
        title = "Diagnose, Assistent & Info",
        subtitle = "Einrichtungs-Assistent, Live-Protokoll, Simulator, Version & Changelog",
        keywords = "diagnose log protokoll test simulator demo assistent wizard onboarding version changelog fork github",
        icon = Icons.Rounded.BugReport,
    ),
)

@Composable
fun SettingsScreen(
    section: SettingsSection,
    onSelectSection: (SettingsSection) -> Unit,
    state: MainUiState,
    onReopenOnboarding: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenDndPolicy: () -> Unit,
    onOpenExactAlarms: () -> Unit,
    onSetCollectorEnabled: (Boolean) -> Unit,
    onSetIncludeAllCompanion: (Boolean) -> Unit,
    onSetForegroundService: (Boolean) -> Unit,
    onSetCompactOngoing: (Boolean) -> Unit,
    onSetSourceUnitMode: (SourceUnitMode) -> Unit,
    onSetJamProtection: (Boolean) -> Unit,
    onSetCustomPackages: (String) -> Unit,
    onSetStaleThresholdMinutes: (Int) -> Unit,
    onStartSensorSession: () -> Unit,
    onStopSensorSession: () -> Unit,
    onSetSensorExpiryConfig: (Int, Boolean) -> Unit,
    onPickAlertRingtone: () -> Unit,
    onResetAlertRingtone: () -> Unit,
    onSetNoAlarmsDuringCalls: (Boolean) -> Unit,
    onSetDisableAlertsStale: (Boolean, Int) -> Unit,
    onSetAscendingOptions: (Boolean, Boolean) -> Unit,
    onSetStartAlertsSnoozed: (Boolean) -> Unit,
    onSetShowButtonsInAlerts: (Boolean) -> Unit,
    onSetWakePhoneDuringAlerts: (Boolean) -> Unit,
    onSetFlashTorchOnAlert: (Boolean) -> Unit,
    onSetSpeechConfig: (Boolean, Boolean, Boolean, Double) -> Unit,
    onTestSpeech: () -> Unit,
    onSetNightscoutConfig: (String, String, Boolean, Boolean) -> Unit,
    onSetNightscoutExtraConfig: (Boolean, Boolean) -> Unit,
    onSetNightscoutDeviceStatusEnabled: (Boolean) -> Unit,
    onSyncNightscoutNow: ((String) -> Unit) -> Unit,
    onSetLocalBroadcastEnabled: (Boolean) -> Unit,
    onSetBroadcastTargetPackage: (String) -> Unit,
    onSetAcceptLocalBroadcastReadings: (Boolean) -> Unit,
    onSetBroadcastSnoozeConfig: (Boolean, Boolean) -> Unit,
    onSetIobCobConfig: (Boolean, Int, Int, Int, Int) -> Unit,
    onSetBolusProfileConfig: (Double, Double, Double) -> Unit,
    onSetBasalProfileConfig: (Boolean, BasalSchedule) -> Unit,
    onSetDexcomShareConfig: (Boolean, Boolean, String, String) -> Unit,
    onSyncDexcomShareNow: ((String) -> Unit) -> Unit,
    onSetLocalWebServiceConfig: (Boolean, Boolean, String) -> Unit,
    onSetRetentionDays: (Int) -> Unit,
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
    onSetChartVisualOptions: (Boolean, Boolean, Boolean, Int) -> Unit,
    onSetShowExtraStatusLine: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetThemeMode: (ThemeModeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        SettingsSection.HUB -> SettingsHubPage(
            state = state,
            onSelectSection = onSelectSection,
            onReopenOnboarding = onReopenOnboarding,
            modifier = modifier,
        )
        SettingsSection.SOURCES -> SettingsSourcesPage(
            state = state,
            onOpenNotificationListener = onOpenNotificationListener,
            onRequestPostNotifications = onRequestPostNotifications,
            onRequestBatteryExemption = onRequestBatteryExemption,
            onOpenExactAlarms = onOpenExactAlarms,
            onSetCollectorEnabled = onSetCollectorEnabled,
            onSetIncludeAllCompanion = onSetIncludeAllCompanion,
            onSetForegroundService = onSetForegroundService,
            onSetCompactOngoing = onSetCompactOngoing,
            onSetSourceUnitMode = onSetSourceUnitMode,
            onSetJamProtection = onSetJamProtection,
            onSetCustomPackages = onSetCustomPackages,
            onSetStaleThresholdMinutes = onSetStaleThresholdMinutes,
            onStartSensorSession = onStartSensorSession,
            onStopSensorSession = onStopSensorSession,
            onSetSensorExpiryConfig = onSetSensorExpiryConfig,
            onSetAcceptLocalBroadcastReadings = onSetAcceptLocalBroadcastReadings,
            onSetDexcomShareConfig = onSetDexcomShareConfig,
            onSyncDexcomShareNow = onSyncDexcomShareNow,
            modifier = modifier,
        )
        SettingsSection.ALARMS -> SettingsAlarmsOptionsPage(
            state = state,
            onOpenDndPolicy = onOpenDndPolicy,
            onPickAlertRingtone = onPickAlertRingtone,
            onResetAlertRingtone = onResetAlertRingtone,
            onSetNoAlarmsDuringCalls = onSetNoAlarmsDuringCalls,
            onSetDisableAlertsStale = onSetDisableAlertsStale,
            onSetAscendingOptions = onSetAscendingOptions,
            onSetStartAlertsSnoozed = onSetStartAlertsSnoozed,
            onSetShowButtonsInAlerts = onSetShowButtonsInAlerts,
            onSetWakePhoneDuringAlerts = onSetWakePhoneDuringAlerts,
            onSetFlashTorchOnAlert = onSetFlashTorchOnAlert,
            onSetSpeechConfig = onSetSpeechConfig,
            onTestSpeech = onTestSpeech,
            onSetBroadcastSnoozeConfig = onSetBroadcastSnoozeConfig,
            modifier = modifier,
        )
        SettingsSection.DISPLAY -> SettingsDisplayPage(
            state = state,
            onSetUseMgdl = onSetUseMgdl,
            onSetTargetRange = onSetTargetRange,
            onSetPredictionCurve = onSetPredictionCurve,
            onSetShowChartTreatments = onSetShowChartTreatments,
            onSetChartVisualOptions = onSetChartVisualOptions,
            onSetShowExtraStatusLine = onSetShowExtraStatusLine,
            onSetKeepScreenOn = onSetKeepScreenOn,
            onSetIobCobConfig = onSetIobCobConfig,
            onSetBolusProfileConfig = onSetBolusProfileConfig,
            onSetBasalProfileConfig = onSetBasalProfileConfig,
            onSetDynamicColor = onSetDynamicColor,
            onSetThemeMode = onSetThemeMode,
            modifier = modifier,
        )
        SettingsSection.CLOUD -> SettingsCloudPage(
            state = state,
            onSetNightscoutConfig = onSetNightscoutConfig,
            onSetNightscoutExtraConfig = onSetNightscoutExtraConfig,
            onSetNightscoutDeviceStatusEnabled = onSetNightscoutDeviceStatusEnabled,
            onSyncNightscoutNow = onSyncNightscoutNow,
            onSetLocalBroadcastEnabled = onSetLocalBroadcastEnabled,
            onSetBroadcastTargetPackage = onSetBroadcastTargetPackage,
            onSetLocalWebServiceConfig = onSetLocalWebServiceConfig,
            onSetRetentionDays = onSetRetentionDays,
            onExportJson = onExportJson,
            onImportJson = onImportJson,
            onExportCsv = onExportCsv,
            modifier = modifier,
        )
        SettingsSection.DIAGNOSTICS -> SettingsDiagnosticsPage(
            state = state,
            onReopenOnboarding = onReopenOnboarding,
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
    onReopenOnboarding: () -> Unit,
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
                placeholder = { Text("Optionen durchsuchen (z. B. Nightscout, TTS, Sensor)…") },
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
                                text = if (state.permissions.coreReady) "Empfang & Alarme bereit" else "Berechtigungen prüfen",
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
                        OutlinedButton(onClick = onReopenOnboarding) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Assistent")
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
    onOpenExactAlarms: () -> Unit,
    onSetCollectorEnabled: (Boolean) -> Unit,
    onSetIncludeAllCompanion: (Boolean) -> Unit,
    onSetForegroundService: (Boolean) -> Unit,
    onSetCompactOngoing: (Boolean) -> Unit,
    onSetSourceUnitMode: (SourceUnitMode) -> Unit,
    onSetJamProtection: (Boolean) -> Unit,
    onSetCustomPackages: (String) -> Unit,
    onSetStaleThresholdMinutes: (Int) -> Unit,
    onStartSensorSession: () -> Unit,
    onStopSensorSession: () -> Unit,
    onSetSensorExpiryConfig: (Int, Boolean) -> Unit,
    onSetAcceptLocalBroadcastReadings: (Boolean) -> Unit,
    onSetDexcomShareConfig: (Boolean, Boolean, String, String) -> Unit,
    onSyncDexcomShareNow: ((String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    var customPkgsText by remember(settings.customPackages) { mutableStateOf(settings.customPackages) }
    var shareUserText by remember(settings.dexcomShareUsername) { mutableStateOf(settings.dexcomShareUsername) }
    var sharePassText by remember(settings.dexcomSharePassword) { mutableStateOf(settings.dexcomSharePassword) }
    var shareFeedback by remember { mutableStateOf<String?>(null) }

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
                    StatusRow("Exakte Alarme", state.permissions.canScheduleExactAlarms, "Prüfen", onOpenExactAlarms)
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
                    Text("Companion Collector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("Companion Collector aktiv", null, settings.collectorEnabled, onSetCollectorEnabled)
                    SwitchRow(
                        "Alle xDrip Companion-Apps zulassen",
                        "Zusätzlich zu CamAPS FX auch Dexcom G6/G7/ONE, Guardian/Simplera, Eversense & Aidex",
                        settings.includeAllCompanionApps,
                        onSetIncludeAllCompanion,
                    )
                    SwitchRow(
                        "Lokalen xDrip-Broadcast empfangen",
                        "Übernimmt BgEstimate-Broadcasts von Juggluco/AAPS/anderen xDrip-Apps",
                        settings.acceptLocalBroadcastReadings,
                        onSetAcceptLocalBroadcastReadings,
                    )

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
                        "CamAPS-Pakete: ${CamApsParser.defaultCamApsPackages.joinToString(", ")}",
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
                    Text("Sensor-Laufzeit & Ablauf", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (settings.sensorStartedAtMs > 0L) {
                        val hours = ((state.nowMs - settings.sensorStartedAtMs) / 3_600_000L).coerceAtLeast(0L)
                        Text("Aktiver Sensor: ${hours / 24} Tage, ${hours % 24} Stunden (Ziel: ${settings.sensorExpectedDays} Tage)", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onStartSensorSession, modifier = Modifier.weight(1f)) { Text("Neu starten") }
                            OutlinedButton(onClick = onStopSensorSession, modifier = Modifier.weight(1f)) { Text("Stoppen") }
                        }
                    } else {
                        Text("Optional: Startzeit deines aktuellen Sensors verfolgen.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Button(onClick = onStartSensorSession) { Text("Sensor jetzt als gestartet markieren") }
                    }
                    Text("Erwartete Sensor-Laufzeit", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 14, 15).forEach { days ->
                            FilterChip(
                                selected = settings.sensorExpectedDays == days,
                                onClick = { onSetSensorExpiryConfig(days, settings.sensorExpiryAlertEnabled) },
                                label = { Text("$days Tage") },
                            )
                        }
                    }
                    SwitchRow(
                        "Vor Sensor-Ablauf erinnern",
                        "Benachrichtigt ab 12 Std. vor Laufzeitende",
                        settings.sensorExpiryAlertEnabled,
                        { onSetSensorExpiryConfig(settings.sensorExpectedDays, it) },
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
                    Text("Dexcom Share Follower", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow(
                        "Dexcom Share Follower aktiv",
                        "Ruft Werte alle 5 Min. direkt von Dexcom Share ab",
                        settings.dexcomShareEnabled,
                        { onSetDexcomShareConfig(it, settings.dexcomShareUsAccount, shareUserText, sharePassText) },
                    )
                    SwitchRow(
                        "US-Server verwenden (share2.dexcom.com)",
                        "Aus = EU / International (shareous1.dexcom.com)",
                        settings.dexcomShareUsAccount,
                        { onSetDexcomShareConfig(settings.dexcomShareEnabled, it, shareUserText, sharePassText) },
                    )
                    OutlinedTextField(
                        value = shareUserText,
                        onValueChange = { shareUserText = it },
                        label = { Text("Dexcom Share Benutzername / E-Mail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = sharePassText,
                        onValueChange = { sharePassText = it },
                        label = { Text("Passwort") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onSetDexcomShareConfig(settings.dexcomShareEnabled, settings.dexcomShareUsAccount, shareUserText, sharePassText)
                                shareFeedback = "Zugangsdaten gespeichert"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Speichern") }
                        Button(
                            onClick = {
                                onSetDexcomShareConfig(settings.dexcomShareEnabled, settings.dexcomShareUsAccount, shareUserText, sharePassText)
                                onSyncDexcomShareNow { shareFeedback = it }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Jetzt abrufen") }
                    }
                    shareFeedback?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = scheme.primary) }
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
    onSetAscendingOptions: (Boolean, Boolean) -> Unit,
    onSetStartAlertsSnoozed: (Boolean) -> Unit,
    onSetShowButtonsInAlerts: (Boolean) -> Unit,
    onSetWakePhoneDuringAlerts: (Boolean) -> Unit,
    onSetFlashTorchOnAlert: (Boolean) -> Unit,
    onSetSpeechConfig: (Boolean, Boolean, Boolean, Double) -> Unit,
    onTestSpeech: () -> Unit,
    onSetBroadcastSnoozeConfig: (Boolean, Boolean) -> Unit,
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
                    Text("Feineinstellungen & Schutzregeln", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("Ansteigend erst nach 3 Min. erhöhen", "Startet bei Profil „Ansteigend“ besonders sanft", settings.delayAscending3Min) {
                        onSetAscendingOptions(it, settings.ascendingToMedium)
                    }
                    SwitchRow("Ansteigend auf Mittel begrenzen (70 %)", null, settings.ascendingToMedium) {
                        onSetAscendingOptions(settings.delayAscending3Min, it)
                    }
                    SwitchRow("Neue Alarme zunächst stumm starten", "Klingelt erst beim zweiten Intervall (start_snoozed)", settings.startAlertsSnoozed, onSetStartAlertsSnoozed)
                    SwitchRow("Schlummern-/Stopp-Buttons in Benachrichtigung", null, settings.showButtonsInAlerts, onSetShowButtonsInAlerts)
                    SwitchRow("Display bei Alarm aufwecken", null, settings.wakePhoneDuringAlerts, onSetWakePhoneDuringAlerts)
                    SwitchRow("Kamerablitz-Puls bei Alarm", "Zwei kurze Lichtimpulse beim Auslösen", settings.flashTorchOnAlert, onSetFlashTorchOnAlert)
                    SwitchRow("Während Telefonaten stumm", "Unterdrückt Ton & Vibration bei aktivem Gespräch", settings.noAlarmsDuringCalls, onSetNoAlarmsDuringCalls)
                    SwitchRow(
                        "Snooze per Broadcast senden",
                        "Informiert andere Apps (z. B. AAPS), wenn ein Alarm hier geschlummert wird",
                        settings.broadcastSnoozeEnabled,
                        { onSetBroadcastSnoozeConfig(it, settings.acceptBroadcastSnooze) },
                    )
                    SwitchRow(
                        "Broadcast-Snooze akzeptieren",
                        "Schlummert aktive Alarme bei eingehendem xDrip-Snooze-Broadcast",
                        settings.acceptBroadcastSnooze,
                        { onSetBroadcastSnoozeConfig(settings.broadcastSnoozeEnabled, it) },
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

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Sprachausgabe (Text-to-Speech)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    SwitchRow("Neue Messwerte vorlesen", null, settings.speakReadingsEnabled) {
                        onSetSpeechConfig(it, settings.speakAlertsEnabled, settings.speakTrendEnabled, settings.speechSpeed)
                    }
                    SwitchRow("Alarme vorlesen", null, settings.speakAlertsEnabled) {
                        onSetSpeechConfig(settings.speakReadingsEnabled, it, settings.speakTrendEnabled, settings.speechSpeed)
                    }
                    SwitchRow("Trend beim Vorlesen nennen", null, settings.speakTrendEnabled) {
                        onSetSpeechConfig(settings.speakReadingsEnabled, settings.speakAlertsEnabled, it, settings.speechSpeed)
                    }
                    Text("Sprechgeschwindigkeit: ${String.format(Locale.US, "%.1fx", settings.speechSpeed)}", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.speechSpeed.toFloat(),
                        onValueChange = { onSetSpeechConfig(settings.speakReadingsEnabled, settings.speakAlertsEnabled, settings.speakTrendEnabled, it.toDouble()) },
                        valueRange = 0.7f..1.5f,
                    )
                    OutlinedButton(onClick = onTestSpeech) { Text("Sprachausgabe testen") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDisplayPage(
    state: MainUiState,
    onSetUseMgdl: (Boolean) -> Unit,
    onSetTargetRange: (Double, Double) -> Unit,
    onSetPredictionCurve: (Boolean) -> Unit,
    onSetShowChartTreatments: (Boolean) -> Unit,
    onSetChartVisualOptions: (Boolean, Boolean, Boolean, Int) -> Unit,
    onSetShowExtraStatusLine: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetIobCobConfig: (Boolean, Int, Int, Int, Int) -> Unit,
    onSetBolusProfileConfig: (Double, Double, Double) -> Unit,
    onSetBasalProfileConfig: (Boolean, BasalSchedule) -> Unit,
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
                    Text("Chart & Übersicht", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow("30-Min. Vorhersage-Kurve", "Gestrichelte Trend-Prognose im Chart", settings.predictionCurveEnabled, onSetPredictionCurve)
                    SwitchRow("Therapie-Marker im Chart", "Zeigt Insulin (IE) & KH (g) direkt im Verlauf", settings.showChartTreatments, onSetShowChartTreatments)
                    SwitchRow("Nur Punkte im Chart (ohne Linie)", null, settings.chartPointsOnly) {
                        onSetChartVisualOptions(it, settings.showChartGrid, settings.showChartAverageLine, settings.chartMaxMgdl)
                    }
                    SwitchRow("Gitterlinien anzeigen", null, settings.showChartGrid) {
                        onSetChartVisualOptions(settings.chartPointsOnly, it, settings.showChartAverageLine, settings.chartMaxMgdl)
                    }
                    SwitchRow("Durchschnittslinie im Chart", null, settings.showChartAverageLine) {
                        onSetChartVisualOptions(settings.chartPointsOnly, settings.showChartGrid, it, settings.chartMaxMgdl)
                    }
                    Text("Y-Achsen-Maximum", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Auto", 250 to "250", 300 to "300", 350 to "350", 400 to "400").forEach { (valMgdl, label) ->
                            FilterChip(
                                selected = settings.chartMaxMgdl == valMgdl,
                                onClick = {
                                    onSetChartVisualOptions(settings.chartPointsOnly, settings.showChartGrid, settings.showChartAverageLine, valMgdl)
                                },
                                label = { Text(if (valMgdl == 0) label else GlucoseUnit.formatValue(valMgdl.toDouble(), settings.useMgdl)) },
                            )
                        }
                    }
                    SwitchRow("24h-Statuszeile auf Übersicht", "Zeigt TIR, Ø, GMI und CV% kompakt unter dem Chart", settings.showExtraStatusLine, onSetShowExtraStatusLine)
                    SwitchRow("Bildschirm wach halten (Nachttisch)", "Verhindert Auto-Sperre solange xDrip 2 You offen ist", settings.keepScreenOn, onSetKeepScreenOn)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Insulin & Kohlenhydrate (IOB / COB)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow(
                        "IOB & COB berechnen & anzeigen",
                        "Zeigt aktives Insulin (IE) und offene KH (g) in Übersicht & Status",
                        settings.showIobCob,
                        {
                            onSetIobCobConfig(
                                it,
                                settings.insulinDurationMinutes,
                                settings.insulinPeakMinutes,
                                settings.carbDelayMinutes,
                                settings.carbAbsorptionGramsPerHour,
                            )
                        },
                    )
                    if (settings.showIobCob) {
                        Text(
                            "Insulin-Wirkdauer (DIA): ${String.format(Locale.US, "%.1f", settings.insulinDurationMinutes / 60.0)} Std. (${settings.insulinDurationMinutes} Min.)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = settings.insulinDurationMinutes.toFloat(),
                            onValueChange = {
                                onSetIobCobConfig(
                                    true,
                                    it.roundToInt(),
                                    settings.insulinPeakMinutes,
                                    settings.carbDelayMinutes,
                                    settings.carbAbsorptionGramsPerHour,
                                )
                            },
                            valueRange = 180f..420f,
                            steps = 7,
                        )
                        Text("Insulin-Profil (Peak)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(45 to "Ultra-Schnell (45m)", 65 to "Schnell (65m)", 85 to "Klassisch (85m)").forEach { (peak, label) ->
                                FilterChip(
                                    selected = settings.insulinPeakMinutes == peak,
                                    onClick = {
                                        onSetIobCobConfig(
                                            true,
                                            settings.insulinDurationMinutes,
                                            peak,
                                            settings.carbDelayMinutes,
                                            settings.carbAbsorptionGramsPerHour,
                                        )
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text(
                            "KH-Aufnahme: ${settings.carbAbsorptionGramsPerHour} g/Std. (Verzögerung: ${settings.carbDelayMinutes} Min.)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = settings.carbAbsorptionGramsPerHour.toFloat(),
                            onValueChange = {
                                onSetIobCobConfig(
                                    true,
                                    settings.insulinDurationMinutes,
                                    settings.insulinPeakMinutes,
                                    settings.carbDelayMinutes,
                                    it.roundToInt(),
                                )
                            },
                            valueRange = 15f..60f,
                            steps = 8,
                        )
                    }
                    Text(
                        "Bolus-Rechner: ${String.format(Locale.US, "%.0f", settings.carbRatioGramsPerUnit)} g/IE · ISF ${GlucoseUnit.formatSpread(settings.isfMgdlPerUnit, settings.useMgdl)} · Ziel ${GlucoseUnit.formatValue(settings.bolusTargetMgdl, settings.useMgdl)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("KH-Faktor (${settings.carbRatioGramsPerUnit.roundToInt()} g pro 1 IE)", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = settings.carbRatioGramsPerUnit.toFloat(),
                        onValueChange = { onSetBolusProfileConfig(it.roundToInt().toDouble(), settings.isfMgdlPerUnit, settings.bolusTargetMgdl) },
                        valueRange = 4f..30f,
                        steps = 25,
                    )
                    Text("Korrekturfaktor / ISF (${GlucoseUnit.formatSpread(settings.isfMgdlPerUnit, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)} pro 1 IE)", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = settings.isfMgdlPerUnit.toFloat(),
                        onValueChange = { onSetBolusProfileConfig(settings.carbRatioGramsPerUnit, (it / 5f).roundToInt() * 5.0, settings.bolusTargetMgdl) },
                        valueRange = 15f..120f,
                        steps = 20,
                    )
                }
            }
        }

        item {
            val schedule = state.basalSchedule
            var selectedHour by remember { mutableIntStateOf(0) }
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Basal-Profil (24-Std.)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow(
                        "Aktuelle Basalrate in Übersicht anzeigen",
                        "Tagesgesamtbasal: ${String.format(Locale.US, "%.1f", schedule.totalDailyUnits)} IE/Tag",
                        settings.showBasalRate,
                        { onSetBasalProfileConfig(it, schedule) },
                    )
                    if (settings.showBasalRate) {
                        val maxRate = (schedule.hourlyRates.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            schedule.hourlyRates.forEachIndexed { hr, rate ->
                                val frac = (rate / maxRate).toFloat().coerceIn(0.08f, 1f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((42.dp * frac))
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(
                                            if (hr == selectedHour) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        )
                                        .clickable { selectedHour = hr },
                                )
                            }
                        }
                        val hourRate = schedule.hourlyRates[selectedHour]
                        Text(
                            text = "${String.format("%02d", selectedHour)}:00 Uhr → ${String.format(Locale.US, "%.2f", hourRate)} IE/Std.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Slider(
                            value = hourRate.toFloat(),
                            onValueChange = {
                                val rounded = (it * 20f).roundToInt() / 20.0
                                onSetBasalProfileConfig(true, schedule.withRate(selectedHour, rounded))
                            },
                            valueRange = 0f..3.0f,
                        )
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCloudPage(
    state: MainUiState,
    onSetNightscoutConfig: (String, String, Boolean, Boolean) -> Unit,
    onSetNightscoutExtraConfig: (Boolean, Boolean) -> Unit,
    onSetNightscoutDeviceStatusEnabled: (Boolean) -> Unit,
    onSyncNightscoutNow: ((String) -> Unit) -> Unit,
    onSetLocalBroadcastEnabled: (Boolean) -> Unit,
    onSetBroadcastTargetPackage: (String) -> Unit,
    onSetLocalWebServiceConfig: (Boolean, Boolean, String) -> Unit,
    onSetRetentionDays: (Int) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    var nsUrlText by remember(settings.nightscoutUrl) { mutableStateOf(settings.nightscoutUrl) }
    var nsSecretText by remember(settings.nightscoutApiSecret) { mutableStateOf(settings.nightscoutApiSecret) }
    var targetPkgText by remember(settings.broadcastTargetPackage) { mutableStateOf(settings.broadcastTargetPackage) }
    var webSecretText by remember(settings.localWebServiceSecret) { mutableStateOf(settings.localWebServiceSecret) }
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
                    SwitchRow("Messwerte hochladen", null, settings.nightscoutUploadEnabled) { upload ->
                        onSetNightscoutConfig(nsUrlText, nsSecretText, settings.nightscoutFollowEnabled, upload)
                    }
                    SwitchRow("Therapie-Einträge hochladen", null, settings.nightscoutUploadTreatments) {
                        onSetNightscoutExtraConfig(it, settings.nightscoutWifiOnly)
                    }
                    SwitchRow("Nur im WLAN synchronisieren", null, settings.nightscoutWifiOnly) {
                        onSetNightscoutExtraConfig(settings.nightscoutUploadTreatments, it)
                    }
                    SwitchRow(
                        "DeviceStatus & Pumpendaten syncen",
                        "Sendet Telefon-Akku & lädt Pumpen-IOB/Reservoir (AAPS/CamAPS)",
                        settings.nightscoutDeviceStatusEnabled,
                        onSetNightscoutDeviceStatusEnabled,
                    )
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
                    Text("Lokaler Broadcast (AAPS / Watches)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow(
                        "Lokaler xDrip-Broadcast aktiv",
                        "Sendet com.eveningoutpost.dexdrip.BgEstimate bei neuen Werten",
                        settings.localBroadcastEnabled,
                        onSetLocalBroadcastEnabled,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = targetPkgText,
                            onValueChange = { targetPkgText = it },
                            label = { Text("Optional: Ziel-Package (leer = alle)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onSetBroadcastTargetPackage(targetPkgText) }) { Text("OK") }
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
                    Text("Lokaler xDrip Web-Service (Port 17580)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SwitchRow(
                        "Lokaler Web-Service aktiv",
                        "Stellt /sgv.json, /status.json, /treatments.json & /pebble lokal bereit",
                        settings.localWebServiceEnabled,
                        { onSetLocalWebServiceConfig(it, settings.localWebServiceOpenNetwork, webSecretText) },
                    )
                    if (settings.localWebServiceEnabled) {
                        SwitchRow(
                            "Im gesamten WLAN öffnen (0.0.0.0)",
                            "Aus = nur auf diesem Gerät (127.0.0.1)",
                            settings.localWebServiceOpenNetwork,
                            { onSetLocalWebServiceConfig(true, it, webSecretText) },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = webSecretText,
                                onValueChange = { webSecretText = it },
                                label = { Text("Optional: api-secret Schutz") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = {
                                    onSetLocalWebServiceConfig(true, settings.localWebServiceOpenNetwork, webSecretText)
                                },
                            ) { Text("OK") }
                        }
                        Text(
                            "Endpunkte: http://127.0.0.1:17580/sgv.json · /pebble · /status.json · /treatments.json",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
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
                    Text("Speicher & Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Lokale Datenvorhaltung", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 90, 180, 365).forEach { days ->
                            FilterChip(
                                selected = settings.retentionDays == days,
                                onClick = { onSetRetentionDays(days) },
                                label = { Text("$days T.") },
                            )
                        }
                    }
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
    onReopenOnboarding: () -> Unit,
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
                    Text("App-Info, Assistent & Changelog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "xDrip 2 You · Version ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Lokaler Kotlin + Jetpack Compose (Material 3 You) Fork von xDrip (Branch: kotlin-jetpack-you).",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showChangelog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.History, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Changelog")
                        }
                        OutlinedButton(onClick = onReopenOnboarding, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Assistent")
                        }
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
                    Text("[26.11.4] · Einrichtungs-Assistent & volle xDrip-Optionen", fontWeight = FontWeight.Bold)
                    Text("• Geführter 4-Schritt-Einrichtungs-Assistent für den ersten Start inkl. aller Systemberechtigungen\n• Alle wichtigen xDrip-Optionen funktional integriert: Sprachausgabe (TTS), Kamerablitz-Puls, Ansteigend-Feinabstimmung, Alle Companion-Apps, Sensor-Ablauf-Erinnerung, Chart-Optionen (Punkte/Gitter/Ø-Linie/Y-Achse), 24h-Statuszeile & Datenvorhaltung")
                    Text("[26.11.3] · Options-Hub & Sensor-Tools", fontWeight = FontWeight.Bold)
                    Text("• Hierarchischer Material-3-You-Options-Hub mit Suchleiste\n• Eigener Alarm-Klingelton, Anruf-Stummschaltung & Stale-Alarm-Sperre\n• Sensor-Laufzeit-Tracking, Nachttisch-Modus & CSV-Export")
                    Text("[26.11.2] · xDrip 2 You & Ökosystem", fontWeight = FontWeight.Bold)
                    Text("• Rebrand zu xDrip 2 You, Therapie-Tagebuch (IE/KH/BZ) + Chart-Marker\n• 30-Min.-Vorhersage, Niedrig-Vorhersage- & Anhaltend-Hoch-Alarm\n• Nightscout Follower/Upload, AAPS-Broadcast, Material-You-Widget & JSON-Backup")
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
