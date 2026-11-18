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
            lastPersistentHighMs = prefs[Keys.LAST_PERSISTENT_HIGH_ALERT_MS] ?: 0L,
        )
    }

    suspend fun setLastMissedAlertMs(ts: Long) = update { it[Keys.LAST_MISSED_ALERT_MS] = ts }
    suspend fun setLastRapidAlertMs(ts: Long) = update { it[Keys.LAST_RAPID_ALERT_MS] = ts }
    suspend fun setLastLowPredAlertMs(ts: Long) = update { it[Keys.LAST_LOW_PRED_ALERT_MS] = ts }
    suspend fun setLastPersistentHighAlertMs(ts: Long) = update { it[Keys.LAST_PERSISTENT_HIGH_ALERT_MS] = ts }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        useMgdl = this[Keys.USE_MGDL] ?: true,
        collectorEnabled = this[Keys.COLLECTOR_ENABLED] ?: true,
        sourceUnitMode = SourceUnitMode.fromWire(this[Keys.SOURCE_UNIT_MODE]),
        jamProtection = this[Keys.JAM_PROTECTION] ?: true,
        foregroundServiceEnabled = this[Keys.FOREGROUND_SERVICE] ?: true,
        compactOngoingNotification = this[Keys.COMPACT_ONGOING] ?: false,
        customPackages = this[Keys.CUSTOM_PACKAGES] ?: "",
        staleThresholdMinutes = this[Keys.STALE_THRESHOLD_MINUTES] ?: 11,
        sensorStartedAtMs = this[Keys.SENSOR_STARTED_AT_MS] ?: 0L,
        smartSnoozing = this[Keys.SMART_SNOOZING] ?: true,
        smartAlerting = this[Keys.SMART_ALERTING] ?: true,
        noAlarmsDuringCalls = this[Keys.NO_ALARMS_DURING_CALLS] ?: true,
        disableAlertsStaleData = this[Keys.DISABLE_ALERTS_STALE] ?: false,
        disableAlertsStaleMinutes = this[Keys.DISABLE_ALERTS_STALE_MINUTES] ?: 15,
        defaultAlertSoundUri = this[Keys.DEFAULT_ALERT_SOUND_URI] ?: "",
        alertProfile = AlertProfile.fromWire(this[Keys.ALERT_PROFILE]),
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
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: false,
        localBroadcastEnabled = this[Keys.LOCAL_BROADCAST] ?: true,
        nightscoutFollowEnabled = this[Keys.NS_FOLLOW_ENABLED] ?: false,
        nightscoutUploadEnabled = this[Keys.NS_UPLOAD_ENABLED] ?: false,
        nightscoutUrl = this[Keys.NS_URL] ?: "",
        nightscoutApiSecret = this[Keys.NS_API_SECRET] ?: "",
        dynamicColorEnabled = this[Keys.DYNAMIC_COLOR] ?: true,
        themeMode = ThemeModeOption.fromWire(this[Keys.THEME_MODE]),
    )

    data class AlertCooldowns(
        val lastMissedMs: Long,
        val lastRapidMs: Long,
        val lastLowPredMs: Long,
        val lastPersistentHighMs: Long,
    )

    object Keys {
        val USE_MGDL = booleanPreferencesKey("use_mgdl")
        val COLLECTOR_ENABLED = booleanPreferencesKey("collector_enabled")
        val SOURCE_UNIT_MODE = stringPreferencesKey("source_unit_mode")
        val JAM_PROTECTION = booleanPreferencesKey("jam_protection")
        val FOREGROUND_SERVICE = booleanPreferencesKey("foreground_service")
        val COMPACT_ONGOING = booleanPreferencesKey("compact_ongoing")
        val CUSTOM_PACKAGES = stringPreferencesKey("custom_packages")
        val STALE_THRESHOLD_MINUTES = intPreferencesKey("stale_threshold_minutes")
        val SENSOR_STARTED_AT_MS = longPreferencesKey("sensor_started_at_ms")
        val SMART_SNOOZING = booleanPreferencesKey("smart_snoozing")
        val SMART_ALERTING = booleanPreferencesKey("smart_alerting")
        val NO_ALARMS_DURING_CALLS = booleanPreferencesKey("no_alarms_during_calls")
        val DISABLE_ALERTS_STALE = booleanPreferencesKey("disable_alerts_stale")
        val DISABLE_ALERTS_STALE_MINUTES = intPreferencesKey("disable_alerts_stale_minutes")
        val DEFAULT_ALERT_SOUND_URI = stringPreferencesKey("default_alert_sound_uri")
        val ALERT_PROFILE = stringPreferencesKey("alert_profile")
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
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LOCAL_BROADCAST = booleanPreferencesKey("local_broadcast")
        val NS_FOLLOW_ENABLED = booleanPreferencesKey("ns_follow_enabled")
        val NS_UPLOAD_ENABLED = booleanPreferencesKey("ns_upload_enabled")
        val NS_URL = stringPreferencesKey("ns_url")
        val NS_API_SECRET = stringPreferencesKey("ns_api_secret")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")

        val LAST_COMPANION_VAL = longPreferencesKey("last_companion_val")
        val LAST_COMPANION_REPEAT = longPreferencesKey("last_companion_repeat")
        val LAST_MISSED_ALERT_MS = longPreferencesKey("last_missed_alert_ms")
        val LAST_RAPID_ALERT_MS = longPreferencesKey("last_rapid_alert_ms")
        val LAST_LOW_PRED_ALERT_MS = longPreferencesKey("last_low_pred_alert_ms")
        val LAST_PERSISTENT_HIGH_ALERT_MS = longPreferencesKey("last_persistent_high_alert_ms")
    }
}
