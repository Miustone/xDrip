package dev.xdripyou.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.xdripyou.app.domain.AlertProfile
import dev.xdripyou.app.domain.AppSettings
import dev.xdripyou.app.domain.SourceUnitMode
import dev.xdripyou.app.domain.ThemeModeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xdrip_you_settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settingsFlow: Flow<AppSettings> = store.data.map { prefs -> prefs.toAppSettings() }

    suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun update(block: (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    suspend fun getCompanionLastState(): Pair<Long, Long> {
        val prefs = store.data.first()
        return (prefs[Keys.LAST_COMPANION_VAL] ?: -1L) to (prefs[Keys.LAST_COMPANION_REPEAT] ?: 0L)
    }

    suspend fun setCompanionLastState(valueMgdl: Long, repeatCount: Long) {
        store.edit { prefs ->
            prefs[Keys.LAST_COMPANION_VAL] = valueMgdl
            prefs[Keys.LAST_COMPANION_REPEAT] = repeatCount
        }
    }

    suspend fun resetCompanionJamState() {
        store.edit { prefs ->
            prefs.remove(Keys.LAST_COMPANION_VAL)
            prefs.remove(Keys.LAST_COMPANION_REPEAT)
        }
    }

    suspend fun getAlertCooldowns(): AlertCooldowns {
        val prefs = store.data.first()
        return AlertCooldowns(
            lastMissedMs = prefs[Keys.LAST_MISSED_ALERT_MS] ?: 0L,
            lastRapidMs = prefs[Keys.LAST_RAPID_ALERT_MS] ?: 0L,
            lastLowPredMs = prefs[Keys.LAST_LOW_PRED_ALERT_MS] ?: 0L,
            lastHighPredMs = prefs[Keys.LAST_HIGH_PRED_ALERT_MS] ?: 0L,
            lastPersistentHighMs = prefs[Keys.LAST_PERSISTENT_HIGH_ALERT_MS] ?: 0L,
        )
    }

    suspend fun setLastMissedAlertMs(ts: Long) = update { it[Keys.LAST_MISSED_ALERT_MS] = ts }
    suspend fun setLastRapidAlertMs(ts: Long) = update { it[Keys.LAST_RAPID_ALERT_MS] = ts }
    suspend fun setLastLowPredAlertMs(ts: Long) = update { it[Keys.LAST_LOW_PRED_ALERT_MS] = ts }
    suspend fun setLastHighPredAlertMs(ts: Long) = update { it[Keys.LAST_HIGH_PRED_ALERT_MS] = ts }
    suspend fun setLastPersistentHighAlertMs(ts: Long) = update { it[Keys.LAST_PERSISTENT_HIGH_ALERT_MS] = ts }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
        useMgdl = this[Keys.USE_MGDL] ?: true,
        collectorEnabled = this[Keys.COLLECTOR_ENABLED] ?: true,
        includeAllCompanionApps = this[Keys.INCLUDE_ALL_COMPANION] ?: false,
        sourceUnitMode = SourceUnitMode.fromWire(this[Keys.SOURCE_UNIT_MODE]),
        jamProtection = this[Keys.JAM_PROTECTION] ?: true,
        foregroundServiceEnabled = this[Keys.FOREGROUND_SERVICE] ?: true,
        compactOngoingNotification = this[Keys.COMPACT_ONGOING] ?: false,
        customPackages = this[Keys.CUSTOM_PACKAGES] ?: "",
        staleThresholdMinutes = this[Keys.STALE_THRESHOLD_MINUTES] ?: 11,
        sensorStartedAtMs = this[Keys.SENSOR_STARTED_AT_MS] ?: 0L,
        sensorExpectedDays = this[Keys.SENSOR_EXPECTED_DAYS] ?: 14,
        sensorExpiryAlertEnabled = this[Keys.SENSOR_EXPIRY_ALERT] ?: false,
        smartSnoozing = this[Keys.SMART_SNOOZING] ?: true,
        smartAlerting = this[Keys.SMART_ALERTING] ?: true,
        delayAscending3Min = this[Keys.DELAY_ASCENDING_3MIN] ?: true,
        ascendingToMedium = this[Keys.ASCENDING_TO_MEDIUM] ?: false,
        startAlertsSnoozed = this[Keys.START_ALERTS_SNOOZED] ?: false,
        showButtonsInAlerts = this[Keys.SHOW_BUTTONS_IN_ALERTS] ?: true,
        wakePhoneDuringAlerts = this[Keys.WAKE_PHONE_DURING_ALERTS] ?: false,
        flashTorchOnAlert = this[Keys.FLASH_TORCH_ON_ALERT] ?: false,
        noAlarmsDuringCalls = this[Keys.NO_ALARMS_DURING_CALLS] ?: true,
        disableAlertsStaleData = this[Keys.DISABLE_ALERTS_STALE] ?: false,
        disableAlertsStaleMinutes = this[Keys.DISABLE_ALERTS_STALE_MINUTES] ?: 15,
        defaultAlertSoundUri = this[Keys.DEFAULT_ALERT_SOUND_URI] ?: "",
        alertProfile = AlertProfile.fromWire(this[Keys.ALERT_PROFILE]),
        speakReadingsEnabled = this[Keys.SPEAK_READINGS] ?: false,
        speakAlertsEnabled = this[Keys.SPEAK_ALERTS] ?: false,
        speakTrendEnabled = this[Keys.SPEAK_TREND] ?: true,
        speechSpeed = this[Keys.SPEECH_SPEED] ?: 1.0,
        missedReadingEnabled = this[Keys.MISSED_ENABLED] ?: true,
        missedReadingMinutes = this[Keys.MISSED_MINUTES] ?: 20,
        missedReadingSnoozeMinutes = this[Keys.MISSED_SNOOZE_MINUTES] ?: 20,
        risingAlertEnabled = this[Keys.RISING_ENABLED] ?: false,
        risingRateMgdlPerMin = this[Keys.RISING_RATE] ?: 3.0,
        fallingAlertEnabled = this[Keys.FALLING_ENABLED] ?: false,
        fallingRateMgdlPerMin = this[Keys.FALLING_RATE] ?: 3.0,
        lowPredictionEnabled = this[Keys.LOW_PRED_ENABLED] ?: false,
        lowPredictionThresholdMgdl = this[Keys.LOW_PRED_THRESHOLD] ?: 70.0,
        lowPredictionHorizonMinutes = this[Keys.LOW_PRED_HORIZON] ?: 25,
        highPredictionEnabled = this[Keys.HIGH_PRED_ENABLED] ?: false,
        highPredictionThresholdMgdl = this[Keys.HIGH_PRED_THRESHOLD] ?: 200.0,
        highPredictionHorizonMinutes = this[Keys.HIGH_PRED_HORIZON] ?: 25,
        persistentHighEnabled = this[Keys.PERSISTENT_HIGH_ENABLED] ?: false,
        persistentHighThresholdMgdl = this[Keys.PERSISTENT_HIGH_THRESHOLD] ?: 180.0,
        persistentHighMinutes = this[Keys.PERSISTENT_HIGH_MINUTES] ?: 60,
        alertsDisabledUntilMs = this[Keys.ALERTS_DISABLED_UNTIL] ?: 0L,
        lowAlertsDisabledUntilMs = this[Keys.LOW_DISABLED_UNTIL] ?: 0L,
        highAlertsDisabledUntilMs = this[Keys.HIGH_DISABLED_UNTIL] ?: 0L,
        targetLowMgdl = this[Keys.TARGET_LOW] ?: 70.0,
        targetHighMgdl = this[Keys.TARGET_HIGH] ?: 180.0,
        predictionCurveEnabled = this[Keys.PREDICTION_CURVE] ?: true,
        showChartTreatments = this[Keys.SHOW_CHART_TREATMENTS] ?: true,
        chartPointsOnly = this[Keys.CHART_POINTS_ONLY] ?: false,
        showChartGrid = this[Keys.SHOW_CHART_GRID] ?: true,
        showChartAverageLine = this[Keys.SHOW_CHART_AVG_LINE] ?: false,
        chartMaxMgdl = this[Keys.CHART_MAX_MGDL] ?: 0,
        showExtraStatusLine = this[Keys.SHOW_EXTRA_STATUS_LINE] ?: true,
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: false,
        localBroadcastEnabled = this[Keys.LOCAL_BROADCAST] ?: true,
        broadcastTargetPackage = this[Keys.BROADCAST_TARGET_PACKAGE] ?: "",
        nightscoutFollowEnabled = this[Keys.NS_FOLLOW_ENABLED] ?: false,
        nightscoutUploadEnabled = this[Keys.NS_UPLOAD_ENABLED] ?: false,
        nightscoutUploadTreatments = this[Keys.NS_UPLOAD_TREATMENTS] ?: true,
        nightscoutWifiOnly = this[Keys.NS_WIFI_ONLY] ?: false,
        nightscoutUrl = this[Keys.NS_URL] ?: "",
        nightscoutApiSecret = this[Keys.NS_API_SECRET] ?: "",
        retentionDays = this[Keys.RETENTION_DAYS] ?: 90,
        showIobCob = this[Keys.SHOW_IOB_COB] ?: true,
        insulinDurationMinutes = this[Keys.INSULIN_DURATION_MINUTES] ?: 270,
        insulinPeakMinutes = this[Keys.INSULIN_PEAK_MINUTES] ?: 65,
        carbDelayMinutes = this[Keys.CARB_DELAY_MINUTES] ?: 15,
        carbAbsorptionGramsPerHour = this[Keys.CARB_ABSORPTION_G_PER_H] ?: 30,
        localWebServiceEnabled = this[Keys.LOCAL_WEB_SERVICE_ENABLED] ?: false,
        localWebServiceOpenNetwork = this[Keys.LOCAL_WEB_SERVICE_OPEN] ?: false,
        localWebServiceSecret = this[Keys.LOCAL_WEB_SERVICE_SECRET] ?: "",
        acceptLocalBroadcastReadings = this[Keys.ACCEPT_LOCAL_BROADCAST_READINGS] ?: false,
        broadcastSnoozeEnabled = this[Keys.BROADCAST_SNOOZE_ENABLED] ?: false,
        acceptBroadcastSnooze = this[Keys.ACCEPT_BROADCAST_SNOOZE] ?: false,
        dexcomShareEnabled = this[Keys.DEXCOM_SHARE_ENABLED] ?: false,
        dexcomShareUsAccount = this[Keys.DEXCOM_SHARE_US] ?: false,
        dexcomShareUsername = this[Keys.DEXCOM_SHARE_USER] ?: "",
        dexcomSharePassword = this[Keys.DEXCOM_SHARE_PASS] ?: "",
        libreLinkEnabled = this[Keys.LIBRE_LINK_ENABLED] ?: false,
        libreLinkRegion = this[Keys.LIBRE_LINK_REGION] ?: "eu",
        libreLinkEmail = this[Keys.LIBRE_LINK_EMAIL] ?: "",
        libreLinkPassword = this[Keys.LIBRE_LINK_PASS] ?: "",
        carbRatioGramsPerUnit = this[Keys.CARB_RATIO] ?: 10.0,
        isfMgdlPerUnit = this[Keys.ISF_MGDL] ?: 40.0,
        bolusTargetMgdl = this[Keys.BOLUS_TARGET_MGDL] ?: 110.0,
        showBasalRate = this[Keys.SHOW_BASAL_RATE] ?: false,
        basalScheduleWire = this[Keys.BASAL_SCHEDULE_WIRE] ?: "",
        nightscoutDeviceStatusEnabled = this[Keys.NS_DEVICE_STATUS] ?: true,
        dynamicColorEnabled = this[Keys.DYNAMIC_COLOR] ?: true,
        themeMode = ThemeModeOption.fromWire(this[Keys.THEME_MODE]),
    )

    data class AlertCooldowns(
        val lastMissedMs: Long,
        val lastRapidMs: Long,
        val lastLowPredMs: Long,
        val lastHighPredMs: Long,
        val lastPersistentHighMs: Long,
    )

    object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val USE_MGDL = booleanPreferencesKey("use_mgdl")
        val COLLECTOR_ENABLED = booleanPreferencesKey("collector_enabled")
        val INCLUDE_ALL_COMPANION = booleanPreferencesKey("include_all_companion")
        val SOURCE_UNIT_MODE = stringPreferencesKey("source_unit_mode")
        val JAM_PROTECTION = booleanPreferencesKey("jam_protection")
        val FOREGROUND_SERVICE = booleanPreferencesKey("foreground_service")
        val COMPACT_ONGOING = booleanPreferencesKey("compact_ongoing")
        val CUSTOM_PACKAGES = stringPreferencesKey("custom_packages")
        val STALE_THRESHOLD_MINUTES = intPreferencesKey("stale_threshold_minutes")
        val SENSOR_STARTED_AT_MS = longPreferencesKey("sensor_started_at_ms")
        val SENSOR_EXPECTED_DAYS = intPreferencesKey("sensor_expected_days")
        val SENSOR_EXPIRY_ALERT = booleanPreferencesKey("sensor_expiry_alert")
        val SMART_SNOOZING = booleanPreferencesKey("smart_snoozing")
        val SMART_ALERTING = booleanPreferencesKey("smart_alerting")
        val DELAY_ASCENDING_3MIN = booleanPreferencesKey("delay_ascending_3min")
        val ASCENDING_TO_MEDIUM = booleanPreferencesKey("ascending_to_medium")
        val START_ALERTS_SNOOZED = booleanPreferencesKey("start_alerts_snoozed")
        val SHOW_BUTTONS_IN_ALERTS = booleanPreferencesKey("show_buttons_in_alerts")
        val WAKE_PHONE_DURING_ALERTS = booleanPreferencesKey("wake_phone_during_alerts")
        val FLASH_TORCH_ON_ALERT = booleanPreferencesKey("flash_torch_on_alert")
        val NO_ALARMS_DURING_CALLS = booleanPreferencesKey("no_alarms_during_calls")
        val DISABLE_ALERTS_STALE = booleanPreferencesKey("disable_alerts_stale")
        val DISABLE_ALERTS_STALE_MINUTES = intPreferencesKey("disable_alerts_stale_minutes")
        val DEFAULT_ALERT_SOUND_URI = stringPreferencesKey("default_alert_sound_uri")
        val ALERT_PROFILE = stringPreferencesKey("alert_profile")
        val SPEAK_READINGS = booleanPreferencesKey("speak_readings")
        val SPEAK_ALERTS = booleanPreferencesKey("speak_alerts")
        val SPEAK_TREND = booleanPreferencesKey("speak_trend")
        val SPEECH_SPEED = doublePreferencesKey("speech_speed")
        val MISSED_ENABLED = booleanPreferencesKey("missed_enabled")
        val MISSED_MINUTES = intPreferencesKey("missed_minutes")
        val MISSED_SNOOZE_MINUTES = intPreferencesKey("missed_snooze_minutes")
        val RISING_ENABLED = booleanPreferencesKey("rising_enabled")
        val RISING_RATE = doublePreferencesKey("rising_rate")
        val FALLING_ENABLED = booleanPreferencesKey("falling_enabled")
        val FALLING_RATE = doublePreferencesKey("falling_rate")
        val LOW_PRED_ENABLED = booleanPreferencesKey("low_pred_enabled")
        val LOW_PRED_THRESHOLD = doublePreferencesKey("low_pred_threshold")
        val LOW_PRED_HORIZON = intPreferencesKey("low_pred_horizon")
        val HIGH_PRED_ENABLED = booleanPreferencesKey("high_pred_enabled")
        val HIGH_PRED_THRESHOLD = doublePreferencesKey("high_pred_threshold")
        val HIGH_PRED_HORIZON = intPreferencesKey("high_pred_horizon")
        val PERSISTENT_HIGH_ENABLED = booleanPreferencesKey("persistent_high_enabled")
        val PERSISTENT_HIGH_THRESHOLD = doublePreferencesKey("persistent_high_threshold")
        val PERSISTENT_HIGH_MINUTES = intPreferencesKey("persistent_high_minutes")
        val ALERTS_DISABLED_UNTIL = longPreferencesKey("alerts_disabled_until")
        val LOW_DISABLED_UNTIL = longPreferencesKey("low_disabled_until")
        val HIGH_DISABLED_UNTIL = longPreferencesKey("high_disabled_until")
        val TARGET_LOW = doublePreferencesKey("target_low")
        val TARGET_HIGH = doublePreferencesKey("target_high")
        val PREDICTION_CURVE = booleanPreferencesKey("prediction_curve")
        val SHOW_CHART_TREATMENTS = booleanPreferencesKey("show_chart_treatments")
        val CHART_POINTS_ONLY = booleanPreferencesKey("chart_points_only")
        val SHOW_CHART_GRID = booleanPreferencesKey("show_chart_grid")
        val SHOW_CHART_AVG_LINE = booleanPreferencesKey("show_chart_avg_line")
        val CHART_MAX_MGDL = intPreferencesKey("chart_max_mgdl")
        val SHOW_EXTRA_STATUS_LINE = booleanPreferencesKey("show_extra_status_line")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LOCAL_BROADCAST = booleanPreferencesKey("local_broadcast")
        val BROADCAST_TARGET_PACKAGE = stringPreferencesKey("broadcast_target_package")
        val NS_FOLLOW_ENABLED = booleanPreferencesKey("ns_follow_enabled")
        val NS_UPLOAD_ENABLED = booleanPreferencesKey("ns_upload_enabled")
        val NS_UPLOAD_TREATMENTS = booleanPreferencesKey("ns_upload_treatments")
        val NS_WIFI_ONLY = booleanPreferencesKey("ns_wifi_only")
        val NS_URL = stringPreferencesKey("ns_url")
        val NS_API_SECRET = stringPreferencesKey("ns_api_secret")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val SHOW_IOB_COB = booleanPreferencesKey("show_iob_cob")
        val INSULIN_DURATION_MINUTES = intPreferencesKey("insulin_duration_minutes")
        val INSULIN_PEAK_MINUTES = intPreferencesKey("insulin_peak_minutes")
        val CARB_DELAY_MINUTES = intPreferencesKey("carb_delay_minutes")
        val CARB_ABSORPTION_G_PER_H = intPreferencesKey("carb_absorption_g_per_h")
        val LOCAL_WEB_SERVICE_ENABLED = booleanPreferencesKey("local_web_service_enabled")
        val LOCAL_WEB_SERVICE_OPEN = booleanPreferencesKey("local_web_service_open")
        val LOCAL_WEB_SERVICE_SECRET = stringPreferencesKey("local_web_service_secret")
        val ACCEPT_LOCAL_BROADCAST_READINGS = booleanPreferencesKey("accept_local_broadcast_readings")
        val BROADCAST_SNOOZE_ENABLED = booleanPreferencesKey("broadcast_snooze_enabled")
        val ACCEPT_BROADCAST_SNOOZE = booleanPreferencesKey("accept_broadcast_snooze")
        val DEXCOM_SHARE_ENABLED = booleanPreferencesKey("dexcom_share_enabled")
        val DEXCOM_SHARE_US = booleanPreferencesKey("dexcom_share_us")
        val DEXCOM_SHARE_USER = stringPreferencesKey("dexcom_share_user")
        val DEXCOM_SHARE_PASS = stringPreferencesKey("dexcom_share_pass")
        val LIBRE_LINK_ENABLED = booleanPreferencesKey("libre_link_enabled")
        val LIBRE_LINK_REGION = stringPreferencesKey("libre_link_region")
        val LIBRE_LINK_EMAIL = stringPreferencesKey("libre_link_email")
        val LIBRE_LINK_PASS = stringPreferencesKey("libre_link_pass")
        val CARB_RATIO = doublePreferencesKey("carb_ratio")
        val ISF_MGDL = doublePreferencesKey("isf_mgdl")
        val BOLUS_TARGET_MGDL = doublePreferencesKey("bolus_target_mgdl")
        val SHOW_BASAL_RATE = booleanPreferencesKey("show_basal_rate")
        val BASAL_SCHEDULE_WIRE = stringPreferencesKey("basal_schedule_wire")
        val NS_DEVICE_STATUS = booleanPreferencesKey("ns_device_status")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")

        val LAST_COMPANION_VAL = longPreferencesKey("last_companion_val")
        val LAST_COMPANION_REPEAT = longPreferencesKey("last_companion_repeat")
        val LAST_MISSED_ALERT_MS = longPreferencesKey("last_missed_alert_ms")
        val LAST_RAPID_ALERT_MS = longPreferencesKey("last_rapid_alert_ms")
        val LAST_LOW_PRED_ALERT_MS = longPreferencesKey("last_low_pred_alert_ms")
        val LAST_HIGH_PRED_ALERT_MS = longPreferencesKey("last_high_pred_alert_ms")
        val LAST_PERSISTENT_HIGH_ALERT_MS = longPreferencesKey("last_persistent_high_alert_ms")
    }
}
