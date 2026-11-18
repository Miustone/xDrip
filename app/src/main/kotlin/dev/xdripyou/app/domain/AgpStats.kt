package dev.xdripyou.app.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class StatsPeriod(val label: String, val days: Int) {
    DAY_1("24 Std.", 1),
    DAYS_7("7 Tage", 7),
    DAYS_14("14 Tage", 14),
    DAYS_30("30 Tage", 30),
    DAYS_90("90 Tage", 90),
}

data class AgpBucket(
    val slotIndex: Int, // 0..47 (30-min buckets starting at 00:00)
    val q10: Double,
    val q25: Double,
    val q50: Double,
    val q75: Double,
    val q90: Double,
    val count: Int,
)

data class ExtendedGlucoseStats(
    val period: StatsPeriod = StatsPeriod.DAY_1,
    val stats: GlucoseStats = GlucoseStats(),
    val dataCapturePercent: Double = 0.0,
    val gvi: Double = 0.0,
    val pgs: Double = 0.0,
    val a1cDcctPercent: Double = 0.0,
    val a1cIfccMmolMol: Int = 0,
    val agpBuckets: List<AgpBucket> = emptyList(),
)

/**
 * Port of xDrip's `PercentileView` & `StatsActivity` multi-day TIR + Ambulatory Glucose Profile (AGP).
 */
object AgpStatsCalculator {

    fun computeAgpBuckets(
        readings: List<GlucoseReading>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<AgpBucket> {
        if (readings.isEmpty()) return emptyList()
        val slots = Array(48) { mutableListOf<Double>() }
        for (r in readings) {
            val zdt = Instant.ofEpochMilli(r.timestampMs).atZone(zoneId)
            val minuteOfDay = zdt.hour * 60 + zdt.minute
            val slot = (minuteOfDay / 30).coerceIn(0, 47)
            slots[slot].add(r.mgdl)
        }

        val result = mutableListOf<AgpBucket>()
        for (slot in 0 until 48) {
            val values = slots[slot]
            if (values.isEmpty()) continue
            values.sort()
            result.add(
                AgpBucket(
                    slotIndex = slot,
                    q10 = percentile(values, 0.10),
                    q25 = percentile(values, 0.25),
                    q50 = percentile(values, 0.50),
                    q75 = percentile(values, 0.75),
                    q90 = percentile(values, 0.90),
                    count = values.size,
                )
            )
        }
        return result
    }

    fun computeExtendedStats(
        readings: List<GlucoseReading>,
        period: StatsPeriod,
        targetLowMgdl: Double,
        targetHighMgdl: Double,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ExtendedGlucoseStats {
        val base = GlucoseStats.compute(
            readings = readings,
            targetLowMgdl = targetLowMgdl,
            targetHighMgdl = targetHighMgdl,
        )
        // Standard CGM cadence is 1 reading every 5 minutes = 288 readings/day.
        val expectedReadings = period.days * 288.0
        val capturePct = if (expectedReadings > 0.0 && readings.isNotEmpty()) {
            min(100.0, (readings.size / expectedReadings) * 100.0)
        } else {
            0.0
        }

        // Exact xDrip StatsResult.java GVI, PGS, A1c DCCT & IFCC formulas
        var gvi = 0.0
        var pgs = 0.0
        var a1cDcct = 0.0
        var a1cIfcc = 0
        if (readings.isNotEmpty() && base.meanMgdl > 0.0) {
            val dcctRaw = (base.meanMgdl + 46.7) / 28.7
            a1cDcct = (dcctRaw * 10.0).roundToInt() / 10.0
            a1cIfcc = ((dcctRaw - 2.15) * 10.929).roundToInt()

            val chronological = readings.sortedBy { it.timestampMs }
            if (chronological.size >= 2) {
                var pathLen = 0.0
                for (i in 1 until chronological.size) {
                    val dBg = chronological[i].mgdl - chronological[i - 1].mgdl
                    pathLen += sqrt(dBg * dBg + 25.0)
                }
                val totalDelta = abs(chronological.last().mgdl - chronological.first().mgdl)
                val idealLen = sqrt((chronological.size * 5.0) * (chronological.size * 5.0) + totalDelta * totalDelta)
                if (idealLen > 0.0) {
                    gvi = ((pathLen / idealLen) * 100.0).roundToInt() / 100.0
                    pgs = ((gvi * base.meanMgdl * (1.0 - (base.inRangePercent / 100.0))) * 100.0).roundToInt() / 100.0
                }
            }
        }

        return ExtendedGlucoseStats(
            period = period,
            stats = base,
            dataCapturePercent = capturePct,
            gvi = gvi,
            pgs = pgs,
            a1cDcctPercent = a1cDcct,
            a1cIfccMmolMol = a1cIfcc,
            agpBuckets = computeAgpBuckets(readings, zoneId),
        )
    }

    private fun percentile(sortedValues: List<Double>, p: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        if (sortedValues.size == 1) return sortedValues.first()
        val idx = (p * (sortedValues.size - 1)).coerceIn(0.0, (sortedValues.size - 1).toDouble())
        val low = idx.toInt()
        val high = (low + 1).coerceAtMost(sortedValues.lastIndex)
        val frac = idx - low
        return sortedValues[low] + frac * (sortedValues[high] - sortedValues[low])
    }
}
