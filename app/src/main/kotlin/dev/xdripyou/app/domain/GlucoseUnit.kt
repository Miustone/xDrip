package dev.xdripyou.app.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Glucose unit conversion and formatting matching xDrip's `Unitized` class.
 */
object GlucoseUnit {
    const val MGDL_TO_MMOL = 0.0554994394556615
    const val MMOL_TO_MGDL = 18.0182

    fun toDisplayValue(mgdl: Double, useMgdl: Boolean): Double =
        if (useMgdl) mgdl else mgdl * MGDL_TO_MMOL

    fun toMgdl(displayValue: Double, fromMgdl: Boolean): Double =
        if (fromMgdl) displayValue else displayValue * MMOL_TO_MGDL

    fun unitLabel(useMgdl: Boolean): String = if (useMgdl) "mg/dL" else "mmol/L"

    fun formatValue(mgdl: Double, useMgdl: Boolean): String {
        if (mgdl >= 400.0) return "HIGH"
        if (mgdl < 40.0) return "LOW"
        return if (useMgdl) {
            mgdl.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", mgdl * MGDL_TO_MMOL)
        }
    }

    fun formatSpread(mgdl: Double, useMgdl: Boolean): String {
        return if (useMgdl) {
            mgdl.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", mgdl * MGDL_TO_MMOL)
        }
    }

    fun formatDelta(deltaMgdl: Double, useMgdl: Boolean): String {
        return if (useMgdl) {
            val rounded = deltaMgdl.roundToInt()
            if (rounded >= 0) "+$rounded" else "$rounded"
        } else {
            val deltaMmol = deltaMgdl * MGDL_TO_MMOL
            val prefix = if (deltaMmol >= 0) "+" else ""
            "$prefix${String.format(Locale.US, "%.1f", deltaMmol)}"
        }
    }
}
