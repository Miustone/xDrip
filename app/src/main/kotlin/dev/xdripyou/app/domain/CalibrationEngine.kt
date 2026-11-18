package dev.xdripyou.app.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

data class CalibrationRecord(
    val id: Long = 0L,
    val timestampMs: Long,
    val bgMgdl: Double,
    val rawValue: Double,
    val slope: Double,
    val intercept: Double,
    val sensorId: String = "",
    val slopeConfidence: Double = 0.5,
    val sensorConfidence: Double = 0.8,
    val note: String = "",
)

data class SlopeIntercept(
    val slope: Double = 1.0,
    val intercept: Double = 0.0,
    val calibrationCount: Int = 0,
    val mardPercent: Double? = null,
    val isSinglePoint: Boolean = true,
) {
    fun rawToBg(raw: Double): Double = (raw * slope + intercept).coerceIn(20.0, 600.0)
    fun bgToRaw(bgMgdl: Double): Double = if (abs(slope) > 1e-6) (bgMgdl - intercept) / slope else bgMgdl
}

/**
 * Port of xDrip's `Calibration.java` weighted least-squares regression engine and sensor noise
 * smoothing (`BgReading.java` Savitzky-Golay / weighted noise estimation).
 */
object CalibrationEngine {

    const val MIN_SLOPE = 0.45
    const val MAX_SLOPE = 2.80
    const val DEFAULT_SLOPE = 1.0

    /**
     * Port of xDrip `Calibration.calculateWeight()`: gives exponentially more weight to newer
     * calibrations inside the active sensor session while keeping older points anchored.
     */
    fun calculateWeight(
        calibrationTimestampMs: Long,
        firstCalibrationTimestampMs: Long,
        newestTimestampMs: Long,
    ): Double {
        val totalSpan = (newestTimestampMs - firstCalibrationTimestampMs).coerceAtLeast(1L).toDouble()
        val lifeTime = (calibrationTimestampMs - firstCalibrationTimestampMs).coerceAtLeast(0L).toDouble()
        var timePercentage = lifeTime / totalSpan
        if (timePercentage > 0.5) {
            val adjusted = 1.0 - timePercentage
            timePercentage = (4.0 * adjusted * adjusted * adjusted) / 2.0
        } else {
            timePercentage = (4.0 * timePercentage * timePercentage * timePercentage) / 2.0
        }
        return (timePercentage + 0.1).coerceIn(0.1, 2.0)
    }

    /**
     * Computes weighted least-squares regression `(y = slope * x + intercept)` where `x = rawValue`
     * and `y = bgMgdl`. Matches xDrip's `calculate_w_l_s()`.
     */
    fun calculateWls(calibrations: List<CalibrationRecord>): SlopeIntercept {
        val valid = calibrations.filter { it.bgMgdl in 20.0..600.0 && it.rawValue > 0.0 }
            .sortedBy { it.timestampMs }
        if (valid.isEmpty()) {
            return SlopeIntercept(slope = 1.0, intercept = 0.0, calibrationCount = 0, mardPercent = null, isSinglePoint = true)
        }
        if (valid.size == 1) {
            val single = valid.first()
            return SlopeIntercept(
                slope = 1.0,
                intercept = single.bgMgdl - single.rawValue,
                calibrationCount = 1,
                mardPercent = 0.0,
                isSinglePoint = true,
            )
        }

        val firstTs = valid.first().timestampMs
        val lastTs = valid.last().timestampMs
        var sX = 0.0
        var sY = 0.0
        var sXX = 0.0
        var sXY = 0.0
        var sumW = 0.0

        for (c in valid) {
            val baseWeight = calculateWeight(c.timestampMs, firstTs, lastTs)
            val confWeight = max(0.2, c.sensorConfidence)
            val w = baseWeight * confWeight
            val x = c.rawValue
            val y = c.bgMgdl
            sX += w * x
            sY += w * y
            sXX += w * x * x
            sXY += w * x * y
            sumW += w
        }

        val denom = sXX * sumW - sX * sX
        val rawSlope: Double
        val rawIntercept: Double
        var singlePoint = false
        if (abs(denom) < 1e-6) {
            rawSlope = 1.0
            rawIntercept = (sY / sumW) - (sX / sumW)
            singlePoint = true
        } else {
            val calcSlope = (sXY * sumW - sX * sY) / denom
            if (calcSlope !in MIN_SLOPE..MAX_SLOPE) {
                rawSlope = calcSlope.coerceIn(MIN_SLOPE, MAX_SLOPE)
                rawIntercept = (sY / sumW) - rawSlope * (sX / sumW)
            } else {
                rawSlope = calcSlope
                rawIntercept = (sY - rawSlope * sX) / sumW
            }
        }

        val mard = valid.map { c ->
            val predicted = rawSlope * c.rawValue + rawIntercept
            abs(predicted - c.bgMgdl) / max(c.bgMgdl, 1.0) * 100.0
        }.average()

        return SlopeIntercept(
            slope = rawSlope,
            intercept = rawIntercept,
            calibrationCount = valid.size,
            mardPercent = mard,
            isSinglePoint = singlePoint,
        )
    }

    /**
     * Port of xDrip's multi-point sensor noise smoothing (5-point triangular Savitzky-Golay filter
     * preserving the newest reading responsiveness while dampening single-sample jitter).
     */
    fun smoothReadings(chronological: List<GlucoseReading>): List<GlucoseReading> {
        if (chronological.size < 3) return chronological
        val out = ArrayList<GlucoseReading>(chronological.size)
        for (i in chronological.indices) {
            val weights = doubleArrayOf(0.1, 0.2, 0.4, 0.2, 0.1)
            var sumW = 0.0
            var sumV = 0.0
            for (k in -2..2) {
                val idx = i + k
                if (idx in chronological.indices) {
                    val dtMin = abs(chronological[i].timestampMs - chronological[idx].timestampMs) / 60_000.0
                    if (dtMin <= 18.0) {
                        val w = weights[k + 2]
                        sumW += w
                        sumV += w * chronological[idx].mgdl
                    }
                }
            }
            val smoothed = if (sumW > 0.0) (sumV / sumW) else chronological[i].mgdl
            out += chronological[i].copy(mgdl = smoothed)
        }
        return out
    }

    /**
     * Estimates xDrip-style signal noise index `[0.0..1.0]` from recent reading jitter.
     */
    fun estimateNoiseLevel(recentDesc: List<GlucoseReading>): Double {
        if (recentDesc.size < 4) return 0.0
        val points = recentDesc.take(8).sortedBy { it.timestampMs }
        var secondDiffSum = 0.0
        var count = 0
        for (i in 1 until points.size - 1) {
            val dt1 = (points[i].timestampMs - points[i - 1].timestampMs) / 60_000.0
            val dt2 = (points[i + 1].timestampMs - points[i].timestampMs) / 60_000.0
            if (dt1 in 1.0..12.0 && dt2 in 1.0..12.0) {
                val d1 = (points[i].mgdl - points[i - 1].mgdl) / dt1
                val d2 = (points[i + 1].mgdl - points[i].mgdl) / dt2
                secondDiffSum += abs(d2 - d1)
                count++
            }
        }
        if (count == 0) return 0.0
        return ((secondDiffSum / count) / 3.5).pow(1.0).coerceIn(0.0, 1.0)
    }
}
