package dev.xdripyou.app.domain

enum class SourceUnitMode(val wireValue: String, val labelDe: String) {
    AUTO("auto", "Automatisch (aus Text)"),
    MGDL("mgdl", "Immer mg/dL"),
    MMOL("mmol", "Immer mmol/L");

    companion object {
        fun fromWire(value: String?): SourceUnitMode =
            entries.firstOrNull { it.wireValue == value } ?: AUTO
    }
}

enum class AlertProfile(val wireValue: String, val labelDe: String) {
    ASCENDING("ascending", "Ansteigend"),
    HIGH("high", "Sofort Laut"),
    MEDIUM("medium", "Mittel"),
    VIBRATE("vibrate", "Nur Vibration"),
    SILENT("silent", "Lautlos (nur Anzeige)");

    companion object {
        fun fromWire(value: String?): AlertProfile =
            entries.firstOrNull { it.wireValue == value } ?: ASCENDING
    }
}

enum class ThemeModeOption(val wireValue: String, val labelDe: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Hell"),
    DARK("dark", "Dunkel");

    companion object {
        fun fromWire(value: String?): ThemeModeOption =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

data class AlertRule(
    val uuid: String,
    val name: String,
    val isHigh: Boolean,
    val thresholdMgdl: Double,
    val enabled: Boolean,
    val allDay: Boolean = true,
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
    val minutesBetween: Int = 10,
    val defaultSnoozeMinutes: Int = 20,
    val overrideSilent: Boolean = false,
    val vibrate: Boolean = true,
    val soundUri: String? = null,
)

data class ActiveAlertState(
    val ruleUuid: String,
    val ruleName: String,
    val isHigh: Boolean,
    val isSnoozed: Boolean,
    val alertStartedAtMs: Long,
    val lastAlertedAtMs: Long,
    val nextAlertAtMs: Long,
    val triggerMgdl: Double,
    val kind: String = "threshold",
)

data class CollectorEvent(
    val id: Long = 0L,
    val timestampMs: Long,
    val packageName: String,
    val status: String,
    val rawTexts: String,
    val parsedMgdl: Double?,
    val message: String,
)

data class PermissionStatus(
    val notificationListenerEnabled: Boolean = false,
    val postNotificationsGranted: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
    val dndPolicyAccessGranted: Boolean = false,
) {
    val coreReady: Boolean
        get() = notificationListenerEnabled && postNotificationsGranted
}

data class AppSettings(
    val useMgdl: Boolean = true,
    val collectorEnabled: Boolean = true,
    val sourceUnitMode: SourceUnitMode = SourceUnitMode.AUTO,
    val jamProtection: Boolean = true,
    val foregroundServiceEnabled: Boolean = true,
    val customPackages: String = "",
    val smartSnoozing: Boolean = true,
    val smartAlerting: Boolean = true,
    val alertProfile: AlertProfile = AlertProfile.ASCENDING,
    val missedReadingEnabled: Boolean = true,
    val missedReadingMinutes: Int = 20,
    val missedReadingSnoozeMinutes: Int = 20,
    val risingAlertEnabled: Boolean = false,
    val risingRateMgdlPerMin: Double = 3.0,
    val fallingAlertEnabled: Boolean = false,
    val fallingRateMgdlPerMin: Double = 3.0,
    val lowPredictionEnabled: Boolean = false,
    val lowPredictionThresholdMgdl: Double = 70.0,
    val lowPredictionHorizonMinutes: Int = 25,
    val persistentHighEnabled: Boolean = false,
    val persistentHighThresholdMgdl: Double = 180.0,
    val persistentHighMinutes: Int = 60,
    val alertsDisabledUntilMs: Long = 0L,
    val lowAlertsDisabledUntilMs: Long = 0L,
    val highAlertsDisabledUntilMs: Long = 0L,
    val targetLowMgdl: Double = 70.0,
    val targetHighMgdl: Double = 180.0,
    val predictionCurveEnabled: Boolean = true,
    val localBroadcastEnabled: Boolean = true,
    val nightscoutFollowEnabled: Boolean = false,
    val nightscoutUploadEnabled: Boolean = false,
    val nightscoutUrl: String = "",
    val nightscoutApiSecret: String = "",
    val dynamicColorEnabled: Boolean = true,
    val themeMode: ThemeModeOption = ThemeModeOption.SYSTEM,
)
