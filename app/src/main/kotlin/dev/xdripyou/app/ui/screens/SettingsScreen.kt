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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenDndPolicy: () -> Unit,
    onSetCollectorEnabled: (Boolean) -> Unit,
    onSetForegroundService: (Boolean) -> Unit,
    onSetSourceUnitMode: (SourceUnitMode) -> Unit,
    onSetJamProtection: (Boolean) -> Unit,
    onSetCustomPackages: (String) -> Unit,
    onSetNightscoutConfig: (String, String, Boolean, Boolean) -> Unit,
    onSyncNightscoutNow: ((String) -> Unit) -> Unit,
    onSetLocalBroadcastEnabled: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onSimulateNotification: (String, (String) -> Unit) -> Unit,
    onInjectManualReading: (Double) -> Unit,
    onSeedDemo: () -> Unit,
    onClearAllReadings: () -> Unit,
    onSetUseMgdl: (Boolean) -> Unit,
    onSetTargetRange: (Double, Double) -> Unit,
    onSetPredictionCurve: (Boolean) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetThemeMode: (ThemeModeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    var simText by remember { mutableStateOf("112 mg/dL →") }
    var manualText by remember { mutableStateOf("105") }
    var simFeedback by remember { mutableStateOf<String?>(null) }
    var customPkgsText by remember(settings.customPackages) { mutableStateOf(settings.customPackages) }
    var nsUrlText by remember(settings.nightscoutUrl) { mutableStateOf(settings.nightscoutUrl) }
    var nsSecretText by remember(settings.nightscoutApiSecret) { mutableStateOf(settings.nightscoutApiSecret) }
    var nsFeedback by remember { mutableStateOf<String?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. CamAPS FX Companion Status
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "CamAPS FX Companion-Quelle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    StatusRow(
                        label = "Benachrichtigungszugriff",
                        ok = state.permissions.notificationListenerEnabled,
                        actionLabel = "Öffnen",
                        onAction = onOpenNotificationListener,
                    )
                    StatusRow(
                        label = "App-Benachrichtigungen",
                        ok = state.permissions.postNotificationsGranted,
                        actionLabel = "Erlauben",
                        onAction = onRequestPostNotifications,
                    )
                    StatusRow(
                        label = "Akku-Optimierung aus",
                        ok = state.permissions.ignoringBatteryOptimizations,
                        actionLabel = "Öffnen",
                        onAction = onRequestBatteryExemption,
                    )
                    StatusRow(
                        label = "Nicht-Stören-Umgehung (DND)",
                        ok = state.permissions.dndPolicyAccessGranted,
                        actionLabel = "Optional",
                        onAction = onOpenDndPolicy,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("CamAPS Collector aktiv")
                        Switch(
                            checked = settings.collectorEnabled,
                            onCheckedChange = onSetCollectorEnabled,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dauerhafte Status-Benachrichtigung")
                            Text(
                                "Hält Hintergrund-Überwachung & Timer aktiv",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.foregroundServiceEnabled,
                            onCheckedChange = onSetForegroundService,
                        )
                    }

                    Text(
                        "Einheit in CamAPS-Benachrichtigung",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceUnitMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.sourceUnitMode == mode,
                                onClick = { onSetSourceUnitMode(mode) },
                                label = { Text(mode.labelDe) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Jam-Schutz (> 6 identische Werte)")
                            Text(
                                "Blockiert festgefrorene Wiederholungen wie in xDrip",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.jamProtection, onCheckedChange = onSetJamProtection)
                    }

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
                        OutlinedButton(onClick = { onSetCustomPackages(customPkgsText) }) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // 2. Nightscout Follower & Upload
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudSync, null, tint = scheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Nightscout (Follower & Upload)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Als Follower abrufen (alle 5 Min.)")
                        Switch(
                            checked = settings.nightscoutFollowEnabled,
                            onCheckedChange = { follow ->
                                onSetNightscoutConfig(
                                    nsUrlText,
                                    nsSecretText,
                                    follow,
                                    settings.nightscoutUploadEnabled,
                                )
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Werte & Einträge hochladen")
                        Switch(
                            checked = settings.nightscoutUploadEnabled,
                            onCheckedChange = { upload ->
                                onSetNightscoutConfig(
                                    nsUrlText,
                                    nsSecretText,
                                    settings.nightscoutFollowEnabled,
                                    upload,
                                )
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onSetNightscoutConfig(
                                    nsUrlText,
                                    nsSecretText,
                                    settings.nightscoutFollowEnabled,
                                    settings.nightscoutUploadEnabled,
                                )
                                nsFeedback = "Konfiguration gespeichert"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Speichern") }
                        Button(
                            onClick = {
                                onSetNightscoutConfig(
                                    nsUrlText,
                                    nsSecretText,
                                    settings.nightscoutFollowEnabled,
                                    settings.nightscoutUploadEnabled,
                                )
                                onSyncNightscoutNow { res -> nsFeedback = res }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Jetzt syncen") }
                    }
                    nsFeedback?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                    }
                }
            }
        }

        // 3. Inter-App Integration, Widget & Backup
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Integration & Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lokaler xDrip-Broadcast (AAPS / Watches)")
                            Text(
                                "Sendet com.eveningoutpost.dexdrip.BgEstimate bei neuen Werten",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.localBroadcastEnabled,
                            onCheckedChange = onSetLocalBroadcastEnabled,
                        )
                    }
                    Text(
                        "Tipp: Das Material-You-Widget „xDrip 2 You · Blutzucker“ kannst du direkt auf deinem Pixel-Startbildschirm hinzufügen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("JSON Export")
                        }
                        OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("JSON Import")
                        }
                    }
                }
            }
        }

        // 4. Display & Material 3 You
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Anzeige & Material 3 You",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

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
                        onValueChange = { range ->
                            onSetTargetRange(
                                range.start.roundToInt().toDouble(),
                                range.endInclusive.roundToInt().toDouble(),
                            )
                        },
                        valueRange = 55f..250f,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("30-Min. Vorhersage-Kurve im Chart")
                            Text(
                                "Zeigt gestrichelte Trend-Prognose",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.predictionCurveEnabled,
                            onCheckedChange = onSetPredictionCurve,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Material You (Dynamische Farben)")
                            Text(
                                "Passt Farben automatisch ans Pixel-Wallpaper an",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.dynamicColorEnabled,
                            onCheckedChange = onSetDynamicColor,
                        )
                    }

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

        // 5. Test & Diagnostics Lab
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Science, null, tint = scheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Test- & Diagnose-Labor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

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
                        Button(
                            onClick = {
                                onSimulateNotification(simText) { res -> simFeedback = res }
                            },
                        ) { Text("Test") }
                    }
                    simFeedback?.let { msg ->
                        Text(
                            "Ergebnis: $msg",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                        )
                    }

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
                                val mgdl = GlucoseUnit.toMgdl(parsed, fromMgdl = settings.useMgdl)
                                onInjectManualReading(mgdl)
                            },
                        ) { Text("Einfügen") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onSeedDemo,
                            modifier = Modifier.weight(1f),
                        ) { Text("12h Demo-Kurve") }
                        OutlinedButton(
                            onClick = onClearAllReadings,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Daten leeren")
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Live CamAPS Collector-Protokoll",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    if (state.collectorEvents.isEmpty()) {
                        Text(
                            "Noch keine Benachrichtigungen protokolliert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.collectorEvents.take(12).forEach { ev ->
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
                                            Text(
                                                "${ev.status} · ${ev.packageName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                timeFmt.format(Date(ev.timestampMs)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            ev.message,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (ev.rawTexts.isNotBlank()) {
                                            Text(
                                                "Text: ${ev.rawTexts}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
