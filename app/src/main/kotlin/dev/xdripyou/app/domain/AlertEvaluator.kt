package dev.xdripyou.app.domain

import java.util.Calendar

/**
 * Pure Kotlin alert evaluation mirroring xDrip's `AlertType`, `ActiveBgAlert`,
 * and `BgReading` alarm logic.
 */
object AlertEvaluator {

    fun isInTimeFrame(rule: AlertRule, nowMs: Long): Boolean {
        if (rule.allDay) return true
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (rule.startMinutes < rule.endMinutes) {
            mins in rule.startMinutes..rule.endMinutes
        } else {
            mins >= rule.startMinutes || mins <= rule.endMinutes
        }
    }

    fun findHighestActiveAlert(
        mgdl: Double,
        rules: List<AlertRule>,
        settings: AppSettings,
        nowMs: Long,
    ): AlertRule? {
        if (settings.alertsDisabledUntilMs > nowMs || mgdl <= 14.0) {
            return null
        }
        val eligible = rules.filter { it.enabled && isInTimeFrame(it, nowMs) }

        // Low alerts take precedence over high alerts (lowest threshold first)
        if (settings.lowAlertsDisabledUntilMs <= nowMs) {
            val lowMatch = eligible
                .filter { !it.isHigh && mgdl <= it.thresholdMgdl }
                .minByOrNull { it.thresholdMgdl }
            if (lowMatch != null) return lowMatch
        }

        // High alerts checked descending (highest threshold first)
        if (settings.highAlertsDisabledUntilMs <= nowMs) {
            val highMatch = eligible
                .filter { it.isHigh && mgdl >= it.thresholdMgdl }
                .maxByOrNull { it.thresholdMgdl }
            if (highMatch != null) return highMatch
        }
        return null
    }

    fun isHigherPriority(candidate: AlertRule, current: AlertRule): Boolean {
        if (!candidate.isHigh && current.isHigh) return true
        if (candidate.isHigh && !current.isHigh) return false
        return if (candidate.isHigh) {
            candidate.thresholdMgdl > current.thresholdMgdl
        } else {
            candidate.thresholdMgdl < current.thresholdMgdl
        }
    }

    /** Mirrors xDrip's `BgReading.trendingToAlertEnd` (expects newest-first readings). */
    fun isTrendingToAlertEnd(
        recentDesc: List<GlucoseReading>,
        isHighAlert: Boolean,
        nowMs: Long,
    ): Boolean {
        if (recentDesc.size < 3) return false
        val p0 = recentDesc[0]
        val p1 = recentDesc[1]
        val p2 = recentDesc[2]
        if (nowMs - p2.timestampMs > 900_000L) return false
        return if (isHighAlert) {
            (p1.mgdl - p0.mgdl) > 1.0 || (p2.mgdl - p0.mgdl) > 2.0
        } else {
            (p0.mgdl - p1.mgdl) > 4.0 || (p0.mgdl - p2.mgdl) > 10.0
        }
    }

    /** Mirrors xDrip's `BgReading.checkForDropRiseAllert` (expects newest-first readings). */
    fun isRapidRateTriggered(
        recentDesc: List<GlucoseReading>,
        rateMgdlPerMin: Double,
        drop: Boolean,
        nowMs: Long,
    ): Boolean {
        if (recentDesc.size < 4) return false
        val p0 = recentDesc[0]
        val p1 = recentDesc[1]
        val p3 = recentDesc[3]
        if (nowMs - p3.timestampMs > 1_200_000L) return false

        val time3 = (p0.timestampMs - p3.timestampMs) / 60_000.0
        if (time3 <= 0.0) return false
        var diff3 = p3.mgdl - p0.mgdl
        if (!drop) diff3 *= -1.0
        if (diff3 < time3 * rateMgdlPerMin) return false

        val time1 = (p0.timestampMs - p1.timestampMs) / 60_000.0
        if (time1 > 7.0 || time1 <= 0.0) return true
        var diff1 = p1.mgdl - p0.mgdl
        if (!drop) diff1 *= -1.0
        return diff1 >= (time1 * rateMgdlPerMin) / 2.0
    }
}
