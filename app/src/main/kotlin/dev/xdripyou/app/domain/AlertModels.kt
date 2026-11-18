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
    val canScheduleExactAlarms: Boolean = true,
) {
    val coreReady: Boolean
        get() = notificationListenerEnabled && postNotificationsGranted

    val allRecommendedGranted: Boolean
        get() = coreReady && ignoringBatteryOptimizations && dndPolicyAccessGranted && canScheduleExactAlarms
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val useMgdl: Boolean = true,
    val collectorEnabled: Boolean = true,
    val includeAllCompanionApps: Boolean = false,
    val sourceUnitMode: SourceUnitMode = SourceUnitMode.AUTO,
    val jamProtection: Boolean = true,
    val foregroundServiceEnabled: Boolean = true,
    val compactOngoingNotification: Boolean = false,
    val customPackages: String = "",
    val staleThresholdMinutes: Int = 11,
    val sensorStartedAtMs: Long = 0L,
    val sensorExpectedDays: Int = 14,
    val sensorExpiryAlertEnabled: Boolean = false,
    val smartSnoozing: Boolean = true,
    val smartAlerting: Boolean = true,
    val delayAscending3Min: Boolean = true,
    val ascendingToMedium: Boolean = false,
    val startAlertsSnoozed: Boolean = false,
    val showButtonsInAlerts: Boolean = true,
    val wakePhoneDuringAlerts: Boolean = false,
    val flashTorchOnAlert: Boolean = false,
    val noAlarmsDuringCalls: Boolean = true,
    val disableAlertsStaleData: Boolean = false,
    val disableAlertsStaleMinutes: Int = 15,
    val defaultAlertSoundUri: String = "",
    val alertProfile: AlertProfile = AlertProfile.ASCENDING,
    val speakReadingsEnabled: Boolean = false,
    val speakAlertsEnabled: Boolean = false,
    val speakTrendEnabled: Boolean = true,
    val speechSpeed: Double = 1.0,
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
    val highPredictionEnabled: Boolean = false,
    val highPredictionThresholdMgdl: Double = 200.0,
    val highPredictionHorizonMinutes: Int = 25,
    val persistentHighEnabled: Boolean = false,
    val persistentHighThresholdMgdl: Double = 180.0,
    val persistentHighMinutes: Int = 60,
    val alertsDisabledUntilMs: Long = 0L,
    val lowAlertsDisabledUntilMs: Long = 0L,
    val highAlertsDisabledUntilMs: Long = 0L,
    val targetLowMgdl: Double = 70.0,
    val targetHighMgdl: Double = 180.0,
    val predictionCurveEnabled: Boolean = true,
    val showChartTreatments: Boolean = true,
    val chartPointsOnly: Boolean = false,
    val showChartGrid: Boolean = true,
    val showChartAverageLine: Boolean = false,
    val chartMaxMgdl: Int = 0, // 0 = auto
    val showExtraStatusLine: Boolean = true,
    val keepScreenOn: Boolean = false,
    val localBroadcastEnabled: Boolean = true,
    val broadcastTargetPackage: String = "",
    val nightscoutFollowEnabled: Boolean = false,
    val nightscoutUploadEnabled: Boolean = false,
    val nightscoutUploadTreatments: Boolean = true,
    val nightscoutWifiOnly: Boolean = false,
    val nightscoutUrl: String = "",
    val nightscoutApiSecret: String = "",
    val retentionDays: Int = 90,
    val showIobCob: Boolean = true,
    val insulinDurationMinutes: Int = 270,
    val insulinPeakMinutes: Int = 65,
    val carbDelayMinutes: Int = 15,
    val carbAbsorptionGramsPerHour: Int = 30,
    val localWebServiceEnabled: Boolean = false,
    val localWebServiceOpenNetwork: Boolean = false,
    val localWebServiceSecret: String = "",
    val acceptLocalBroadcastReadings: Boolean = false,
    val broadcastSnoozeEnabled: Boolean = false,
    val acceptBroadcastSnooze: Boolean = false,
    val dexcomShareEnabled: Boolean = false,
    val dexcomShareUsAccount: Boolean = false,
    val dexcomShareUsername: String = "",
    val dexcomSharePassword: String = "",
    val libreLinkEnabled: Boolean = false,
    val libreLinkRegion: String = "eu",
    val libreLinkEmail: String = "",
    val libreLinkPassword: String = "",
    val carbRatioGramsPerUnit: Double = 10.0,
    val isfMgdlPerUnit: Double = 40.0,
    val bolusTargetMgdl: Double = 110.0,
    val showBasalRate: Boolean = false,
    val basalScheduleWire: String = "",
    val nightscoutDeviceStatusEnabled: Boolean = true,
    val healthConnectEnabled: Boolean = false,
    val healthConnectSend: Boolean = true,
    val healthConnectReceive: Boolean = false,
    val influxEnabled: Boolean = false,
    val influxUrl: String = "",
    val influxDatabase: String = "",
    val influxOrg: String = "",
    val influxUsername: String = "",
    val influxTokenOrPassword: String = "",
    val tidepoolEnabled: Boolean = false,
    val tidepoolDevServers: Boolean = false,
    val tidepoolEmail: String = "",
    val tidepoolPassword: String = "",
    val peerSyncEnabled: Boolean = false,
    val peerSyncHost: String = "",
    val peerSyncSecret: String = "",
    val dynamicColorEnabled: Boolean = true,
    val themeMode: ThemeModeOption = ThemeModeOption.SYSTEM,
    val insulinPreset: String = "novorapid",
    val snoozeOnDismissEnabled: Boolean = false,
    val snoozeOnDismissMinutes: Int = 20,
    val watchStatusBroadcastEnabled: Boolean = false,
    val floatingOverlayEnabled: Boolean = false,
    val smoothChartNoise: Boolean = false,
    val showChartIobCurve: Boolean = true,
    val careLinkEnabled: Boolean = false,
    val careLinkRegion: String = "eu",
    val careLinkUsername: String = "",
    val careLinkPassword: String = "",
    val webFollowEnabled: Boolean = false,
    val webFollowUrl: String = "",
    val webFollowJsonPath: String = "sgv",
    val bleDeviceType: String = "none",
    val bleDeviceAddress: String = "",
    val bleDeviceName: String = "",
    val nfcLibreEnabled: Boolean = true,
    val calibrationAlgorithm: String = "xdrip_wls",
)

data class ReminderItem(
    val id: Long = 0L,
    val title: String,
    val intervalHours: Int,
    val nextDueMs: Long,
    val enabled: Boolean = true,
    val snoozedMinutes: Int = 60,
    val lastTriggeredMs: Long = 0L,
)
