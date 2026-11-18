package dev.xdripyou.app.domain

import kotlin.math.pow

/**
 * Computes damped short-term glucose predictions (next 30 minutes) from recent readings,
 * inspired by xDrip's `BgReading.find_new_curve` / damped velocity extrapolation.
 */
object GlucosePredictor {

    fun predict(
        recentDesc: List<GlucoseReading>,
        nowMs: Long,
        horizonMinutes: Int = 30,
        stepMinutes: Int = 5,
    ): List<PredictionPoint> {
        if (recentDesc.size < 2) return emptyList()
        val p0 = recentDesc[0]
        val p1 = recentDesc[1]
        if (nowMs - p0.timestampMs > GlucoseReading.STALE_DATA_MS) return emptyList()
        val dt01Min = (p0.timestampMs - p1.timestampMs) / 60_000.0
        if (dt01Min <= 0.0 || dt01Min > 15.0) return emptyList()

        val v0 = (p0.mgdl - p1.mgdl) / dt01Min
        // Second-order acceleration term if 3 recent points are available
        val accel = if (recentDesc.size >= 3) {
            val p2 = recentDesc[2]
            val dt12Min = (p1.timestampMs - p2.timestampMs) / 60_000.0
            if (dt12Min > 0.0 && dt12Min <= 15.0) {
                val vPrev = (p1.mgdl - p2.mgdl) / dt12Min
                ((v0 - vPrev) / ((dt01Min + dt12Min) / 2.0)).coerceIn(-0.25, 0.25)
            } else {
                0.0
            }
        } else {
            0.0
        }

        val points = mutableListOf<PredictionPoint>()
        var currentMgdl = p0.mgdl
        var currentVel = v0.coerceIn(-5.0, 5.0)
        val steps = (horizonMinutes / stepMinutes).coerceAtLeast(1)

        for (step in 1..steps) {
            // Apply mild damping per 5-min step so long extrapolations stay realistic
            val damping = 0.88.pow(step.toDouble())
            currentVel = (currentVel + accel * stepMinutes * 0.5).coerceIn(-5.0, 5.0)
            currentMgdl = (currentMgdl + currentVel * stepMinutes * damping).coerceIn(39.0, 400.0)
            points.add(
                PredictionPoint(
                    timestampMs = p0.timestampMs + step * stepMinutes * 60_000L,
                    mgdl = currentMgdl,
                )
            )
        }
        return points
    }

    fun minutesUntilBelow(
        predictions: List<PredictionPoint>,
        baseTimestampMs: Long,
        thresholdMgdl: Double,
        horizonMinutes: Int,
    ): Int? {
        for (pt in predictions) {
            val minsAhead = ((pt.timestampMs - baseTimestampMs) / 60_000L).toInt()
            if (minsAhead > horizonMinutes) break
            if (pt.mgdl <= thresholdMgdl) {
                return minsAhead.coerceAtLeast(1)
            }
        }
        return null
    }

    fun minutesUntilAbove(
        predictions: List<PredictionPoint>,
        baseTimestampMs: Long,
        thresholdMgdl: Double,
        horizonMinutes: Int,
    ): Int? {
        for (pt in predictions) {
            val minsAhead = ((pt.timestampMs - baseTimestampMs) / 60_000L).toInt()
            if (minsAhead > horizonMinutes) break
            if (pt.mgdl >= thresholdMgdl) {
                return minsAhead.coerceAtLeast(1)
            }
        }
        return null
    }
}
