package dev.xdripyou.app.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xdripyou.app.ui.screens.AlertsScreen
import dev.xdripyou.app.ui.screens.BedsideScreen
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
    onRequestHealthConnectPermissions: () -> Unit,
    onPickAlertRingtone: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImportGlookoFile: () -> Unit,
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
    var bedsideMode by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (bedsideMode) {
        BedsideScreen(
            state = state,
            onExit = { bedsideMode = false },
        )
        return
    }

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
            if (!(isLandscape && selectedTab == 0)) {
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
                    actions = {
                        if (selectedTab == 0) {
                            IconButton(onClick = { bedsideMode = true }) {
                                Icon(Icons.Rounded.NightsStay, contentDescription = "Nachttisch-Modus")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isLandscape) {
                CompactBottomNavBar(
                    selectedTab = selectedTab,
                    onSelectTab = { tab ->
                        if (tab == 3 && selectedTab == 3) settingsSection = SettingsSection.HUB
                        selectedTab = tab
                    },
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
                onSetHighPrediction = viewModel::setHighPrediction,
                onSetPersistentHigh = viewModel::setPersistentHigh,
                onUpsertReminder = viewModel::upsertReminder,
                onDeleteReminder = viewModel::deleteReminder,
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
                viewModel = viewModel,
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
                onSetBolusProfileConfig = viewModel::setBolusProfileConfig,
                onSetBasalProfileConfig = viewModel::setBasalProfileConfig,
                onSetNightscoutDeviceStatusEnabled = viewModel::setNightscoutDeviceStatusEnabled,
                onSetDexcomShareConfig = viewModel::setDexcomShareConfig,
                onSyncDexcomShareNow = { cb ->
                    viewModel.syncDexcomShareNow { msg ->
                        cb(msg)
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                onSetLibreLinkConfig = viewModel::setLibreLinkConfig,
                onSyncLibreLinkNow = { cb ->
                    viewModel.syncLibreLinkNow { msg ->
                        cb(msg)
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                onSetLocalWebServiceConfig = viewModel::setLocalWebServiceConfig,
                onRequestHealthConnectPermissions = onRequestHealthConnectPermissions,
                onSetHealthConnectConfig = viewModel::setHealthConnectConfig,
                onSyncHealthConnectNow = { cb ->
                    viewModel.syncHealthConnectNow { msg ->
                        cb(msg)
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                onSetInfluxConfig = viewModel::setInfluxConfig,
                onSyncInfluxNow = { cb ->
                    viewModel.syncInfluxNow { msg ->
                        cb(msg)
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                onSetTidepoolConfig = viewModel::setTidepoolConfig,
                onSyncTidepoolNow = { cb ->
                    viewModel.syncTidepoolNow { msg ->
                        cb(msg)
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                onSetPeerSyncConfig = viewModel::setPeerSyncConfig,
                onSyncPeerMasterNow = { cb ->
                    viewModel.syncPeerMasterNow { msg ->
                        cb(msg)
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                onSetRetentionDays = viewModel::setRetentionDays,
                onExportJson = onExportJson,
                onImportJson = onImportJson,
                onExportCsv = onExportCsv,
                onImportGlookoFile = onImportGlookoFile,
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

private data class NavTabMeta(val index: Int, val label: String, val icon: ImageVector)

@Composable
private fun CompactBottomNavBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tabs = listOf(
        NavTabMeta(0, "Übersicht", Icons.AutoMirrored.Rounded.ShowChart),
        NavTabMeta(1, "Alarme", Icons.Rounded.NotificationsActive),
        NavTabMeta(2, "Verlauf", Icons.Rounded.History),
        NavTabMeta(3, "Optionen", Icons.Rounded.Tune),
    )

    Surface(
        color = scheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = selectedTab == tab.index
                val pillBg = if (selected) scheme.secondaryContainer else Color.Transparent
                val fg = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectTab(tab.index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(pillBg)
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = fg,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
