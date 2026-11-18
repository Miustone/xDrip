package dev.xdripyou.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.data.InsertOutcome
import dev.xdripyou.app.data.SettingsStore
import dev.xdripyou.app.domain.ActiveAlertState
import dev.xdripyou.app.domain.AlertProfile
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.AppSettings
import dev.xdripyou.app.domain.CamApsParser
import dev.xdripyou.app.domain.ChartWindow
import dev.xdripyou.app.domain.CollectorEvent
import dev.xdripyou.app.domain.GlucosePredictor
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseStats
import dev.xdripyou.app.domain.PermissionStatus
import dev.xdripyou.app.domain.PredictionPoint
import dev.xdripyou.app.domain.SourceUnitMode
import dev.xdripyou.app.domain.ThemeModeOption
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.service.GlucoseForegroundService
import dev.xdripyou.app.service.SystemPermissionsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val readings24h: List<GlucoseReading> = emptyList(),
    val treatments24h: List<TreatmentEntry> = emptyList(),
    val alertRules: List<AlertRule> = emptyList(),
    val activeAlert: ActiveAlertState? = null,
    val collectorEvents: List<CollectorEvent> = emptyList(),
    val chartWindow: ChartWindow = ChartWindow.SIX_HOURS,
    val permissions: PermissionStatus = PermissionStatus(),
    val nowMs: Long = System.currentTimeMillis(),
) {
    val latestReading: GlucoseReading? get() = readings24h.firstOrNull()

    val latestDeltaMgdl: Double?
        get() {
            if (readings24h.size < 2) return null
            val current = readings24h[0]
            val prev = readings24h[1]
            if (current.timestampMs - prev.timestampMs > 1_200_000L) return null
            return current.mgdl - prev.mgdl
        }

    val predictions: List<PredictionPoint>
        get() = if (settings.predictionCurveEnabled) {
            GlucosePredictor.predict(readings24h, nowMs)
        } else {
            emptyList()
        }

    val readingsInWindow: List<GlucoseReading>
        get() {
            val cutoff = nowMs - chartWindow.durationMs
            return readings24h.filter { it.timestampMs >= cutoff }
        }

    val treatmentsInWindow: List<TreatmentEntry>
        get() {
            val cutoff = nowMs - chartWindow.durationMs
            return treatments24h.filter { it.timestampMs >= cutoff }
        }

    val windowStats: GlucoseStats
        get() = GlucoseStats.compute(
            readings = readingsInWindow,
            targetLowMgdl = settings.targetLowMgdl,
            targetHighMgdl = settings.targetHighMgdl,
        )

    val stats24h: GlucoseStats
        get() = GlucoseStats.compute(
            readings = readings24h,
            targetLowMgdl = settings.targetLowMgdl,
            targetHighMgdl = settings.targetHighMgdl,
        )
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as XdripYouApplication
    private val repo = app.repository
    private val settingsStore = app.settingsStore
    private val alertEngine = app.alertEngine

    private val chartWindowFlow = MutableStateFlow(ChartWindow.SIX_HOURS)
    private val permissionsFlow = MutableStateFlow(SystemPermissionsHelper.getStatus(app))
    private val nowMsFlow = MutableStateFlow(System.currentTimeMillis())

    private val since24h = System.currentTimeMillis() - 24 * 3_600_000L

    private val primaryDataFlow = combine(
        settingsStore.settingsFlow,
        repo.observeReadingsSince(since24h, limit = 600),
        repo.observeTreatmentsSince(since24h, limit = 300),
        repo.observeAlertRules(),
        repo.observeActiveAlert(),
    ) { settings, readings, treatments, rules, activeAlert ->
        PrimaryBundle(settings, readings, treatments, rules, activeAlert)
    }

    val uiState: StateFlow<MainUiState> = combine(
        primaryDataFlow,
        repo.observeCollectorEvents(limit = 50),
        chartWindowFlow,
        permissionsFlow,
        nowMsFlow,
    ) { primary, events, window, perms, nowMs ->
        MainUiState(
            settings = primary.settings,
            readings24h = primary.readings,
            treatments24h = primary.treatments,
            alertRules = primary.rules,
            activeAlert = primary.activeAlert,
            collectorEvents = events,
            chartWindow = window,
            permissions = perms,
            nowMs = nowMs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(permissions = SystemPermissionsHelper.getStatus(app)),
    )

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                nowMsFlow.value = System.currentTimeMillis()
                permissionsFlow.value = SystemPermissionsHelper.getStatus(app)
            }
        }
    }

    fun refreshPermissions() {
        permissionsFlow.value = SystemPermissionsHelper.getStatus(app)
        nowMsFlow.value = System.currentTimeMillis()
    }

    fun selectWindow(window: ChartWindow) {
        chartWindowFlow.value = window
    }

    fun setUseMgdl(useMgdl: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.USE_MGDL] = useMgdl }
            alertEngine.refreshOngoingNotification()
        }
    }

    fun setTargetRange(lowMgdl: Double, highMgdl: Double) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.TARGET_LOW] = lowMgdl
                it[SettingsStore.Keys.TARGET_HIGH] = highMgdl
            }
        }
    }

    fun setPredictionCurveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.PREDICTION_CURVE] = enabled }
        }
    }

    fun setLocalBroadcastEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.LOCAL_BROADCAST] = enabled }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.DYNAMIC_COLOR] = enabled }
        }
    }

    fun setThemeMode(mode: ThemeModeOption) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.THEME_MODE] = mode.wireValue }
        }
    }

    fun setCollectorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.COLLECTOR_ENABLED] = enabled }
        }
    }

    fun setSourceUnitMode(mode: SourceUnitMode) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.SOURCE_UNIT_MODE] = mode.wireValue }
        }
    }

    fun setJamProtection(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.JAM_PROTECTION] = enabled }
        }
    }

    fun setCustomPackages(packages: String) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.CUSTOM_PACKAGES] = packages }
        }
    }

    fun setForegroundService(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.FOREGROUND_SERVICE] = enabled }
            if (enabled && SystemPermissionsHelper.hasPostNotifications(app)) {
                GlucoseForegroundService.start(app)
            } else {
                GlucoseForegroundService.stop(app)
            }
        }
    }

    fun setAlertProfile(profile: AlertProfile) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.ALERT_PROFILE] = profile.wireValue }
        }
    }

    fun setSmartAlerting(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.SMART_ALERTING] = enabled }
        }
    }

    fun setSmartSnoozing(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.SMART_SNOOZING] = enabled }
        }
    }

    fun setMissedReading(enabled: Boolean, minutes: Int, snoozeMinutes: Int) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.MISSED_ENABLED] = enabled
                it[SettingsStore.Keys.MISSED_MINUTES] = minutes.coerceIn(5, 180)
                it[SettingsStore.Keys.MISSED_SNOOZE_MINUTES] = snoozeMinutes.coerceIn(5, 180)
            }
        }
    }

    fun setRisingAlert(enabled: Boolean, rateMgdlPerMin: Double) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.RISING_ENABLED] = enabled
                it[SettingsStore.Keys.RISING_RATE] = rateMgdlPerMin.coerceIn(1.0, 6.0)
            }
        }
    }

    fun setFallingAlert(enabled: Boolean, rateMgdlPerMin: Double) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.FALLING_ENABLED] = enabled
                it[SettingsStore.Keys.FALLING_RATE] = rateMgdlPerMin.coerceIn(1.0, 6.0)
            }
        }
    }

    fun setLowPrediction(enabled: Boolean, thresholdMgdl: Double, horizonMinutes: Int) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.LOW_PRED_ENABLED] = enabled
                it[SettingsStore.Keys.LOW_PRED_THRESHOLD] = thresholdMgdl.coerceIn(50.0, 110.0)
                it[SettingsStore.Keys.LOW_PRED_HORIZON] = horizonMinutes.coerceIn(10, 45)
            }
        }
    }

    fun setPersistentHigh(enabled: Boolean, thresholdMgdl: Double, durationMinutes: Int) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.PERSISTENT_HIGH_ENABLED] = enabled
                it[SettingsStore.Keys.PERSISTENT_HIGH_THRESHOLD] = thresholdMgdl.coerceIn(140.0, 300.0)
                it[SettingsStore.Keys.PERSISTENT_HIGH_MINUTES] = durationMinutes.coerceIn(20, 240)
            }
        }
    }

    fun setNightscoutConfig(
        url: String,
        apiSecret: String,
        followEnabled: Boolean,
        uploadEnabled: Boolean,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.NS_URL] = url.trim()
                it[SettingsStore.Keys.NS_API_SECRET] = apiSecret.trim()
                it[SettingsStore.Keys.NS_FOLLOW_ENABLED] = followEnabled
                it[SettingsStore.Keys.NS_UPLOAD_ENABLED] = uploadEnabled
            }
        }
    }

    fun syncNightscoutNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncNightscout()
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
            onResult(res.getOrElse { "Fehler: ${it.message}" })
        }
    }

    fun addTreatment(entry: TreatmentEntry) {
        viewModelScope.launch {
            repo.addOrUpdateTreatment(entry)
            if (settingsStore.current().nightscoutUploadEnabled) {
                repo.syncNightscout()
            }
        }
    }

    fun deleteTreatment(id: Long) {
        viewModelScope.launch { repo.deleteTreatment(id) }
    }

    fun silenceAlerts(scope: String, minutes: Int) {
        viewModelScope.launch {
            val untilMs = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
            settingsStore.update {
                when (scope) {
                    "low" -> it[SettingsStore.Keys.LOW_DISABLED_UNTIL] = untilMs
                    "high" -> it[SettingsStore.Keys.HIGH_DISABLED_UNTIL] = untilMs
                    else -> {
                        it[SettingsStore.Keys.ALERTS_DISABLED_UNTIL] = untilMs
                        if (minutes > 0) {
                            it[SettingsStore.Keys.LOW_DISABLED_UNTIL] = 0L
                            it[SettingsStore.Keys.HIGH_DISABLED_UNTIL] = 0L
                        }
                    }
                }
            }
            if (minutes > 0) {
                alertEngine.stopAlert(clearActive = true)
            }
        }
    }

    fun upsertAlertRule(rule: AlertRule) {
        viewModelScope.launch { repo.upsertAlertRule(rule) }
    }

    fun deleteAlertRule(uuid: String) {
        viewModelScope.launch { repo.deleteAlertRule(uuid) }
    }

    fun snoozeActiveAlert(minutes: Int) {
        viewModelScope.launch { alertEngine.snoozeActiveAlert(minutes) }
    }

    fun stopActiveAlert() {
        viewModelScope.launch { alertEngine.stopAlert(clearActive = true) }
    }

    fun triggerTestAlert(uuid: String? = null) {
        viewModelScope.launch { alertEngine.triggerTestAlert(uuid) }
    }

    fun simulateCamApsNotification(rawText: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val mode = settingsStore.current().sourceUnitMode
            val parsed = CamApsParser.tryExtractGlucose(listOf(rawText), mode)
            if (parsed == null) {
                repo.recordCollectorEvent(
                    packageName = "com.camdiab.fx_alert.mgdl",
                    status = "unparsed",
                    rawTexts = rawText,
                    parsedMgdl = null,
                    message = "Simulierter Text nicht als Glukosewert erkannt",
                )
                onResult("Nicht erkannt")
                return@launch
            }
            val outcome = repo.insertReading(
                mgdl = parsed.mgdl,
                timestampMs = System.currentTimeMillis(),
                source = "CamAPS FX (Test)",
                rawText = parsed.rawText,
                applyDeduplication = false,
            )
            if (outcome is InsertOutcome.Inserted) {
                repo.recordCollectorEvent(
                    packageName = "com.camdiab.fx_alert.mgdl",
                    status = "accepted",
                    rawTexts = rawText,
                    parsedMgdl = parsed.mgdl,
                    message = "Simuliert übernommen (${parsed.detectedUnit})",
                )
                nowMsFlow.value = System.currentTimeMillis()
                alertEngine.refreshOngoingNotification()
                alertEngine.evaluateOnNewReading(outcome.reading)
                onResult("Übernommen: ${parsed.mgdl.toInt()} mg/dL")
            } else {
                onResult("Übersprungen")
            }
        }
    }

    fun injectManualReading(mgdl: Double) {
        viewModelScope.launch {
            val outcome = repo.insertReading(
                mgdl = mgdl.coerceIn(40.0, 405.0),
                timestampMs = System.currentTimeMillis(),
                source = "Manuell",
                rawText = null,
                applyDeduplication = false,
            )
            nowMsFlow.value = System.currentTimeMillis()
            if (outcome is InsertOutcome.Inserted) {
                alertEngine.refreshOngoingNotification()
                alertEngine.evaluateOnNewReading(outcome.reading)
            }
        }
    }

    fun seedDemoCurve() {
        viewModelScope.launch {
            repo.seedDemoCurve()
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
        }
    }

    fun clearAllReadings() {
        viewModelScope.launch {
            repo.clearAllReadings()
            alertEngine.stopAlert(clearActive = true)
            alertEngine.refreshOngoingNotification()
        }
    }

    suspend fun exportBackupJson(): String = repo.exportBackupJson()

    fun importBackupJson(jsonText: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = runCatching { repo.importBackupJson(jsonText) }
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
            onResult(res.getOrElse { "Import-Fehler: ${it.message}" })
        }
    }

    private data class PrimaryBundle(
        val settings: AppSettings,
        val readings: List<GlucoseReading>,
        val treatments: List<TreatmentEntry>,
        val rules: List<AlertRule>,
        val activeAlert: ActiveAlertState?,
    )
}
