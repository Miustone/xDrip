package dev.xdripyou.app

import dev.xdripyou.app.domain.AgpStatsCalculator
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.StatsPeriod
import dev.xdripyou.app.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AgpStatsTest {

    @Test
    fun `computes agp percentile buckets in half-hour slots`() {
        val zone = ZoneOffset.UTC
        val base = ZonedDateTime.of(2026, 11, 10, 8, 10, 0, 0, zone).toInstant().toEpochMilli()
        val values = listOf(80.0, 100.0, 120.0, 140.0, 160.0)
        val readings = values.mapIndexed { idx, v ->
            GlucoseReading(
                timestampMs = base + idx * 86_400_000L,
                mgdl = v,
                slopePerMin = 0.0,
                trend = TrendArrow.FLAT,
                source = "Test",
            )
        }

        val buckets = AgpStatsCalculator.computeAgpBuckets(readings, zone)
        assertEquals(1, buckets.size)
        val bucket = buckets.first()
        assertEquals(16, bucket.slotIndex) // 08:00-08:30 -> slot 16
        assertEquals(5, bucket.count)
        assertEquals(120.0, bucket.q50, 1e-4)
        assertTrue(bucket.q10 < bucket.q25 && bucket.q25 < bucket.q50 && bucket.q50 < bucket.q75 && bucket.q75 < bucket.q90)
    }

    @Test
    fun `computes multi-day extended stats and data capture percentage`() {
        val zone = ZoneOffset.UTC
        val base = ZonedDateTime.of(2026, 11, 10, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        // 288 readings = 1 full day out of 7 days => ~14.28% capture
        val readings = (0 until 288).map { idx ->
            GlucoseReading(
                timestampMs = base + idx * 300_000L,
                mgdl = if (idx % 4 == 0) 190.0 else 110.0,
                slopePerMin = 0.0,
                trend = TrendArrow.FLAT,
                source = "Test",
            )
        }
        val ext = AgpStatsCalculator.computeExtendedStats(
            readings = readings,
            period = StatsPeriod.DAYS_7,
            targetLowMgdl = 70.0,
            targetHighMgdl = 180.0,
            zoneId = zone,
        )
        assertEquals(75.0, ext.stats.inRangePercent, 0.1)
        assertEquals(100.0 / 7.0, ext.dataCapturePercent, 0.1)
        assertEquals(48, ext.agpBuckets.size)
    }
}
