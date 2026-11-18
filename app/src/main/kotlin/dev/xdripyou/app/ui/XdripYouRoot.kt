package dev.xdripyou.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.xdripyou.app.ui.screens.AlertsScreen
import dev.xdripyou.app.ui.screens.HistoryScreen
import dev.xdripyou.app.ui.screens.OverviewScreen
import dev.xdripyou.app.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XdripYouRoot(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenNotificationListener: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenDndPolicy: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val titles = listOf("xDrip You", "Alarme", "Verlauf", "Quelle & Setup")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titles[selectedTab],
                        fontWeight = FontWeight.Bold,
                    )
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
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Rounded.Sensors, contentDescription = null) },
                    label = { Text("Quelle") },
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
                onOpenNotificationListener = onOpenNotificationListener,
                onRequestPostNotifications = onRequestPostNotifications,
                onRequestBatteryExemption = onRequestBatteryExemption,
                onSeedDemo = viewModel::seedDemoCurve,
                onOpenSettingsTab = { selectedTab = 3 },
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
                modifier = mod,
            )
            2 -> HistoryScreen(
                state = state,
                modifier = mod,
            )
            else -> SettingsScreen(
                state = state,
                onOpenNotificationListener = onOpenNotificationListener,
                onRequestPostNotifications = onRequestPostNotifications,
                onRequestBatteryExemption = onRequestBatteryExemption,
                onOpenDndPolicy = onOpenDndPolicy,
                onSetCollectorEnabled = viewModel::setCollectorEnabled,
                onSetForegroundService = viewModel::setForegroundService,
                onSetSourceUnitMode = viewModel::setSourceUnitMode,
                onSetJamProtection = viewModel::setJamProtection,
                onSetCustomPackages = viewModel::setCustomPackages,
                onSimulateNotification = viewModel::simulateCamApsNotification,
                onInjectManualReading = viewModel::injectManualReading,
                onSeedDemo = viewModel::seedDemoCurve,
                onClearAllReadings = viewModel::clearAllReadings,
                onSetUseMgdl = viewModel::setUseMgdl,
                onSetTargetRange = viewModel::setTargetRange,
                onSetDynamicColor = viewModel::setDynamicColor,
                onSetThemeMode = viewModel::setThemeMode,
                modifier = mod,
            )
        }
    }
}
