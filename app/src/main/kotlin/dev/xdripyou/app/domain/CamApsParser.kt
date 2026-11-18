package dev.xdripyou.app.domain

import kotlin.math.roundToInt

data class ParsedGlucoseCandidate(
    val mgdl: Double,
    val rawText: String,
    val detectedUnit: String,
)

/**
 * Notification text parser for CamAPS FX Companion App notifications.
 * Mirrors xDrip's `UiBasedCollector` filtering and bounds validation,
 * enhanced with automatic unit detection from notification text.
 */
object CamApsParser {
    const val MIN_VALID_MGDL = 40.0
    const val MAX_VALID_MGDL = 405.0

    val defaultCamApsPackages: Set<String> = setOf(
        "com.camdiab.fx_alert.mmoll",
        "com.camdiab.fx_alert.mgdl",
        "com.camdiab.fx_alert.hx.mmoll",
        "com.camdiab.fx_alert.hx.mgdl",
        "com.camdiab.fx_alert.mmoll.ca",
    )

    /** Extended CGM companion apps supported by xDrip's `UiBasedCollector`. */
    val extendedCompanionPackages: Set<String> = defaultCamApsPackages + setOf(
        "com.dexcom.g6",
        "com.dexcom.g7",
        "com.dexcom.dexcomone",
        "com.dexcom.d1plus",
        "com.dexcom.stelo",
        "com.medtronic.diabetes.guardian",
        "com.medtronic.diabetes.guardianconnect",
        "com.medtronic.diabetes.minimedmobile.eu",
        "com.medtronic.diabetes.minimedmobile.us",
        "com.medtronic.diabetes.simplera.eu",
        "com.senseonics.gen12androidapp",
        "com.senseonics.androidapp",
        "com.senseonics.eversense365.us",
        "com.microtech.aidexx.mgdl",
        "com.microtech.aidexx.linxneo.mmoll",
        "com.microtech.aidexx.diaexport.mmoll",
        "com.microtech.aidexx",
        "com.ottai.seas",
        "com.ottai.tag",
        "com.sinocare.cgm.ce",
        "com.sinocare.ican.health.ce",
        "com.diabeloop.dblg2",
    )

    private val decimalPattern = Regex("""\b([0-9]{1,2}[.,][0-9]{1,2})\b""")
    private val integerPattern = Regex("""\b([0-9]{2,3})\b""")
    private val mmolHintPattern = Regex("""mmol""", RegexOption.IGNORE_CASE)
    private val mgdlHintPattern = Regex("""mg\s*/?\s*dl""", RegexOption.IGNORE_CASE)

    fun filterArrows(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val code = ch.code
            val isArrowOrSymbol =
                (code in 0x2190..0x21FF) ||
                (code in 0x2700..0x27BF) ||
                (code in 0x2900..0x297F) ||
                (code in 0x2B00..0x2BFF)
            if (!isArrowOrSymbol) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun basicClean(input: String): String {
        return filterArrows(input)
            .replace("\u00A0", " ")
            .replace("\u2060", "")
            .replace("\\", "/")
            .replace("≤", "")
            .replace("≥", "")
            .trim()
    }

    fun stripUnitTokens(input: String): String {
        return basicClean(input)
            .replace(Regex("""mmol\s*/?\s*l""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""mg\s*/?\s*dl""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun tryExtractGlucose(
        texts: List<String>,
        unitMode: SourceUnitMode,
    ): ParsedGlucoseCandidate? {
        val matches = mutableListOf<ParsedGlucoseCandidate>()

        for (raw in texts) {
            val cleaned = basicClean(raw)
            if (cleaned.isEmpty()) continue
            val stripped = stripUnitTokens(cleaned)
            val hasMmolHint = mmolHintPattern.containsMatchIn(cleaned)
            val hasMgdlHint = mgdlHintPattern.containsMatchIn(cleaned)

            val exact = parseExactToken(stripped, unitMode, hasMmolHint, hasMgdlHint)
            if (exact != null) {
                matches.add(ParsedGlucoseCandidate(exact.first, raw, exact.second))
                continue
            }

            if (unitMode == SourceUnitMode.MMOL || (unitMode == SourceUnitMode.AUTO && (hasMmolHint || !hasMgdlHint))) {
                val decMatch = decimalPattern.find(stripped)
                if (decMatch != null) {
                    val mmol = decMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (mmol != null && mmol in 2.2..22.5) {
                        val mgdl = (mmol * GlucoseUnit.MMOL_TO_MGDL).roundToInt().toDouble()
                        if (mgdl in MIN_VALID_MGDL..MAX_VALID_MGDL) {
                            matches.add(ParsedGlucoseCandidate(mgdl, raw, "mmol/L"))
                            continue
                        }
                    }
                }
            }

            if (unitMode == SourceUnitMode.MGDL || (unitMode == SourceUnitMode.AUTO && (hasMgdlHint || !hasMmolHint))) {
                val intMatch = integerPattern.find(stripped)
                if (intMatch != null) {
                    val mgdlInt = intMatch.groupValues[1].toIntOrNull()
                    if (mgdlInt != null && mgdlInt.toDouble() in MIN_VALID_MGDL..MAX_VALID_MGDL) {
                        matches.add(ParsedGlucoseCandidate(mgdlInt.toDouble(), raw, "mg/dL"))
                    }
                }
            }
        }

        if (matches.isEmpty()) return null
        val distinctValues = matches.map { it.mgdl }.distinct()
        return if (distinctValues.size == 1) matches.first() else null
    }

    private fun parseExactToken(
        stripped: String,
        unitMode: SourceUnitMode,
        hasMmolHint: Boolean,
        hasMgdlHint: Boolean,
    ): Pair<Double, String>? {
        val isDecimal = stripped.matches(Regex("""[0-9]+[.,][0-9]+"""))
        val isInteger = stripped.matches(Regex("""[0-9]+"""))

        if (unitMode == SourceUnitMode.MMOL || (unitMode == SourceUnitMode.AUTO && (hasMmolHint || isDecimal))) {
            if (isDecimal || (hasMmolHint && isInteger)) {
                val mmol = stripped.replace(',', '.').toDoubleOrNull() ?: return null
                val mgdl = (mmol * GlucoseUnit.MMOL_TO_MGDL).roundToInt().toDouble()
                if (mgdl in MIN_VALID_MGDL..MAX_VALID_MGDL) {
                    return mgdl to "mmol/L"
                }
            }
            if (unitMode == SourceUnitMode.MMOL) return null
        }

        if (unitMode == SourceUnitMode.MGDL || (unitMode == SourceUnitMode.AUTO && (hasMgdlHint || isInteger))) {
            if (isInteger) {
                val mgdl = stripped.toIntOrNull()?.toDouble() ?: return null
                if (mgdl in MIN_VALID_MGDL..MAX_VALID_MGDL) {
                    return mgdl to "mg/dL"
                }
            }
        }
        return null
    }
}
