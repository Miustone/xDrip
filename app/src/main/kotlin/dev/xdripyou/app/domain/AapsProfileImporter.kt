package dev.xdripyou.app.domain

import kotlin.math.roundToInt

data class BasalTimeBlock(
    val startMinuteOfDay: Int,
    val unitsPerHour: Double,
)

data class AapsImportedProfile(
    val basalBlocks: List<BasalTimeBlock>,
    val carbRatioGramsPerUnit: Double?,
    val isfMgdlPerUnit: Double?,
    val insulinDurationMinutes: Int?,
    val isMmolUnits: Boolean,
    val profileName: String? = null,
) {
    fun toHourlyRates(): List<Double> {
        if (basalBlocks.isEmpty()) return BasalSchedule().hourlyRates
        val sorted = basalBlocks.sortedBy { it.startMinuteOfDay }
        return List(24) { hour ->
            val minute = hour * 60
            val active = sorted.lastOrNull { it.startMinuteOfDay <= minute } ?: sorted.first()
            active.unitsPerHour.coerceIn(0.0, 10.0)
        }
    }
}

/**
 * Pure-Kotlin port of xDrip's `ImportAapsProfile.java` / `AapsProfile.java`.
 *
 * Parses exported AndroidAPS / OpenAPS JSON profile payloads (`basal`, `carbratio`, `sens`, `dia`,
 * `units`) using pure Kotlin scanning so both Android runtime and JVM unit tests execute identically.
 */
object AapsProfileImporter {

    private val numberFieldRegex = Regex(""""([A-Za-z0-9_]+)"\s*:\s*(-?\d+(?:\.\d+)?)""")
    private val stringFieldRegex = Regex(""""([A-Za-z0-9_]+)"\s*:\s*"([^"]*)"""")

    fun parseProfileJson(rawJson: String): Result<AapsImportedProfile> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.contains("{")) {
            return Result.failure(IllegalArgumentException("Leeres oder ungültiges JSON-Profil"))
        }

        val unitStr = extractTopString(trimmed, "units")?.lowercase()
        val isMmol = unitStr?.contains("mmol") == true
        val profileName = extractTopString(trimmed, "profile") ?: extractTopString(trimmed, "name")

        val diaHours = extractTopNumber(trimmed, "dia")
        val diaMinutes = diaHours
            ?.takeIf { it in 2.0..10.0 }
            ?.let { (it * 60.0).roundToInt().coerceIn(120, 480) }

        val basalObjects = extractObjectArray(trimmed, "basal")
        val basalBlocks = basalObjects.mapNotNull { obj ->
            val value = extractFieldNumber(obj, "value") ?: return@mapNotNull null
            val minutes = extractFieldNumber(obj, "timeAsMinutes")?.roundToInt()
                ?: extractFieldString(obj, "time")?.let(::parseTimeToMinutes)
                ?: return@mapNotNull null
            BasalTimeBlock(
                startMinuteOfDay = minutes.coerceIn(0, 1439),
                unitsPerHour = value.coerceIn(0.0, 20.0),
            )
        }.sortedBy { it.startMinuteOfDay }
            .distinctBy { it.startMinuteOfDay }

        val crObjects = extractObjectArray(trimmed, "carbratio")
        val firstCr = crObjects.firstNotNullOfOrNull { extractFieldNumber(it, "value") }
            ?.takeIf { it in 1.0..150.0 }

        val sensObjects = extractObjectArray(trimmed, "sens")
        val rawSens = sensObjects.firstNotNullOfOrNull { extractFieldNumber(it, "value") }
        val sensMgdl = rawSens?.let { v ->
            val converted = if (isMmol || v <= 15.0) v * GlucoseUnit.MMOL_TO_MGDL else v
            converted.takeIf { it in 5.0..400.0 }
        }

        if (basalBlocks.isEmpty() && firstCr == null && sensMgdl == null && diaMinutes == null) {
            return Result.failure(IllegalArgumentException("Keine bekannten AAPS-Felder (basal/carbratio/sens/dia) gefunden"))
        }

        return Result.success(
            AapsImportedProfile(
                basalBlocks = basalBlocks,
                carbRatioGramsPerUnit = firstCr,
                isfMgdlPerUnit = sensMgdl,
                insulinDurationMinutes = diaMinutes,
                isMmolUnits = isMmol,
                profileName = profileName,
            )
        )
    }

    private fun extractTopNumber(json: String, key: String): Double? =
        Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""").find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

    private fun extractTopString(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.getOrNull(1)

    private fun extractFieldNumber(obj: String, key: String): Double? {
        for (m in numberFieldRegex.findAll(obj)) {
            if (m.groupValues[1] == key) return m.groupValues[2].toDoubleOrNull()
        }
        return null
    }

    private fun extractFieldString(obj: String, key: String): String? {
        for (m in stringFieldRegex.findAll(obj)) {
            if (m.groupValues[1] == key) return m.groupValues[2]
        }
        return null
    }

    private fun parseTimeToMinutes(hhmm: String): Int? {
        val parts = hhmm.trim().split(':')
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun extractObjectArray(json: String, arrayKey: String): List<String> {
        val keyMatch = Regex(""""$arrayKey"\s*:\s*\[""").find(json) ?: return emptyList()
        var idx = keyMatch.range.last + 1
        var bracketDepth = 1
        val objects = mutableListOf<String>()
        var objStart = -1
        var braceDepth = 0
        var inString = false
        var escaped = false

        while (idx < json.length && bracketDepth > 0) {
            val ch = json[idx]
            if (escaped) {
                escaped = false
            } else if (ch == '\\' && inString) {
                escaped = true
            } else if (ch == '"') {
                inString = !inString
            } else if (!inString) {
                when (ch) {
                    '[' -> bracketDepth++
                    ']' -> bracketDepth--
                    '{' -> {
                        if (braceDepth == 0) objStart = idx
                        braceDepth++
                    }
                    '}' -> {
                        braceDepth--
                        if (braceDepth == 0 && objStart >= 0) {
                            objects += json.substring(objStart, idx + 1)
                            objStart = -1
                        }
                    }
                }
            }
            idx++
        }
        return objects
    }
}
