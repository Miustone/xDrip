package dev.xdripyou.app

import dev.xdripyou.app.domain.AlertEvaluator
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.AppSettings
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlertEvaluatorTest {

    private val defaultRules = listOf(
        AlertRule(uuid = "urgent-low", name = "Dringend Niedrig", isHigh = false, thresholdMgdl = 55.0, enabled = true),
        AlertRule(uuid = "low", name = "Niedrig", isHigh = false, thresholdMgdl = 70.0, enabled = true),
        AlertRule(uuid = "high", name = "Hoch", isHigh = true, thresholdMgdl = 180.0, enabled = true),
        AlertRule(uuid = "urgent-high", name = "Sehr Hoch", isHigh = true, thresholdMgdl = 250.0, enabled = true),
    )

    @Test
    fun `selects lowest urgent low alert first`() {
        val now = 1_700_000_000_000L
        val match = AlertEvaluator.findHighestActiveAlert(
            mgdl = 52.0,
            rules = defaultRules,
            settings = AppSettings(),
            nowMs = now,
        )
        assertEquals("urgent-low", match?.uuid)
    }

    @Test
    fun `selects highest urgent high alert first`() {
        val now = 1_700_000_000_000L
        val match = AlertEvaluator.findHighestActiveAlert(
            mgdl = 265.0,
            rules = defaultRules,
            settings = AppSettings(),
            nowMs = now,
        )
        assertEquals("urgent-high", match?.uuid)
    }

    @Test
    fun `respects global and scoped silence timestamps`() {
        val now = 1_700_000_000_000L
        val globalSilenced = AppSettings(alertsDisabledUntilMs = now + 60_000L)
        assertNull(AlertEvaluator.findHighestActiveAlert(50.0, defaultRules, globalSilenced, now))

        val lowSilenced = AppSettings(lowAlertsDisabledUntilMs = now + 60_000L)
        assertNull(AlertEvaluator.findHighestActiveAlert(50.0, defaultRules, lowSilenced, now))
        assertEquals("high", AlertEvaluator.findHighestActiveAlert(195.0, defaultRules, lowSilenced, now)?.uuid)
    }

    @Test
    fun `handles overnight timeframe wraparound`() {
        val nightRule = AlertRule(
            uuid = "night",
            name = "Nacht",
            isHigh = false,
            thresholdMgdl = 75.0,
            enabled = true,
            allDay = false,
            startMinutes = 22 * 60,
            endMinutes = 7 * 60,
        )
        val cal23 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }
        val cal14 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(AlertEvaluator.isInTimeFrame(nightRule, cal23.timeInMillis))
        assertFalse(AlertEvaluator.isInTimeFrame(nightRule, cal14.timeInMillis))
    }

    @Test
    fun `detects trending to alert end for low and high`() {
        val now = 1_700_000_000_000L
        val recoveringLow = listOf(
            GlucoseReading(timestampMs = now, mgdl = 66.0, slopePerMin = 2.0, trend = TrendArrow.FORTY_FIVE_UP, source = "Test"),
            GlucoseReading(timestampMs = now - 300_000L, mgdl = 60.0, slopePerMin = 1.0, trend = TrendArrow.FLAT, source = "Test"),
            GlucoseReading(timestampMs = now - 600_000L, mgdl = 54.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "Test"),
        )
        assertTrue(AlertEvaluator.isTrendingToAlertEnd(recoveringLow, isHighAlert = false, nowMs = now))
        assertFalse(AlertEvaluator.isTrendingToAlertEnd(recoveringLow, isHighAlert = true, nowMs = now))
    }

    @Test
    fun `detects rapid drop alert`() {
        val now = 1_700_000_000_000L
        val rapidDrop = listOf(
            GlucoseReading(timestampMs = now, mgdl = 110.0, slopePerMin = -4.0, trend = TrendArrow.DOUBLE_DOWN, source = "Test"),
            GlucoseReading(timestampMs = now - 300_000L, mgdl = 130.0, slopePerMin = -3.0, trend = TrendArrow.SINGLE_DOWN, source = "Test"),
            GlucoseReading(timestampMs = now - 600_000L, mgdl = 148.0, slopePerMin = -2.0, trend = TrendArrow.FORTY_FIVE_DOWN, source = "Test"),
            GlucoseReading(timestampMs = now - 900_000L, mgdl = 165.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "Test"),
        )
        assertTrue(AlertEvaluator.isRapidRateTriggered(rapidDrop, rateMgdlPerMin = 3.0, drop = true, nowMs = now))
        assertFalse(AlertEvaluator.isRapidRateTriggered(rapidDrop, rateMgdlPerMin = 3.0, drop = false, nowMs = now))
    }

    @Test
    fun `detects persistent high only when continuously above threshold`() {
        val now = 1_700_000_000_000L
        val continuousHigh = (0..12).map { idx ->
            GlucoseReading(
                timestampMs = now - idx * 300_000L,
                mgdl = 205.0,
                slopePerMin = 0.0,
                trend = TrendArrow.FLAT,
                source = "Test",
            )
        }
        assertTrue(
            AlertEvaluator.isPersistentHighTriggered(
                readingsDesc = continuousHigh,
                thresholdMgdl = 180.0,
                durationMinutes = 60,
                nowMs = now,
            )
        )

        val interrupted = continuousHigh.toMutableList().apply {
            this[5] = this[5].copy(mgdl = 165.0)
        }
        assertFalse(
            AlertEvaluator.isPersistentHighTriggered(
                readingsDesc = interrupted,
                thresholdMgdl = 180.0,
                durationMinutes = 60,
                nowMs = now,
            )
        )
    }
}
