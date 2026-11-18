package dev.xdripyou.app

import dev.xdripyou.app.domain.BolusCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class BolusCalculatorTest {

    @Test
    fun `computes meal plus correction bolus minus active iob`() {
        // 60g / 10 = 6.0U meal; (190 - 110) / 40 = +2.0U correction; - 1.5U IOB = 6.5U
        val res = BolusCalculator.calculate(
            carbsGrams = 60.0,
            currentBgMgdl = 190.0,
            activeIobUnits = 1.5,
            carbRatioGramsPerUnit = 10.0,
            isfMgdlPerUnit = 40.0,
            targetBgMgdl = 110.0,
        )
        assertEquals(6.0, res.mealUnits, 1e-4)
        assertEquals(2.0, res.correctionUnits, 1e-4)
        assertEquals(1.5, res.iobDeductedUnits, 1e-4)
        assertEquals(6.5, res.totalUnits, 1e-4)
    }

    @Test
    fun `never recommends negative bolus`() {
        val res = BolusCalculator.calculate(
            carbsGrams = 0.0,
            currentBgMgdl = 75.0,
            activeIobUnits = 2.0,
            carbRatioGramsPerUnit = 10.0,
            isfMgdlPerUnit = 40.0,
            targetBgMgdl = 110.0,
        )
        assertEquals(0.0, res.totalUnits, 1e-4)
    }
}
