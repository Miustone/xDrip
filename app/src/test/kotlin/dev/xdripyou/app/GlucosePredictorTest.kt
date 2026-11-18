package dev.xdripyou.app

import dev.xdripyou.app.domain.GlucosePredictor
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucosePredictorTest {

    @Test
    fun `predicts falling curve and estimates time until low`() {
        val now = 1_700_000_000_000L
        val recentDesc = listOf(
            GlucoseReading(timestampMs = now, mgdl = 100.0, slopePerMin = -2.0, trend = TrendArrow.SINGLE_DOWN, source = "Test"),
            GlucoseReading(timestampMs = now - 300_000L, mgdl = 112.0, slopePerMin = -2.0, trend = TrendArrow.SINGLE_DOWN, source = "Test"),
            GlucoseReading(timestampMs = now - 600_000L, mgdl = 124.0, slopePerMin = -2.0, trend = TrendArrow.SINGLE_DOWN, source = "Test"),
        )
        val predictions = GlucosePredictor.predict(recentDesc, nowMs = now)
        assertEquals(6, predictions.size)
        assertTrue(predictions.last().mgdl < 80.0)

        val minsUntil70 = GlucosePredictor.minutesUntilBelow(
            predictions = predictions,
            baseTimestampMs = now,
            thresholdMgdl = 75.0,
            horizonMinutes = 30,
        )
        assertNotNull(minsUntil70)
        assertTrue(minsUntil70!! in 5..30)
    }

    @Test
    fun `returns empty predictions when latest reading is stale`() {
        val now = 1_700_000_000_000L
        val stale = listOf(
            GlucoseReading(timestampMs = now - 900_000L, mgdl = 100.0, slopePerMin = -2.0, trend = TrendArrow.SINGLE_DOWN, source = "Test"),
            GlucoseReading(timestampMs = now - 1_200_000L, mgdl = 110.0, slopePerMin = -2.0, trend = TrendArrow.SINGLE_DOWN, source = "Test"),
        )
        assertTrue(GlucosePredictor.predict(stale, nowMs = now).isEmpty())
    }

    @Test
    fun `rising curve does not trigger low prediction`() {
        val now = 1_700_000_000_000L
        val rising = listOf(
            GlucoseReading(timestampMs = now, mgdl = 105.0, slopePerMin = 1.5, trend = TrendArrow.FORTY_FIVE_UP, source = "Test"),
            GlucoseReading(timestampMs = now - 300_000L, mgdl = 98.0, slopePerMin = 1.4, trend = TrendArrow.FORTY_FIVE_UP, source = "Test"),
        )
        val preds = GlucosePredictor.predict(rising, nowMs = now)
        assertNull(GlucosePredictor.minutesUntilBelow(preds, now, 70.0, 30))
    }

    @Test
    fun `rising curve triggers high prediction`() {
        val now = 1_700_000_000_000L
        val risingFast = listOf(
            GlucoseReading(timestampMs = now, mgdl = 165.0, slopePerMin = 2.6, trend = TrendArrow.SINGLE_UP, source = "Test"),
            GlucoseReading(timestampMs = now - 300_000L, mgdl = 152.0, slopePerMin = 2.4, trend = TrendArrow.SINGLE_UP, source = "Test"),
            GlucoseReading(timestampMs = now - 600_000L, mgdl = 140.0, slopePerMin = 2.2, trend = TrendArrow.SINGLE_UP, source = "Test"),
        )
        val preds = GlucosePredictor.predict(risingFast, nowMs = now)
        val mins = GlucosePredictor.minutesUntilAbove(preds, now, 195.0, 30)
        assertNotNull(mins)
        assertTrue(mins!! in 5..30)
    }
}
