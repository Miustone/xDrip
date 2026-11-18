package dev.xdripyou.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.data.HealthConnectSyncManager
import dev.xdripyou.app.data.InsertOutcome
import dev.xdripyou.app.data.SettingsStore
import dev.xdripyou.app.domain.ActiveAlertState
import dev.xdripyou.app.domain.AgpStatsCalculator
import dev.xdripyou.app.domain.AlertProfile
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.AppSettings
import dev.xdripyou.app.domain.BasalSchedule
import dev.xdripyou.app.domain.CamApsParser
import dev.xdripyou.app.domain.ChartWindow
import dev.xdripyou.app.domain.CollectorEvent
import dev.xdripyou.app.domain.ExtendedGlucoseStats
import dev.xdripyou.app.domain.ExternalPumpStatus
import dev.xdripyou.app.domain.GlucosePredictor
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseStats
import dev.xdripyou.app.domain.IobCobCalculator
import dev.xdripyou.app.domain.IobCobSnapshot
import dev.xdripyou.app.domain.PermissionStatus
import dev.xdripyou.app.domain.PredictionPoint
import dev.xdripyou.app.domain.SourceUnitMode
import dev.xdripyou.app.domain.StatsPeriod
import dev.xdripyou.app.domain.ThemeModeOption
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.service.GlucoseForegroundService
import dev.xdripyou.app.service.SystemPermissionsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val isLoaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val readings24h: List<GlucoseReading> = emptyList(),
    val treatments24h: List<TreatmentEntry> = emptyList(),
    val alertRules: List<AlertRule> = emptyList(),
    val activeAlert: ActiveAlertState? = null,
    val collectorEvents: List<CollectorEvent> = emptyList(),
    val chartWindow: ChartWindow = ChartWindow.SIX_HOURS,
    val statsPeriod: StatsPeriod = StatsPeriod.DAY_1,
    val extendedStats: ExtendedGlucoseStats = ExtendedGlucoseStats(),
    val externalPumpStatus: ExternalPumpStatus? = null,
    val healthConnectAvailable: Boolean = false,
    val healthConnectGranted: Boolean = false,
    val permissions: PermissionStatus = PermissionStatus(),
    val nowMs: Long = System.currentTimeMillis(),
) {
    val latestReading: GlucoseReading? get() = readings24h.firstOrNull()

    val basalSchedule: BasalSchedule
        get() = BasalSchedule.decodeWire(settings.basalScheduleWire)

    val currentBasalRate: Double?
        get() = if (settings.showBasalRate) basalSchedule.rateAt(nowMs) else null

    val iobCob: IobCobSnapshot
        get() {
            val local = IobCobCalculator.snapshot(treatments24h, nowMs, settings)
            val ext = externalPumpStatus
            val extFresh = ext != null && (nowMs - ext.timestampMs) <= 1_800_000L
            return if (extFresh && !local.hasActiveIob && ext?.iobUnits != null) {
                IobCobSnapshot(
                    iobUnits = ext.iobUnits,
                    cobGrams = ext.cobGrams ?: local.cobGrams,
                )
            } else {
                local
            }
        }

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
    private val speechHelper = app.speechHelper

    private val chartWindowFlow = MutableStateFlow(ChartWindow.SIX_HOURS)
    private val statsPeriodFlow = MutableStateFlow(StatsPeriod.DAY_1)
    private val extendedStatsFlow = MutableStateFlow(ExtendedGlucoseStats())
    private val permissionsFlow = MutableStateFlow(SystemPermissionsHelper.getStatus(app))
    private val healthConnectGrantedFlow = MutableStateFlow(false)
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

    private val metaFlow = combine(
        repo.observeCollectorEvents(limit = 50),
        chartWindowFlow,
        statsPeriodFlow,
        extendedStatsFlow,
        permissionsFlow,
    ) { events, window, statsPeriod, extStats, perms ->
        MetaBundle(events, window, statsPeriod, extStats, perms)
    }

    val uiState: StateFlow<MainUiState> = combine(
        primaryDataFlow,
        metaFlow,
        repo.externalPumpStatusFlow,
        healthConnectGrantedFlow,
        nowMsFlow,
    ) { primary, meta, pumpStatus, hcGranted, nowMs ->
        MainUiState(
            isLoaded = true,
            settings = primary.settings,
            readings24h = primary.readings,
            treatments24h = primary.treatments,
            alertRules = primary.rules,
            activeAlert = primary.activeAlert,
            collectorEvents = meta.events,
            chartWindow = meta.window,
            statsPeriod = meta.statsPeriod,
            extendedStats = meta.extendedStats,
            externalPumpStatus = pumpStatus,
            healthConnectAvailable = HealthConnectSyncManager.isAvailable(app),
            healthConnectGranted = hcGranted,
            permissions = meta.perms,
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
        viewModelScope.launch {
            combine(statsPeriodFlow, primaryDataFlow) { period, primary ->
                Triple(period, primary.settings.targetLowMgdl, primary.settings.targetHighMgdl)
            }.collect { (period, targetLow, targetHigh) ->
                refreshExtendedStats(period, targetLow, targetHigh)
            }
        }
    }

    private suspend fun refreshExtendedStats(
        period: StatsPeriod,
        targetLowMgdl: Double,
        targetHighMgdl: Double,
    ) {
        val now = System.currentTimeMillis()
        val since = now - period.days * 86_400_000L
        val computed = withContext(Dispatchers.Default) {
            val readings = repo.getReadingsSince(since, limit = (period.days * 320).coerceAtLeast(600))
            AgpStatsCalculator.computeExtendedStats(
                readings = readings,
                period = period,
                targetLowMgdl = targetLowMgdl,
                targetHighMgdl = targetHighMgdl,
            )
        }
        extendedStatsFlow.value = computed
    }

    fun selectStatsPeriod(period: StatsPeriod) {
        statsPeriodFlow.value = period
    }

    fun refreshPermissions() {
        permissionsFlow.value = SystemPermissionsHelper.getStatus(app)
        nowMsFlow.value = System.currentTimeMillis()
        viewModelScope.launch {
            healthConnectGrantedFlow.value = runCatching { HealthConnectSyncManager.hasAllPermissions(app) }.getOrDefault(false)
        }
    }

    fun completeOnboarding(seedDemo: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.ONBOARDING_COMPLETED] = true }
            if (seedDemo && repo.getLatestReadings(1).isEmpty()) {
                repo.seedDemoCurve()
            }
            if (settingsStore.current().foregroundServiceEnabled && SystemPermissionsHelper.hasPostNotifications(app)) {
                GlucoseForegroundService.start(app)
            }
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.ONBOARDING_COMPLETED] = false }
        }
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

    fun setBroadcastTargetPackage(pkg: String) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.BROADCAST_TARGET_PACKAGE] = pkg.trim() }
        }
    }

    fun setAcceptLocalBroadcastReadings(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.ACCEPT_LOCAL_BROADCAST_READINGS] = enabled }
        }
    }

    fun setBroadcastSnoozeConfig(sendSnooze: Boolean, acceptSnooze: Boolean) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.BROADCAST_SNOOZE_ENABLED] = sendSnooze
                it[SettingsStore.Keys.ACCEPT_BROADCAST_SNOOZE] = acceptSnooze
            }
        }
    }

    fun setIobCobConfig(
        showIobCob: Boolean,
        diaMinutes: Int,
        peakMinutes: Int,
        carbDelayMinutes: Int,
        carbAbsorptionGPerH: Int,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.SHOW_IOB_COB] = showIobCob
                it[SettingsStore.Keys.INSULIN_DURATION_MINUTES] = diaMinutes.coerceIn(120, 480)
                it[SettingsStore.Keys.INSULIN_PEAK_MINUTES] = peakMinutes.coerceIn(30, 120)
                it[SettingsStore.Keys.CARB_DELAY_MINUTES] = carbDelayMinutes.coerceIn(0, 60)
                it[SettingsStore.Keys.CARB_ABSORPTION_G_PER_H] = carbAbsorptionGPerH.coerceIn(10, 90)
            }
            alertEngine.refreshOngoingNotification()
        }
    }

    fun setLocalWebServiceConfig(
        enabled: Boolean,
        openNetwork: Boolean,
        secret: String,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.LOCAL_WEB_SERVICE_ENABLED] = enabled
                it[SettingsStore.Keys.LOCAL_WEB_SERVICE_OPEN] = openNetwork
                it[SettingsStore.Keys.LOCAL_WEB_SERVICE_SECRET] = secret.trim()
            }
            app.localWebServer.syncWithSettings(settingsStore.current())
        }
    }

    fun setDexcomShareConfig(
        enabled: Boolean,
        usAccount: Boolean,
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.DEXCOM_SHARE_ENABLED] = enabled
                it[SettingsStore.Keys.DEXCOM_SHARE_US] = usAccount
                it[SettingsStore.Keys.DEXCOM_SHARE_USER] = username.trim()
                it[SettingsStore.Keys.DEXCOM_SHARE_PASS] = password
            }
        }
    }

    fun syncDexcomShareNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncDexcomShare()
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
            onResult(res.getOrElse { "Fehler: ${it.message}" })
        }
    }

    fun setLibreLinkConfig(
        enabled: Boolean,
        region: String,
        email: String,
        password: String,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.LIBRE_LINK_ENABLED] = enabled
                it[SettingsStore.Keys.LIBRE_LINK_REGION] = region
                it[SettingsStore.Keys.LIBRE_LINK_EMAIL] = email.trim()
                it[SettingsStore.Keys.LIBRE_LINK_PASS] = password
            }
        }
    }

    fun syncLibreLinkNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncLibreLinkUp()
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
            onResult(res.getOrElse { "Fehler: ${it.message}" })
        }
    }

    fun setHealthConnectConfig(enabled: Boolean, send: Boolean, receive: Boolean) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.HC_ENABLED] = enabled
                it[SettingsStore.Keys.HC_SEND] = send
                it[SettingsStore.Keys.HC_RECEIVE] = receive
            }
        }
    }

    fun syncHealthConnectNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncHealthConnect(app)
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
            onResult(res.getOrElse { "Fehler: ${it.message}" })
        }
    }

    fun setInfluxConfig(
        enabled: Boolean,
        url: String,
        database: String,
        org: String,
        username: String,
        tokenOrPass: String,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.INFLUX_ENABLED] = enabled
                it[SettingsStore.Keys.INFLUX_URL] = url.trim()
                it[SettingsStore.Keys.INFLUX_DB] = database.trim()
                it[SettingsStore.Keys.INFLUX_ORG] = org.trim()
                it[SettingsStore.Keys.INFLUX_USER] = username.trim()
                it[SettingsStore.Keys.INFLUX_PASS] = tokenOrPass
            }
        }
    }

    fun syncInfluxNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncInfluxDb()
            onResult(res.getOrElse { "Fehler: ${it.message}" })
        }
    }

    fun setTidepoolConfig(
        enabled: Boolean,
        devServers: Boolean,
        email: String,
        password: String,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.TIDEPOOL_ENABLED] = enabled
                it[SettingsStore.Keys.TIDEPOOL_DEV] = devServers
                it[SettingsStore.Keys.TIDEPOOL_EMAIL] = email.trim()
                it[SettingsStore.Keys.TIDEPOOL_PASS] = password
            }
        }
    }

    fun syncTidepoolNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncTidepool()
            onResult(res.getOrElse { "Fehler: ${it.message}" })
        }
    }

    fun setPeerSyncConfig(enabled: Boolean, host: String, secret: String) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.PEER_SYNC_ENABLED] = enabled
                it[SettingsStore.Keys.PEER_SYNC_HOST] = host.trim()
                it[SettingsStore.Keys.PEER_SYNC_SECRET] = secret.trim()
            }
        }
    }

    fun syncPeerMasterNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncPeerMaster()
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
            onResult(res.getOrElse { "Fehler: ${it.message}" })
        }
    }

    fun setBolusProfileConfig(
        carbRatioGramsPerUnit: Double,
        isfMgdlPerUnit: Double,
        bolusTargetMgdl: Double,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.CARB_RATIO] = carbRatioGramsPerUnit.coerceIn(2.0, 60.0)
                it[SettingsStore.Keys.ISF_MGDL] = isfMgdlPerUnit.coerceIn(10.0, 200.0)
                it[SettingsStore.Keys.BOLUS_TARGET_MGDL] = bolusTargetMgdl.coerceIn(70.0, 180.0)
            }
        }
    }

    fun setBasalProfileConfig(showBasalRate: Boolean, schedule: BasalSchedule) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.SHOW_BASAL_RATE] = showBasalRate
                it[SettingsStore.Keys.BASAL_SCHEDULE_WIRE] = schedule.encodeWire()
            }
        }
    }

    fun setNightscoutDeviceStatusEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.NS_DEVICE_STATUS] = enabled }
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

    fun setIncludeAllCompanionApps(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.INCLUDE_ALL_COMPANION] = enabled }
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

    fun setCompactOngoing(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.COMPACT_ONGOING] = enabled }
            alertEngine.refreshOngoingNotification()
        }
    }

    fun setStaleThresholdMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.STALE_THRESHOLD_MINUTES] = minutes.coerceIn(6, 45) }
        }
    }

    fun startSensorSession() {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.SENSOR_STARTED_AT_MS] = System.currentTimeMillis() }
        }
    }

    fun stopSensorSession() {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.SENSOR_STARTED_AT_MS] = 0L }
        }
    }

    fun setSensorExpiryConfig(expectedDays: Int, alertEnabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.SENSOR_EXPECTED_DAYS] = expectedDays.coerceIn(7, 30)
                it[SettingsStore.Keys.SENSOR_EXPIRY_ALERT] = alertEnabled
            }
        }
    }

    fun setNoAlarmsDuringCalls(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.NO_ALARMS_DURING_CALLS] = enabled }
        }
    }

    fun setDisableAlertsStaleData(enabled: Boolean, minutes: Int) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.DISABLE_ALERTS_STALE] = enabled
                it[SettingsStore.Keys.DISABLE_ALERTS_STALE_MINUTES] = minutes.coerceIn(10, 60)
            }
        }
    }

    fun setDefaultAlertSoundUri(uri: String) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.DEFAULT_ALERT_SOUND_URI] = uri }
        }
    }

    fun setAscendingOptions(delay3Min: Boolean, capMedium: Boolean) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.DELAY_ASCENDING_3MIN] = delay3Min
                it[SettingsStore.Keys.ASCENDING_TO_MEDIUM] = capMedium
            }
        }
    }

    fun setStartAlertsSnoozed(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.START_ALERTS_SNOOZED] = enabled }
        }
    }

    fun setShowButtonsInAlerts(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.SHOW_BUTTONS_IN_ALERTS] = enabled }
        }
    }

    fun setWakePhoneDuringAlerts(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.WAKE_PHONE_DURING_ALERTS] = enabled }
        }
    }

    fun setFlashTorchOnAlert(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.FLASH_TORCH_ON_ALERT] = enabled }
        }
    }

    fun setSpeechConfig(
        speakReadings: Boolean,
        speakAlerts: Boolean,
        speakTrend: Boolean,
        speed: Double,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.SPEAK_READINGS] = speakReadings
                it[SettingsStore.Keys.SPEAK_ALERTS] = speakAlerts
                it[SettingsStore.Keys.SPEAK_TREND] = speakTrend
                it[SettingsStore.Keys.SPEECH_SPEED] = speed.coerceIn(0.6, 1.6)
            }
        }
    }

    fun testSpeech() {
        val current = uiState.value
        val latest = current.latestReading
        if (latest != null) {
            speechHelper.speakReading(
                reading = latest,
                useMgdl = current.settings.useMgdl,
                includeTrend = current.settings.speakTrendEnabled,
                speed = current.settings.speechSpeed.toFloat(),
            )
        } else {
            speechHelper.speakText("xDrip 2 You Sprachausgabe aktiv.", current.settings.speechSpeed.toFloat())
        }
    }

    fun setShowChartTreatments(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.SHOW_CHART_TREATMENTS] = enabled }
        }
    }

    fun setChartVisualOptions(
        pointsOnly: Boolean,
        showGrid: Boolean,
        showAverageLine: Boolean,
        chartMaxMgdl: Int,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.CHART_POINTS_ONLY] = pointsOnly
                it[SettingsStore.Keys.SHOW_CHART_GRID] = showGrid
                it[SettingsStore.Keys.SHOW_CHART_AVG_LINE] = showAverageLine
                it[SettingsStore.Keys.CHART_MAX_MGDL] = chartMaxMgdl
            }
        }
    }

    fun setShowExtraStatusLine(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.SHOW_EXTRA_STATUS_LINE] = enabled }
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.KEEP_SCREEN_ON] = enabled }
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

    fun setHighPrediction(enabled: Boolean, thresholdMgdl: Double, horizonMinutes: Int) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.HIGH_PRED_ENABLED] = enabled
                it[SettingsStore.Keys.HIGH_PRED_THRESHOLD] = thresholdMgdl.coerceIn(140.0, 300.0)
                it[SettingsStore.Keys.HIGH_PRED_HORIZON] = horizonMinutes.coerceIn(10, 45)
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

    fun setNightscoutExtraConfig(uploadTreatments: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            settingsStore.update {
                it[SettingsStore.Keys.NS_UPLOAD_TREATMENTS] = uploadTreatments
                it[SettingsStore.Keys.NS_WIFI_ONLY] = wifiOnly
            }
        }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsStore.update { it[SettingsStore.Keys.RETENTION_DAYS] = days.coerceIn(7, 3650) }
            repo.pruneExpiredReadings()
        }
    }

    fun syncNightscoutNow(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncNightscout(SystemPermissionsHelper.getBatteryPercent(app))
            nowMsFlow.value = System.currentTimeMillis()
            alertEngine.refreshOngoingNotification()
            onResult(res.getOrElse { "Fehler: ${it.message}" })
        }
    }

    fun addTreatment(entry: TreatmentEntry) {
        viewModelScope.launch {
            repo.addOrUpdateTreatment(entry)
            alertEngine.refreshOngoingNotification()
            if (settingsStore.current().nightscoutUploadEnabled) {
                repo.syncNightscout()
            }
        }
    }

    fun deleteTreatment(id: Long) {
        viewModelScope.launch {
            repo.deleteTreatment(id)
            alertEngine.refreshOngoingNotification()
        }
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

    suspend fun exportReadingsCsv(): String = repo.exportReadingsCsv()

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

    private data class MetaBundle(
        val events: List<CollectorEvent>,
        val window: ChartWindow,
        val statsPeriod: StatsPeriod,
        val extendedStats: ExtendedGlucoseStats,
        val perms: PermissionStatus,
    )
}
