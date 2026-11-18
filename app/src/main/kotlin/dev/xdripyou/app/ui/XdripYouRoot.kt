package dev.xdripyou.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.xdripyou.app.ui.screens.AlertsScreen
import dev.xdripyou.app.ui.screens.HistoryScreen
import dev.xdripyou.app.ui.screens.OnboardingWizard
import dev.xdripyou.app.ui.screens.OverviewScreen
import dev.xdripyou.app.ui.screens.SettingsScreen
import dev.xdripyou.app.ui.screens.SettingsSection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XdripYouRoot(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenDndPolicy: () -> Unit,
    onOpenExactAlarms: () -> Unit,
    onPickAlertRingtone: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportCsv: () -> Unit,
) {
    if (!state.isLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!state.settings.onboardingCompleted) {
        OnboardingWizard(
            state = state,
            onSetUseMgdl = viewModel::setUseMgdl,
            onSetTargetRange = viewModel::setTargetRange,
            onSetDynamicColor = viewModel::setDynamicColor,
            onSetThemeMode = viewModel::setThemeMode,
            onSetSourceConfig = { collectorEnabled, includeAll, nsUrl, nsSecret, nsFollow ->
                viewModel.setCollectorEnabled(collectorEnabled)
                viewModel.setIncludeAllCompanionApps(includeAll)
                viewModel.setNightscoutConfig(nsUrl, nsSecret, nsFollow, state.settings.nightscoutUploadEnabled)
            },
            onRequestPostNotifications = onRequestPostNotifications,
            onOpenNotificationListener = onOpenNotificationListener,
            onRequestBatteryExemption = onRequestBatteryExemption,
            onOpenDndPolicy = onOpenDndPolicy,
            onOpenExactAlarms = onOpenExactAlarms,
            onSetMissedReading = viewModel::setMissedReading,
            onStartSensor = viewModel::startSensorSession,
            onFinish = viewModel::completeOnboarding,
        )
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsSection by rememberSaveable { mutableStateOf(SettingsSection.HUB) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = selectedTab == 3 && settingsSection != SettingsSection.HUB) {
        settingsSection = SettingsSection.HUB
    }

    val topTitle = when (selectedTab) {
        0 -> "xDrip 2 You"
        1 -> "Alarme"
        2 -> "Verlauf"
        else -> settingsSection.title
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = topTitle,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    if (selectedTab == 3 && settingsSection != SettingsSection.HUB) {
                        IconButton(onClick = { settingsSection = SettingsSection.HUB }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null) },
                    label = { Text("Übersicht") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                    label = { Text("Alarme") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    label = { Text("Verlauf") },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        if (selectedTab == 3) settingsSection = SettingsSection.HUB
                        selectedTab = 3
                    },
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    label = { Text("Optionen") },
                )
            }
        },
    ) { innerPadding ->
        val mod = Modifier.padding(innerPadding)
        when (selectedTab) {
            0 -> OverviewScreen(
                state = state,
                onSelectWindow = viewModel::selectWindow,
                onSnoozeAlert = viewModel::snoozeActiveAlert,
                onStopAlert = viewModel::stopActiveAlert,
                onAddTreatment = viewModel::addTreatment,
                onOpenNotificationListener = onOpenNotificationListener,
                onRequestPostNotifications = onRequestPostNotifications,
                onRequestBatteryExemption = onRequestBatteryExemption,
                onSeedDemo = viewModel::seedDemoCurve,
                onOpenSettingsTab = {
                    settingsSection = SettingsSection.DIAGNOSTICS
                    selectedTab = 3
                },
                modifier = mod,
            )
            1 -> AlertsScreen(
                state = state,
                onSnoozeActive = viewModel::snoozeActiveAlert,
                onStopActive = viewModel::stopActiveAlert,
                onSilenceScope = viewModel::silenceAlerts,
                onUpsertRule = viewModel::upsertAlertRule,
                onDeleteRule = viewModel::deleteAlertRule,
                onTriggerTestAlert = viewModel::triggerTestAlert,
                onSetProfile = viewModel::setAlertProfile,
                onSetSmartAlerting = viewModel::setSmartAlerting,
                onSetSmartSnoozing = viewModel::setSmartSnoozing,
                onSetMissedReading = viewModel::setMissedReading,
                onSetRisingAlert = viewModel::setRisingAlert,
                onSetFallingAlert = viewModel::setFallingAlert,
                onSetLowPrediction = viewModel::setLowPrediction,
                onSetPersistentHigh = viewModel::setPersistentHigh,
                modifier = mod,
            )
            2 -> HistoryScreen(
                state = state,
                onSelectStatsPeriod = viewModel::selectStatsPeriod,
                onAddTreatment = viewModel::addTreatment,
                onDeleteTreatment = viewModel::deleteTreatment,
                modifier = mod,
            )
            else -> SettingsScreen(
                section = settingsSection,
                onSelectSection = { settingsSection = it },
                state = state,
                onReopenOnboarding = viewModel::reopenOnboarding,
                onOpenNotificationListener = onOpenNotificationListener,
                onRequestPostNotifications = onRequestPostNotifications,
                onRequestBatteryExemption = onRequestBatteryExemption,
                onOpenDndPolicy = onOpenDndPolicy,
                onOpenExactAlarms = onOpenExactAlarms,
                onSetCollectorEnabled = viewModel::setCollectorEnabled,
                onSetIncludeAllCompanion = viewModel::setIncludeAllCompanionApps,
                onSetForegroundService = viewModel::setForegroundService,
                onSetCompactOngoing = viewModel::setCompactOngoing,
                onSetSourceUnitMode = viewModel::setSourceUnitMode,
                onSetJamProtection = viewModel::setJamProtection,
                onSetCustomPackages = viewModel::setCustomPackages,
                onSetStaleThresholdMinutes = viewModel::setStaleThresholdMinutes,
                onStartSensorSession = viewModel::startSensorSession,
                onStopSensorSession = viewModel::stopSensorSession,
                onSetSensorExpiryConfig = viewModel::setSensorExpiryConfig,
                onPickAlertRingtone = onPickAlertRingtone,
                onResetAlertRingtone = { viewModel.setDefaultAlertSoundUri("") },
                onSetNoAlarmsDuringCalls = viewModel::setNoAlarmsDuringCalls,
                onSetDisableAlertsStale = viewModel::setDisableAlertsStaleData,
                onSetAscendingOptions = viewModel::setAscendingOptions,
                onSetStartAlertsSnoozed = viewModel::setStartAlertsSnoozed,
                onSetShowButtonsInAlerts = viewModel::setShowButtonsInAlerts,
                onSetWakePhoneDuringAlerts = viewModel::setWakePhoneDuringAlerts,
                onSetFlashTorchOnAlert = viewModel::setFlashTorchOnAlert,
                onSetSpeechConfig = viewModel::setSpeechConfig,
                onTestSpeech = viewModel::testSpeech,
                onSetNightscoutConfig = viewModel::setNightscoutConfig,
                onSetNightscoutExtraConfig = viewModel::setNightscoutExtraConfig,
                onSyncNightscoutNow = { cb ->
                    viewModel.syncNightscoutNow { msg ->
                        cb(msg)
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                onSetLocalBroadcastEnabled = viewModel::setLocalBroadcastEnabled,
                onSetBroadcastTargetPackage = viewModel::setBroadcastTargetPackage,
                onSetAcceptLocalBroadcastReadings = viewModel::setAcceptLocalBroadcastReadings,
                onSetBroadcastSnoozeConfig = viewModel::setBroadcastSnoozeConfig,
                onSetIobCobConfig = viewModel::setIobCobConfig,
                onSetLocalWebServiceConfig = viewModel::setLocalWebServiceConfig,
                onSetRetentionDays = viewModel::setRetentionDays,
                onExportJson = onExportJson,
                onImportJson = onImportJson,
                onExportCsv = onExportCsv,
                onSimulateNotification = viewModel::simulateCamApsNotification,
                onInjectManualReading = viewModel::injectManualReading,
                onSeedDemo = viewModel::seedDemoCurve,
                onClearAllReadings = viewModel::clearAllReadings,
                onSetUseMgdl = viewModel::setUseMgdl,
                onSetTargetRange = viewModel::setTargetRange,
                onSetPredictionCurve = viewModel::setPredictionCurveEnabled,
                onSetShowChartTreatments = viewModel::setShowChartTreatments,
                onSetChartVisualOptions = viewModel::setChartVisualOptions,
                onSetShowExtraStatusLine = viewModel::setShowExtraStatusLine,
                onSetKeepScreenOn = viewModel::setKeepScreenOn,
                onSetDynamicColor = viewModel::setDynamicColor,
                onSetThemeMode = viewModel::setThemeMode,
                modifier = mod,
            )
        }
    }
}
