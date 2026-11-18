package dev.xdripyou.app

import dev.xdripyou.app.domain.AppSettings
import dev.xdripyou.app.domain.IobCobCalculator
import dev.xdripyou.app.domain.TreatmentEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IobCobCalculatorTest {

    @Test
    fun `insulin remaining fraction follows onset peak and dia bounds`() {
        assertEquals(1.0, IobCobCalculator.insulinRemainingFraction(0.0, 15.0, 65.0, 270.0), 1e-6)
        assertEquals(1.0, IobCobCalculator.insulinRemainingFraction(10.0, 15.0, 65.0, 270.0), 1e-6)
        val atPeak = IobCobCalculator.insulinRemainingFraction(65.0, 15.0, 65.0, 270.0)
        assertTrue(atPeak in 0.70..0.90)
        val midTail = IobCobCalculator.insulinRemainingFraction(150.0, 15.0, 65.0, 270.0)
        assertTrue(midTail in 0.10..0.45)
        assertEquals(0.0, IobCobCalculator.insulinRemainingFraction(270.0, 15.0, 65.0, 270.0), 1e-6)
        assertEquals(0.0, IobCobCalculator.insulinRemainingFraction(350.0, 15.0, 65.0, 270.0), 1e-6)
    }

    @Test
    fun `calculates stacked bolus iob accurately`() {
        val now = 1_700_000_000_000L
        val treatments = listOf(
            TreatmentEntry(timestampMs = now - 5 * 60_000L, insulinUnits = 2.0),
            TreatmentEntry(timestampMs = now - 135 * 60_000L, insulinUnits = 4.0),
            TreatmentEntry(timestampMs = now - 300 * 60_000L, insulinUnits = 5.0),
        )
        val iob = IobCobCalculator.calculateIob(treatments, atMs = now, durationMinutes = 270, peakMinutes = 65)
        // First bolus (5m < 15m onset) = 2.0 U, second bolus (135m) > 0, third (300m >= 270m) = 0
        assertTrue(iob > 2.5 && iob < 4.5)
    }

    @Test
    fun `calculates cob with delay and linear absorption`() {
        val now = 1_700_000_000_000L
        val treatments = listOf(
            TreatmentEntry(timestampMs = now - 10 * 60_000L, carbsGrams = 30.0),
            TreatmentEntry(timestampMs = now - 45 * 60_000L, carbsGrams = 30.0),
        )
        // First: 10m <= 15m delay -> 30g left. Second: (45 - 15) * (30/60) = 15g absorbed -> 15g left.
        val cob = IobCobCalculator.calculateCob(
            treatments = treatments,
            atMs = now,
            carbDelayMinutes = 15,
            carbAbsorptionGramsPerHour = 30,
        )
        assertEquals(45.0, cob, 1e-4)
    }

    @Test
    fun `snapshot respects showIobCob setting`() {
        val now = 1_700_000_000_000L
        val treatments = listOf(
            TreatmentEntry(timestampMs = now - 10 * 60_000L, insulinUnits = 3.0, carbsGrams = 40.0),
        )
        val enabled = IobCobCalculator.snapshot(treatments, now, AppSettings(showIobCob = true))
        assertEquals(3.0, enabled.iobUnits, 1e-4)
        assertEquals(40.0, enabled.cobGrams, 1e-4)

        val disabled = IobCobCalculator.snapshot(treatments, now, AppSettings(showIobCob = false))
        assertEquals(0.0, disabled.iobUnits, 1e-6)
        assertEquals(0.0, disabled.cobGrams, 1e-6)
    }
}
