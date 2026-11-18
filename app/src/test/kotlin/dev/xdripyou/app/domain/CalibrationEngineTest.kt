package dev.xdripyou.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationEngineTest {

    @Test
    fun singlePointCalibrationComputesInterceptAndUnitSlope() {
        val cals = listOf(
            CalibrationRecord(
                timestampMs = 1_000_000L,
                bgMgdl = 135.0,
                rawValue = 120.0,
                slope = 1.0,
                intercept = 0.0,
            )
        )
        val si = CalibrationEngine.calculateWls(cals)
        assertEquals(1.0, si.slope, 1e-4)
        assertEquals(15.0, si.intercept, 1e-4)
        assertEquals(135.0, si.rawToBg(120.0), 1e-4)
        assertTrue(si.isSinglePoint)
    }

    @Test
    fun multiPointWlsRecoversLinearSlopeAndIntercept() {
        // y = 1.2 * x - 10
        val cals = listOf(
            CalibrationRecord(timestampMs = 1_000_000L, bgMgdl = 110.0, rawValue = 100.0, slope = 1.0, intercept = 0.0),
            CalibrationRecord(timestampMs = 4_600_000L, bgMgdl = 170.0, rawValue = 150.0, slope = 1.0, intercept = 0.0),
            CalibrationRecord(timestampMs = 8_200_000L, bgMgdl = 230.0, rawValue = 200.0, slope = 1.0, intercept = 0.0),
        )
        val si = CalibrationEngine.calculateWls(cals)
        assertEquals(1.2, si.slope, 1e-3)
        assertEquals(-10.0, si.intercept, 0.2)
        assertNotNull(si.mardPercent)
        assertTrue(si.mardPercent!! < 1.0)
    }

    @Test
    fun outlierSlopeIsClampedWithinBounds() {
        val cals = listOf(
            CalibrationRecord(timestampMs = 1_000_000L, bgMgdl = 80.0, rawValue = 100.0, slope = 1.0, intercept = 0.0),
            CalibrationRecord(timestampMs = 2_000_000L, bgMgdl = 300.0, rawValue = 110.0, slope = 1.0, intercept = 0.0),
        )
        val si = CalibrationEngine.calculateWls(cals)
        assertEquals(CalibrationEngine.MAX_SLOPE, si.slope, 1e-4)
    }

    @Test
    fun noiseSmoothingReducesSpikeAmplitude() {
        val now = 10_000_000L
        val readings = listOf(
            GlucoseReading(timestampMs = now, mgdl = 120.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "test"),
            GlucoseReading(timestampMs = now + 300_000L, mgdl = 122.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "test"),
            GlucoseReading(timestampMs = now + 600_000L, mgdl = 190.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "test"),
            GlucoseReading(timestampMs = now + 900_000L, mgdl = 121.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "test"),
            GlucoseReading(timestampMs = now + 1_200_000L, mgdl = 123.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "test"),
        )
        val smoothed = CalibrationEngine.smoothReadings(readings)
        assertTrue(smoothed[2].mgdl < 165.0)
        assertTrue(CalibrationEngine.estimateNoiseLevel(readings.reversed()) > 0.5)
    }
}
