package dev.xdripyou.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.ThemeModeOption
import dev.xdripyou.app.ui.MainUiState
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import kotlin.math.roundToInt

@Composable
fun OnboardingWizard(
    state: MainUiState,
    onSetUseMgdl: (Boolean) -> Unit,
    onSetTargetRange: (Double, Double) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetThemeMode: (ThemeModeOption) -> Unit,
    onSetSourceConfig: (collectorEnabled: Boolean, includeAllCompanion: Boolean, nsUrl: String, nsSecret: String, nsFollow: Boolean) -> Unit,
    onRequestPostNotifications: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenDndPolicy: () -> Unit,
    onOpenExactAlarms: () -> Unit,
    onSetMissedReading: (Boolean, Int, Int) -> Unit,
    onStartSensor: () -> Unit,
    onFinish: (seedDemo: Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = state.settings
    var step by rememberSaveable { mutableIntStateOf(0) } // 0..3
    var sourceChoice by rememberSaveable { mutableIntStateOf(0) } // 0=CamAPS, 1=All Companion, 2=Nightscout, 3=Demo
    var nsUrl by rememberSaveable { mutableStateOf(settings.nightscoutUrl) }
    var nsSecret by rememberSaveable { mutableStateOf(settings.nightscoutApiSecret) }
    var seedDemoOnFinish by rememberSaveable { mutableStateOf(false) }
    var markSensorStarted by rememberSaveable { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "xDrip 2 You · Einrichtung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
                Text(
                    text = "Schritt ${step + 1} von 4",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (step + 1) / 4f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    0 -> StepWelcomeAndUnits(
                        useMgdl = settings.useMgdl,
                        targetLowMgdl = settings.targetLowMgdl,
                        targetHighMgdl = settings.targetHighMgdl,
                        dynamicColor = settings.dynamicColorEnabled,
                        themeMode = settings.themeMode,
                        onSetUseMgdl = onSetUseMgdl,
                        onSetTargetRange = onSetTargetRange,
                        onSetDynamicColor = onSetDynamicColor,
                        onSetThemeMode = onSetThemeMode,
                    )

                    1 -> StepDataSource(
                        sourceChoice = sourceChoice,
                        nsUrl = nsUrl,
                        nsSecret = nsSecret,
                        onSelectChoice = { choice ->
                            sourceChoice = choice
                            if (choice == 3) seedDemoOnFinish = true
                        },
                        onNsUrlChange = { nsUrl = it },
                        onNsSecretChange = { nsSecret = it },
                    )

                    2 -> StepPermissions(
                        state = state,
                        onRequestPostNotifications = onRequestPostNotifications,
                        onOpenNotificationListener = onOpenNotificationListener,
                        onRequestBatteryExemption = onRequestBatteryExemption,
                        onOpenDndPolicy = onOpenDndPolicy,
                        onOpenExactAlarms = onOpenExactAlarms,
                    )

                    else -> StepAlertsAndReady(
                        state = state,
                        seedDemo = seedDemoOnFinish,
                        markSensorStarted = markSensorStarted,
                        onToggleSeedDemo = { seedDemoOnFinish = it },
                        onToggleMarkSensor = { markSensorStarted = it },
                        onSetMissedReading = onSetMissedReading,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Zurück")
                    }
                } else {
                    TextButton(onClick = { onFinish(false) }) {
                        Text("Später")
                    }
                }

                if (step < 3) {
                    Button(
                        onClick = {
                            if (step == 1) {
                                when (sourceChoice) {
                                    0 -> onSetSourceConfig(true, false, nsUrl, nsSecret, false)
                                    1 -> onSetSourceConfig(true, true, nsUrl, nsSecret, false)
                                    2 -> onSetSourceConfig(false, false, nsUrl, nsSecret, true)
                                    else -> onSetSourceConfig(true, false, nsUrl, nsSecret, false)
                                }
                            }
                            step++
                        },
                    ) {
                        Text("Weiter")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            if (markSensorStarted) onStartSensor()
                            onFinish(seedDemoOnFinish)
                        },
                    ) {
                        Text("xDrip 2 You starten")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepWelcomeAndUnits(
    useMgdl: Boolean,
    targetLowMgdl: Double,
    targetHighMgdl: Double,
    dynamicColor: Boolean,
    themeMode: ThemeModeOption,
    onSetUseMgdl: (Boolean) -> Unit,
    onSetTargetRange: (Double, Double) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetThemeMode: (ThemeModeOption) -> Unit,
) {
    Text(
        text = "Willkommen bei xDrip 2 You",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Aufgeräumtes Material-3-You-CGM-Monitoring in Kotlin – optimiert für CamAPS FX, verlässliche Alarme und Nightscout.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Blutzucker-Einheit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = useMgdl,
                    onClick = { onSetUseMgdl(true) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("mg/dL") }
                SegmentedButton(
                    selected = !useMgdl,
                    onClick = { onSetUseMgdl(false) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("mmol/L") }
            }

            Text(
                "Zielbereich: ${GlucoseUnit.formatValue(targetLowMgdl, useMgdl)} – ${GlucoseUnit.formatValue(targetHighMgdl, useMgdl)} ${GlucoseUnit.unitLabel(useMgdl)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            RangeSlider(
                value = targetLowMgdl.toFloat()..targetHighMgdl.toFloat(),
                onValueChange = { r -> onSetTargetRange(r.start.roundToInt().toDouble(), r.endInclusive.roundToInt().toDouble()) },
                valueRange = 55f..250f,
            )
        }
    }

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Design (Material 3 You)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dynamische Wallpaper-Farben")
                    Text("Android 12+ Material You", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = dynamicColor, onCheckedChange = onSetDynamicColor)
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeModeOption.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { onSetThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(idx, ThemeModeOption.entries.size),
                    ) { Text(mode.labelDe) }
                }
            }
        }
    }
}

@Composable
private fun StepDataSource(
    sourceChoice: Int,
    nsUrl: String,
    nsSecret: String,
    onSelectChoice: (Int) -> Unit,
    onNsUrlChange: (String) -> Unit,
    onNsSecretChange: (String) -> Unit,
) {
    Text(
        text = "Datenquelle auswählen",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Wähle, woher xDrip 2 You deine Blutzuckerwerte empfangen soll. Du kannst das jederzeit unter Optionen ändern.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SourceChoiceCard(
        selected = sourceChoice == 0,
        icon = Icons.Rounded.Sensors,
        title = "CamAPS FX Companion (Empfohlen)",
        subtitle = "Liest die laufende CamAPS FX Benachrichtigung direkt und zuverlässig aus.",
        onClick = { onSelectChoice(0) },
    )
    SourceChoiceCard(
        selected = sourceChoice == 1,
        icon = Icons.Rounded.Tune,
        title = "Alle CGM Companion-Apps",
        subtitle = "CamAPS FX plus Dexcom G6/G7/ONE, Medtronic Guardian/Simplera, Eversense, Aidex & mehr.",
        onClick = { onSelectChoice(1) },
    )
    SourceChoiceCard(
        selected = sourceChoice == 2,
        icon = Icons.Rounded.CloudSync,
        title = "Nightscout Follower",
        subtitle = "Ruft Werte alle 5 Minuten von deiner Nightscout-Instanz ab.",
        onClick = { onSelectChoice(2) },
    )
    if (sourceChoice == 2) {
        OutlinedTextField(
            value = nsUrl,
            onValueChange = onNsUrlChange,
            label = { Text("Nightscout-URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nsSecret,
            onValueChange = onNsSecretChange,
            label = { Text("API-Secret (optional beim Abruf)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SourceChoiceCard(
        selected = sourceChoice == 3,
        icon = Icons.Rounded.Science,
        title = "Test- & Demo-Modus",
        subtitle = "Lädt direkt eine 12-Stunden-Demokurve zum Ausprobieren von Chart & Alarmen.",
        onClick = { onSelectChoice(3) },
    )
}

@Composable
private fun SourceChoiceCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) scheme.onPrimaryContainer else scheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StepPermissions(
    state: MainUiState,
    onRequestPostNotifications: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenDndPolicy: () -> Unit,
    onOpenExactAlarms: () -> Unit,
) {
    val perms = state.permissions
    Text(
        text = "Berechtigungen erteilen",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Damit CamAPS-Werte im Hintergrund ankommen und Alarme auch nachts zuverlässig auslösen:",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        color = if (perms.coreReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = if (perms.allRecommendedGranted) {
                "Alle 5 Berechtigungen sind erteilt!"
            } else if (perms.coreReady) {
                "Kern-Berechtigungen aktiv – empfohlene Hintergrund-Rechte unten prüfen."
            } else {
                "Bitte mindestens Benachrichtigungen und Benachrichtigungszugriff aktivieren."
            },
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }

    WizardPermissionCard(
        icon = Icons.Rounded.Notifications,
        title = "App-Benachrichtigungen",
        description = "Erforderlich für laufende Blutzucker-Anzeige und Alarme (Android 13+).",
        granted = perms.postNotificationsGranted,
        actionLabel = "Erlauben",
        onAction = onRequestPostNotifications,
    )
    WizardPermissionCard(
        icon = Icons.Rounded.NotificationsActive,
        title = "Benachrichtigungszugriff (CamAPS FX)",
        description = "Erlaubt das Auslesen der laufenden CamAPS FX Benachrichtigung.",
        granted = perms.notificationListenerEnabled,
        actionLabel = "Einstellungen",
        onAction = onOpenNotificationListener,
    )
    WizardPermissionCard(
        icon = Icons.Rounded.BatteryChargingFull,
        title = "Akku-Optimierung ignorieren",
        description = "Verhindert, dass Android die Überwachung im Standby beendet.",
        granted = perms.ignoringBatteryOptimizations,
        actionLabel = "Freigeben",
        onAction = onRequestBatteryExemption,
    )
    WizardPermissionCard(
        icon = Icons.Rounded.DoNotDisturbOn,
        title = "Nicht-Stören umgehen (DND)",
        description = "Wichtig, damit dringende Unterzucker-Alarme nachts trotzdem klingen.",
        granted = perms.dndPolicyAccessGranted,
        actionLabel = "Öffnen",
        onAction = onOpenDndPolicy,
    )
    WizardPermissionCard(
        icon = Icons.Rounded.Alarm,
        title = "Exakte Alarme & Weckzeiten",
        description = "Sorgt für pünktliche Schlummer- und „Verpasster Wert“-Timer.",
        granted = perms.canScheduleExactAlarms,
        actionLabel = "Prüfen",
        onAction = onOpenExactAlarms,
    )
}

@Composable
private fun WizardPermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val glucoseColors = LocalGlucoseColors.current
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = if (granted) glucoseColors.inRange else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text("OK", style = MaterialTheme.typography.labelLarge, color = glucoseColors.inRange, fontWeight = FontWeight.Bold)
            } else {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun StepAlertsAndReady(
    state: MainUiState,
    seedDemo: Boolean,
    markSensorStarted: Boolean,
    onToggleSeedDemo: (Boolean) -> Unit,
    onToggleMarkSensor: (Boolean) -> Unit,
    onSetMissedReading: (Boolean, Int, Int) -> Unit,
) {
    val settings = state.settings
    Text(
        text = "Fast geschafft!",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Standard-Alarme (Dringend Niedrig 55, Niedrig 70, Hoch 180) sind vorkonfiguriert und können im Tab „Alarme“ jederzeit angepasst werden.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Warnung bei verpassten Werten", fontWeight = FontWeight.Medium)
                    Text("Meldet nach ${settings.missedReadingMinutes} Min. ohne neue Daten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.missedReadingEnabled,
                    onCheckedChange = { onSetMissedReading(it, settings.missedReadingMinutes, settings.missedReadingSnoozeMinutes) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sensor-Laufzeit jetzt starten", fontWeight = FontWeight.Medium)
                    Text("Zeigt das Alter deines Sensors auf der Übersicht", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = markSensorStarted, onCheckedChange = onToggleMarkSensor)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("12h Demo-Kurve zum Start laden", fontWeight = FontWeight.Medium)
                    Text("Praktisch zum Ausprobieren, falls CamAPS noch keinen Wert gesendet hat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = seedDemo, onCheckedChange = onToggleSeedDemo)
            }
        }
    }
}
