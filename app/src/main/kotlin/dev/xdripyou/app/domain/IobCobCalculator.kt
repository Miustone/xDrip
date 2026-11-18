package dev.xdripyou.app.domain

import kotlin.math.max

enum class InsulinPreset(
    val wireValue: String,
    val labelDe: String,
    val onsetMinutes: Int,
    val peakMinutes: Int,
    val durationMinutes: Int,
) {
    ULTRA_RAPID_FIASP("fiasp", "Ultra-Schnell (Fiasp)", onsetMinutes = 10, peakMinutes = 45, durationMinutes = 240),
    ULTRA_RAPID_LYUMJEV("lyumjev", "Ultra-Schnell (Lyumjev)", onsetMinutes = 8, peakMinutes = 40, durationMinutes = 220),
    RAPID_NOVORAPID("novorapid", "Schnell (NovoRapid / Humalog)", onsetMinutes = 15, peakMinutes = 65, durationMinutes = 270),
    CUSTOM("custom", "Benutzerdefiniert", onsetMinutes = 15, peakMinutes = 65, durationMinutes = 270);

    companion object {
        fun fromWire(value: String?): InsulinPreset =
            entries.firstOrNull { it.wireValue == value } ?: RAPID_NOVORAPID
    }
}

data class IobCobSnapshot(
    val iobUnits: Double = 0.0,
    val cobGrams: Double = 0.0,
) {
    val hasActiveIob: Boolean get() = iobUnits >= 0.05
    val hasActiveCob: Boolean get() = cobGrams >= 1.0
}

/**
 * Port of xDrip's `LinearTrapezoidInsulin` + `Treatments` IOB/COB calculation.
 *
 * Uses a trapezoidal insulin activity profile (onset -> peak -> duration) to compute exact
 * remaining Insulin on Board (IOB) and a linear delayed absorption model for Carbs on Board (COB).
 */
object IobCobCalculator {

    /**
     * Exact fraction of bolus still active [0.0..1.0] after [elapsedMinutes], matching xDrip's
     * `LinearTrapezoidInsulin.calculateIOB(minutes)`.
     */
    fun insulinRemainingFraction(
        elapsedMinutes: Double,
        onsetMinutes: Double = 15.0,
        peakMinutes: Double = 65.0,
        durationMinutes: Double = 270.0,
    ): Double {
        if (elapsedMinutes < 0.0) return 0.0
        val onset = onsetMinutes.coerceIn(0.0, 45.0)
        val duration = durationMinutes.coerceIn(120.0, 480.0)
        val peak = peakMinutes.coerceIn(onset + 10.0, duration - 20.0)
        if (elapsedMinutes >= duration) return 0.0
        if (elapsedMinutes < onset) return 1.0

        val peakHeight = 2.0 / (duration - onset)
        return if (elapsedMinutes < peak) {
            val dt = elapsedMinutes - onset
            (1.0 - (0.5 * dt * dt * peakHeight) / (peak - onset)).coerceIn(0.0, 1.0)
        } else {
            val rem = duration - elapsedMinutes
            ((0.5 * rem * rem * peakHeight) / (duration - peak)).coerceIn(0.0, 1.0)
        }
    }

    fun calculateIob(
        treatments: List<TreatmentEntry>,
        atMs: Long,
        durationMinutes: Int = 270,
        peakMinutes: Int = 65,
        onsetMinutes: Int = 15,
    ): Double {
        val maxWindowMs = durationMinutes.coerceIn(120, 480) * 60_000L
        var total = 0.0
        for (t in treatments) {
            if (t.insulinUnits <= 0.0) continue
            val elapsedMs = atMs - t.timestampMs
            if (elapsedMs < 0L || elapsedMs >= maxWindowMs) continue
            val elapsedMin = elapsedMs / 60_000.0
            total += t.insulinUnits * insulinRemainingFraction(
                elapsedMinutes = elapsedMin,
                onsetMinutes = onsetMinutes.toDouble(),
                peakMinutes = peakMinutes.toDouble(),
                durationMinutes = durationMinutes.toDouble(),
            )
        }
        return total.coerceAtLeast(0.0)
    }

    fun calculateCob(
        treatments: List<TreatmentEntry>,
        atMs: Long,
        carbDelayMinutes: Int = 15,
        carbAbsorptionGramsPerHour: Int = 30,
    ): Double {
        val delayMin = carbDelayMinutes.coerceIn(0, 60).toDouble()
        val ratePerMin = carbAbsorptionGramsPerHour.coerceIn(5, 120) / 60.0
        var total = 0.0
        for (t in treatments) {
            if (t.carbsGrams <= 0.0) continue
            val elapsedMs = atMs - t.timestampMs
            if (elapsedMs < 0L || elapsedMs > 8 * 3_600_000L) continue
            val elapsedMin = elapsedMs / 60_000.0
            val absorbed = if (elapsedMin <= delayMin) {
                0.0
            } else {
                (elapsedMin - delayMin) * ratePerMin
            }
            total += max(0.0, t.carbsGrams - absorbed)
        }
        return total.coerceAtLeast(0.0)
    }

    fun snapshot(
        treatments: List<TreatmentEntry>,
        atMs: Long,
        settings: AppSettings,
    ): IobCobSnapshot {
        if (!settings.showIobCob) return IobCobSnapshot()
        val preset = InsulinPreset.fromWire(settings.insulinPreset)
        val onset = if (preset == InsulinPreset.CUSTOM) 15 else preset.onsetMinutes
        val peak = if (preset == InsulinPreset.CUSTOM) settings.insulinPeakMinutes else preset.peakMinutes
        val duration = if (preset == InsulinPreset.CUSTOM) settings.insulinDurationMinutes else preset.durationMinutes
        return IobCobSnapshot(
            iobUnits = calculateIob(
                treatments = treatments,
                atMs = atMs,
                durationMinutes = duration,
                peakMinutes = peak,
                onsetMinutes = onset,
            ),
            cobGrams = calculateCob(
                treatments = treatments,
                atMs = atMs,
                carbDelayMinutes = settings.carbDelayMinutes,
                carbAbsorptionGramsPerHour = settings.carbAbsorptionGramsPerHour,
            ),
        )
    }
}
