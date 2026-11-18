package dev.xdripyou.app

import dev.xdripyou.app.data.BackupManager
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseStats
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Test

class GlucoseStatsTest {

    @Test
    fun `formats mgdl and mmol correctly`() {
        assertEquals("118", GlucoseUnit.formatValue(118.0, useMgdl = true))
        assertEquals("6.5", GlucoseUnit.formatValue(118.0, useMgdl = false))
        assertEquals("HIGH", GlucoseUnit.formatValue(405.0, useMgdl = true))
        assertEquals("LOW", GlucoseUnit.formatValue(35.0, useMgdl = false))
    }

    @Test
    fun `maps slope thresholds to xdrip trend arrows`() {
        assertEquals(TrendArrow.DOUBLE_UP, TrendArrow.fromSlopePerMin(3.8))
        assertEquals(TrendArrow.SINGLE_UP, TrendArrow.fromSlopePerMin(2.5))
        assertEquals(TrendArrow.FORTY_FIVE_UP, TrendArrow.fromSlopePerMin(1.4))
        assertEquals(TrendArrow.FLAT, TrendArrow.fromSlopePerMin(0.2))
        assertEquals(TrendArrow.FORTY_FIVE_DOWN, TrendArrow.fromSlopePerMin(-1.5))
        assertEquals(TrendArrow.SINGLE_DOWN, TrendArrow.fromSlopePerMin(-2.8))
        assertEquals(TrendArrow.DOUBLE_DOWN, TrendArrow.fromSlopePerMin(-4.2))
    }

    @Test
    fun `computes time in range and gmi accurately`() {
        val readings = listOf(
            GlucoseReading(timestampMs = 1L, mgdl = 50.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "Test"),
            GlucoseReading(timestampMs = 2L, mgdl = 100.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "Test"),
            GlucoseReading(timestampMs = 3L, mgdl = 140.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "Test"),
            GlucoseReading(timestampMs = 4L, mgdl = 210.0, slopePerMin = 0.0, trend = TrendArrow.FLAT, source = "Test"),
        )
        val stats = GlucoseStats.compute(readings)
        assertEquals(4, stats.count)
        assertEquals(25.0, stats.veryLowPercent, 0.01)
        assertEquals(50.0, stats.inRangePercent, 0.01)
        assertEquals(25.0, stats.highPercent, 0.01)
        assertEquals(125.0, stats.meanMgdl, 0.01)
        assertEquals(3.31 + 0.02392 * 125.0, stats.gmiPercent, 0.01)
    }

    @Test
    fun `encodes readings to csv sorted descending`() {
        val readings = listOf(
            GlucoseReading(timestampMs = 1_700_000_000_000L, mgdl = 100.0, slopePerMin = 0.5, trend = TrendArrow.FLAT, source = "CamAPS FX"),
            GlucoseReading(timestampMs = 1_700_000_300_000L, mgdl = 118.0, slopePerMin = 1.2, trend = TrendArrow.FORTY_FIVE_UP, source = "CamAPS FX"),
        )
        val csv = BackupManager.encodeReadingsToCsv(readings)
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertEquals("timestamp_iso,timestamp_ms,mgdl,mmol,trend,slope_mgdl_per_min,source", lines[0])
        assertEquals(true, lines[1].contains(",118,6.5,FortyFiveUp,1.20,CamAPS FX"))
    }
}
