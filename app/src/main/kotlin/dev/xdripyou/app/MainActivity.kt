package dev.xdripyou.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.xdripyou.app.data.HealthConnectSyncManager
import dev.xdripyou.app.service.FloatingOverlayService
import dev.xdripyou.app.service.GlucoseForegroundService
import dev.xdripyou.app.service.NfcLibreReader
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
                Toast.makeText(this@MainActivity, "xDrip 2 You JSON-Backup gespeichert", Toast.LENGTH_SHORT).show()
            }
        }

    private val exportCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val csv = viewModel.exportReadingsCsv()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(this@MainActivity, "xDrip 2 You CSV-Export gespeichert", Toast.LENGTH_SHORT).show()
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

    private val importGlookoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val pair = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val name = queryDisplayName(uri)
                    bytes to name
                }
                val bytes = pair.first ?: return@launch
                viewModel.importGlookoExport(bytes, pair.second) { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setDefaultAlertSoundUri(uri?.toString() ?: "")
        }

    private val healthConnectPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            viewModel.onHealthConnectPermissionsResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startForegroundIfEnabled()
        handleNfcIntentIfPresent(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.settings.keepScreenOn) {
                if (state.settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            LaunchedEffect(state.settings.floatingOverlayEnabled) {
                if (state.settings.floatingOverlayEnabled) {
                    FloatingOverlayService.start(this@MainActivity)
                } else {
                    FloatingOverlayService.stop(this@MainActivity)
                }
            }

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
                    onOpenExactAlarms = {
                        SystemPermissionsHelper.openExactAlarmSettings(this)
                    },
                    onRequestHealthConnectPermissions = {
                        if (HealthConnectSyncManager.isAvailable(this)) {
                            healthConnectPermissionLauncher.launch(HealthConnectSyncManager.requiredPermissions)
                        } else {
                            HealthConnectSyncManager.openInstallOrSettings(this)
                        }
                    },
                    onPickAlertRingtone = {
                        val existing = state.settings.defaultAlertSoundUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            if (existing != null) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                            }
                        }
                        ringtonePickerLauncher.launch(intent)
                    },
                    onExportJson = {
                        exportBackupLauncher.launch("xdrip2you-backup.json")
                    },
                    onImportJson = {
                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    onExportCsv = {
                        exportCsvLauncher.launch("xdrip2you-werte.csv")
                    },
                    onImportGlookoFile = {
                        importGlookoLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "text/csv", "text/comma-separated-values", "*/*")
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntentIfPresent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        startForegroundIfEnabled()
    }

    private fun handleNfcIntentIfPresent(intent: Intent?) {
        val app = application as XdripYouApplication
        lifecycleScope.launch {
            val res = NfcLibreReader.handleTagIntent(app, intent) ?: return@launch
            val msg = res.getOrElse { "NFC Fehler: ${it.message}" }
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
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
