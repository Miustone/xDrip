package dev.xdripyou.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.xdripyou.app.domain.ActiveAlertState
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.CollectorEvent
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow

@Entity(
    tableName = "bg_readings",
    indices = [Index(value = ["timestampMs"], unique = true)],
)
data class BgReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val mgdl: Double,
    val slopePerMin: Double,
    val trendName: String,
    val source: String,
    val rawText: String?,
    val syncedNightscout: Boolean = false,
) {
    fun toDomain(): GlucoseReading = GlucoseReading(
        id = id,
        timestampMs = timestampMs,
        mgdl = mgdl,
        slopePerMin = slopePerMin,
        trend = TrendArrow.fromWireName(trendName),
        source = source,
        rawText = rawText,
        syncedNightscout = syncedNightscout,
    )
}

@Entity(
    tableName = "treatments",
    indices = [Index(value = ["timestampMs"])],
)
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val insulinUnits: Double,
    val carbsGrams: Double,
    val bloodGlucoseMgdl: Double?,
    val note: String,
    val eventType: String,
    val syncedNightscout: Boolean = false,
) {
    fun toDomain(): TreatmentEntry = TreatmentEntry(
        id = id,
        timestampMs = timestampMs,
        insulinUnits = insulinUnits,
        carbsGrams = carbsGrams,
        bloodGlucoseMgdl = bloodGlucoseMgdl,
        note = note,
        eventType = eventType,
        syncedNightscout = syncedNightscout,
    )

    companion object {
        fun fromDomain(entry: TreatmentEntry): TreatmentEntity = TreatmentEntity(
            id = entry.id,
            timestampMs = entry.timestampMs,
            insulinUnits = entry.insulinUnits,
            carbsGrams = entry.carbsGrams,
            bloodGlucoseMgdl = entry.bloodGlucoseMgdl,
            note = entry.note,
            eventType = entry.eventType,
            syncedNightscout = entry.syncedNightscout,
        )
    }
}

@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val isHigh: Boolean,
    val thresholdMgdl: Double,
    val enabled: Boolean,
    val allDay: Boolean,
    val startMinutes: Int,
    val endMinutes: Int,
    val minutesBetween: Int,
    val defaultSnoozeMinutes: Int,
    val overrideSilent: Boolean,
    val vibrate: Boolean,
    val soundUri: String?,
) {
    fun toDomain(): AlertRule = AlertRule(
        uuid = uuid,
        name = name,
        isHigh = isHigh,
        thresholdMgdl = thresholdMgdl,
        enabled = enabled,
        allDay = allDay,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        minutesBetween = minutesBetween,
        defaultSnoozeMinutes = defaultSnoozeMinutes,
        overrideSilent = overrideSilent,
        vibrate = vibrate,
        soundUri = soundUri,
    )

    companion object {
        fun fromDomain(rule: AlertRule): AlertRuleEntity = AlertRuleEntity(
            uuid = rule.uuid,
            name = rule.name,
            isHigh = rule.isHigh,
            thresholdMgdl = rule.thresholdMgdl,
            enabled = rule.enabled,
            allDay = rule.allDay,
            startMinutes = rule.startMinutes,
            endMinutes = rule.endMinutes,
            minutesBetween = rule.minutesBetween,
            defaultSnoozeMinutes = rule.defaultSnoozeMinutes,
            overrideSilent = rule.overrideSilent,
            vibrate = rule.vibrate,
            soundUri = rule.soundUri,
        )
    }
}

@Entity(tableName = "active_alert")
data class ActiveAlertEntity(
    @PrimaryKey val id: Int = 1,
    val ruleUuid: String,
    val ruleName: String,
    val isHigh: Boolean,
    val isSnoozed: Boolean,
    val alertStartedAtMs: Long,
    val lastAlertedAtMs: Long,
    val nextAlertAtMs: Long,
    val triggerMgdl: Double,
    val kind: String,
) {
    fun toDomain(): ActiveAlertState = ActiveAlertState(
        ruleUuid = ruleUuid,
        ruleName = ruleName,
        isHigh = isHigh,
        isSnoozed = isSnoozed,
        alertStartedAtMs = alertStartedAtMs,
        lastAlertedAtMs = lastAlertedAtMs,
        nextAlertAtMs = nextAlertAtMs,
        triggerMgdl = triggerMgdl,
        kind = kind,
    )

    companion object {
        fun fromDomain(state: ActiveAlertState): ActiveAlertEntity = ActiveAlertEntity(
            id = 1,
            ruleUuid = state.ruleUuid,
            ruleName = state.ruleName,
            isHigh = state.isHigh,
            isSnoozed = state.isSnoozed,
            alertStartedAtMs = state.alertStartedAtMs,
            lastAlertedAtMs = state.lastAlertedAtMs,
            nextAlertAtMs = state.nextAlertAtMs,
            triggerMgdl = state.triggerMgdl,
            kind = state.kind,
        )
    }
}

@Entity(tableName = "collector_events")
data class CollectorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val packageName: String,
    val status: String,
    val rawTexts: String,
    val parsedMgdl: Double?,
    val message: String,
) {
    fun toDomain(): CollectorEvent = CollectorEvent(
        id = id,
        timestampMs = timestampMs,
        packageName = packageName,
        status = status,
        rawTexts = rawTexts,
        parsedMgdl = parsedMgdl,
        message = message,
    )
}
