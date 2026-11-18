package dev.xdripyou.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.xdripyou.app.service.GlucoseForegroundService
import dev.xdripyou.app.service.SystemPermissionsHelper
import dev.xdripyou.app.ui.MainViewModel
import dev.xdripyou.app.ui.XdripYouRoot
import dev.xdripyou.app.ui.theme.XdripYouTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.refreshPermissions()
            if (granted) {
                startForegroundIfEnabled()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startForegroundIfEnabled()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            XdripYouTheme(
                themeMode = state.settings.themeMode,
                dynamicColor = state.settings.dynamicColorEnabled,
            ) {
                XdripYouRoot(
                    state = state,
                    viewModel = viewModel,
                    onOpenNotificationListener = {
                        SystemPermissionsHelper.openNotificationListenerSettings(this)
                    },
                    onRequestPostNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestBatteryExemption = {
                        SystemPermissionsHelper.openBatteryOptimizationSettings(this)
                    },
                    onOpenDndPolicy = {
                        SystemPermissionsHelper.openDndPolicySettings(this)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        startForegroundIfEnabled()
    }

    private fun startForegroundIfEnabled() {
        val app = application as XdripYouApplication
        lifecycleScope.launch {
            if (app.settingsStore.current().foregroundServiceEnabled &&
                SystemPermissionsHelper.hasPostNotifications(this@MainActivity)
            ) {
                GlucoseForegroundService.start(this@MainActivity)
            }
        }
    }
}
