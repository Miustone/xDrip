package dev.xdripyou.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.refreshPermissions()
            if (granted) {
                startForegroundIfEnabled()
            }
        }

    private val exportBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val json = viewModel.exportBackupJson()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(this@MainActivity, "xDrip 2 You Backup gespeichert", Toast.LENGTH_SHORT).show()
            }
        }

    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: return@launch
                viewModel.importBackupJson(text) { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
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
                    onExportBackup = {
                        exportBackupLauncher.launch("xdrip2you-backup.json")
                    },
                    onImportBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
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
