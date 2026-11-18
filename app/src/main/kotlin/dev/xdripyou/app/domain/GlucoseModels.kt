package dev.xdripyou.app.domain

import kotlin.math.sqrt

enum class ChartWindow(val label: String, val durationMs: Long) {
    THREE_HOURS("3 Std.", 3 * 3_600_000L),
    SIX_HOURS("6 Std.", 6 * 3_600_000L),
    TWELVE_HOURS("12 Std.", 12 * 3_600_000L),
    TWENTY_FOUR_HOURS("24 Std.", 24 * 3_600_000L),
}

data class GlucoseReading(
    val id: Long = 0L,
    val timestampMs: Long,
    val mgdl: Double,
    val slopePerMin: Double,
    val trend: TrendArrow,
    val source: String,
    val rawText: String? = null,
) {
    /** Matches xDrip's `Home.stale_data_millis()` default (11 minutes = 660,000 ms). */
    fun isStaleAt(nowMs: Long): Boolean = (nowMs - timestampMs) > STALE_DATA_MS

    fun minutesAgoAt(nowMs: Long): Long = ((nowMs - timestampMs) / 60_000L).coerceAtLeast(0L)

    companion object {
        const val STALE_DATA_MS = 660_000L
    }
}

data class GlucoseStats(
    val count: Int = 0,
    val meanMgdl: Double = 0.0,
    val sdMgdl: Double = 0.0,
    val cvPercent: Double = 0.0,
    val gmiPercent: Double = 0.0,
    val veryLowPercent: Double = 0.0,
    val lowPercent: Double = 0.0,
    val inRangePercent: Double = 0.0,
    val highPercent: Double = 0.0,
    val veryHighPercent: Double = 0.0,
    val minMgdl: Double = 0.0,
    val maxMgdl: Double = 0.0,
) {
    companion object {
        fun compute(
            readings: List<GlucoseReading>,
            targetLowMgdl: Double = 70.0,
            targetHighMgdl: Double = 180.0,
            urgentLowMgdl: Double = 54.0,
            urgentHighMgdl: Double = 250.0,
        ): GlucoseStats {
            if (readings.isEmpty()) return GlucoseStats()
            val n = readings.size
            var sum = 0.0
            var minVal = readings.first().mgdl
            var maxVal = readings.first().mgdl
            var veryLow = 0
            var low = 0
            var inRange = 0
            var high = 0
            var veryHigh = 0

            for (r in readings) {
                val v = r.mgdl
                sum += v
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
                when {
                    v < urgentLowMgdl -> veryLow++
                    v < targetLowMgdl -> low++
                    v <= targetHighMgdl -> inRange++
                    v <= urgentHighMgdl -> high++
                    else -> veryHigh++
                }
            }

            val mean = sum / n
            var sqSum = 0.0
            for (r in readings) {
                val d = r.mgdl - mean
                sqSum += d * d
            }
            val sd = sqrt(sqSum / n)
            val cv = if (mean > 0.0) (sd / mean) * 100.0 else 0.0
            val gmi = 3.31 + 0.02392 * mean

            return GlucoseStats(
                count = n,
                meanMgdl = mean,
                sdMgdl = sd,
                cvPercent = cv,
                gmiPercent = gmi,
                veryLowPercent = (veryLow.toDouble() / n) * 100.0,
                lowPercent = (low.toDouble() / n) * 100.0,
                inRangePercent = (inRange.toDouble() / n) * 100.0,
                highPercent = (high.toDouble() / n) * 100.0,
                veryHighPercent = (veryHigh.toDouble() / n) * 100.0,
                minMgdl = minVal,
                maxMgdl = maxVal,
            )
        }
    }
}
