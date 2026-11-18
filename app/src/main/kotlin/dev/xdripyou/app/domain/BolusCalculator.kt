package dev.xdripyou.app.domain

import kotlin.math.max
import kotlin.math.round

data class BolusSuggestion(
    val mealUnits: Double,
    val correctionUnits: Double,
    val iobDeductedUnits: Double,
    val totalUnits: Double,
)

/**
 * Simple IOB-aware Bolus & Correction Calculator using configured Carb Ratio, ISF, and Target BG.
 */
object BolusCalculator {
    fun calculate(
        carbsGrams: Double,
        currentBgMgdl: Double?,
        activeIobUnits: Double,
        carbRatioGramsPerUnit: Double,
        isfMgdlPerUnit: Double,
        targetBgMgdl: Double,
    ): BolusSuggestion {
        val safeCr = carbRatioGramsPerUnit.coerceIn(1.0, 100.0)
        val safeIsf = isfMgdlPerUnit.coerceIn(5.0, 250.0)

        val meal = if (carbsGrams > 0.0) carbsGrams / safeCr else 0.0
        val correction = if (currentBgMgdl != null && currentBgMgdl > 0.0) {
            (currentBgMgdl - targetBgMgdl) / safeIsf
        } else {
            0.0
        }
        val rawTotal = meal + correction - activeIobUnits.coerceAtLeast(0.0)
        val roundedTotal = round(max(0.0, rawTotal) * 10.0) / 10.0

        return BolusSuggestion(
            mealUnits = round(meal * 10.0) / 10.0,
            correctionUnits = round(correction * 10.0) / 10.0,
            iobDeductedUnits = round(activeIobUnits.coerceAtLeast(0.0) * 10.0) / 10.0,
            totalUnits = roundedTotal,
        )
    }
}
