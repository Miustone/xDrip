package dev.xdripyou.app.domain

import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.abs

data class GlookoImportReading(
    val timestampMs: Long,
    val mgdl: Double,
    val source: String = "Glooko Import",
)

data class GlookoImportResult(
    val readings: List<GlookoImportReading> = emptyList(),
    val treatments: List<TreatmentEntry> = emptyList(),
    val basalHourlyRates: List<Double>? = null,
    val processedFiles: Int = 0,
)

/**
 * Parses official Glooko Web Export `.zip` archives (`my.glooko.com` -> Export to CSV)
 * as well as standalone Glooko `.csv` files (German and English localized column headers).
 */
object GlookoExportImporter {

    private const val MGDL_PER_MMOL = 18.0182

    fun parseBytes(bytes: ByteArray, fileName: String? = null): Result<GlookoImportResult> = runCatching {
        val isZip = (fileName?.endsWith(".zip", ignoreCase = true) == true) ||
            (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())

        val readings = mutableListOf<GlookoImportReading>()
        val treatments = mutableListOf<TreatmentEntry>()
        val basalBuckets = Array(24) { mutableListOf<Double>() }
        var fileCount = 0

        if (isZip) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".csv", ignoreCase = true)) {
                        val csvText = zis.readBytes().toString(Charsets.UTF_8)
                        parseSingleCsvInto(entry.name, csvText, readings, treatments, basalBuckets)
                        fileCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } else {
            val csvText = bytes.toString(Charsets.UTF_8)
            parseSingleCsvInto(fileName ?: "glooko.csv", csvText, readings, treatments, basalBuckets)
            fileCount = 1
        }

        val basalSchedule = if (basalBuckets.any { it.isNotEmpty() }) {
            val nonZeroAvg = basalBuckets.filter { it.isNotEmpty() }.map { it.average() }.average()
            List(24) { hr ->
                if (basalBuckets[hr].isNotEmpty()) basalBuckets[hr].average() else nonZeroAvg
            }
        } else null

        GlookoImportResult(
            readings = readings
                .filter { it.mgdl in 20.0..600.0 && it.timestampMs > 0L }
                .distinctBy { it.timestampMs / 60_000L }
                .sortedBy { it.timestampMs },
            treatments = deduplicateTreatments(treatments).sortedBy { it.timestampMs },
            basalHourlyRates = basalSchedule,
            processedFiles = fileCount,
        )
    }

    private fun parseSingleCsvInto(
        entryName: String,
        csvText: String,
        outReadings: MutableList<GlookoImportReading>,
        outTreatments: MutableList<TreatmentEntry>,
        basalBuckets: Array<MutableList<Double>>,
    ) {
        val cleanText = csvText.removePrefix("\uFEFF")
        val rawLines = cleanText.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (rawLines.size < 2) return

        // Glooko CSVs often include 1 metadata header line (e.g. "Name:... Date Range:...") before column headers.
        val headerIndex = rawLines.indexOfFirst { line ->
            val lower = line.lowercase(Locale.ROOT)
            (lower.contains("timestamp") || lower.contains("zeitstempel") || lower.contains("datum")) &&
                (line.contains(',') || line.contains(';'))
        }.takeIf { it >= 0 } ?: 0

        if (headerIndex >= rawLines.lastIndex) return
        val headerLine = rawLines[headerIndex]
        val delimiter = detectDelimiter(headerLine)
        val headers = splitCsvLine(headerLine, delimiter)
        val lowerHeaders = headers.map { it.trim().lowercase(Locale.ROOT) }
        val lowerName = entryName.lowercase(Locale.ROOT)

        val tsIdx = findColumnIndex(lowerHeaders, "timestamp", "zeitstempel", "date/time", "datum/uhrzeit", "datum")
        if (tsIdx < 0) return

        val cgmIdx = findColumnIndex(
            lowerHeaders,
            "cgm glucose value",
            "cgm-glukosewert",
            "glukosewert",
            "glucose value",
            "sensor glucose",
            "sensorglukose",
        )
        val bgIdx = findColumnIndex(
            lowerHeaders,
            "glucose value",
            "blutzucker",
            "bg",
            "messwert",
        )
        val insulinIdx = findColumnIndex(
            lowerHeaders,
            "insulin delivered",
            "abgegebenes insulin",
            "bolus volume",
            "bolusvolumen",
            "insulin (u)",
            "insulin (e)",
            "insulin",
            "dose",
            "dosis",
        )
        val carbsIdx = findColumnIndex(
            lowerHeaders,
            "carbs input",
            "carbs (g)",
            "kohlenhydrate (g)",
            "kohlenhydrate",
            "carbs",
            "kh (g)",
        )
        val basalRateIdx = findColumnIndex(
            lowerHeaders,
            "rate",
            "basal rate",
            "basalrate",
            "units/hr",
            "e/std",
        )
        val durationIdx = findColumnIndex(lowerHeaders, "duration", "dauer")
        val typeIdx = findColumnIndex(lowerHeaders, "bolus type", "insulin type", "type", "typ", "name", "bezeichnung")
        val noteIdx = findColumnIndex(lowerHeaders, "notes", "notiz", "comment", "kommentar", "food", "mahlzeit", "activity")

        val cgmIsMmolHeader = cgmIdx >= 0 && lowerHeaders[cgmIdx].contains("mmol")
        val bgIsMmolHeader = bgIdx >= 0 && lowerHeaders[bgIdx].contains("mmol")

        val isBasalFile = lowerName.contains("basal") || (basalRateIdx >= 0 && insulinIdx < 0)
        val isExerciseFile = lowerName.contains("exercise") || lowerName.contains("sport") || lowerName.contains("aktiv")
        val isCgmFile = lowerName.contains("cgm") || (cgmIdx >= 0 && lowerHeaders[cgmIdx].contains("cgm"))

        for (i in (headerIndex + 1) until rawLines.size) {
            val cols = splitCsvLine(rawLines[i], delimiter)
            if (tsIdx >= cols.size) continue
            val tsMs = parseFlexibleTimestamp(cols[tsIdx]) ?: continue

            if (isBasalFile && basalRateIdx >= 0 && basalRateIdx < cols.size) {
                val rate = parseDecimal(cols[basalRateIdx])
                if (rate != null && rate in 0.0..20.0) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = tsMs }
                    val hr = cal.get(java.util.Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                    basalBuckets[hr] += rate
                }
                continue
            }

            if (isExerciseFile) {
                val durMins = durationIdx.takeIf { it in cols.indices }?.let { parseDecimal(cols[it]) }?.toInt()
                val label = (typeIdx.takeIf { it in cols.indices }?.let { cols[it].trim() }
                    ?: noteIdx.takeIf { it in cols.indices }?.let { cols[it].trim() }
                    ?: "Training").ifBlank { "Training" }
                val durText = if (durMins != null && durMins > 0) " (${durMins}m)" else ""
                outTreatments += TreatmentEntry(
                    timestampMs = tsMs,
                    note = "$label$durText · Glooko",
                    eventType = "Exercise",
                )
                continue
            }

            // CGM reading
            if (isCgmFile || cgmIdx >= 0) {
                val idx = if (cgmIdx >= 0) cgmIdx else bgIdx
                if (idx in cols.indices) {
                    val raw = parseDecimal(cols[idx])
                    if (raw != null) {
                        val mgdl = normalizeBg(raw, cgmIsMmolHeader || bgIsMmolHeader)
                        if (mgdl != null) {
                            outReadings += GlookoImportReading(
                                timestampMs = tsMs,
                                mgdl = mgdl,
                                source = "Glooko CSV",
                            )
                        }
                    }
                }
            } else if (bgIdx in cols.indices && lowerName.contains("bg")) {
                val raw = parseDecimal(cols[bgIdx])
                if (raw != null) {
                    val mgdl = normalizeBg(raw, bgIsMmolHeader)
                    if (mgdl != null) {
                        outTreatments += TreatmentEntry(
                            timestampMs = tsMs,
                            bloodGlucoseMgdl = mgdl,
                            note = "Glooko BZ",
                            eventType = "BG Check",
                        )
                    }
                }
            }

            // Insulin & Carbs treatments
            val insulinVal = insulinIdx.takeIf { it in cols.indices }?.let { parseDecimal(cols[it]) }?.takeIf { it > 0.0 } ?: 0.0
            val carbsVal = carbsIdx.takeIf { it in cols.indices }?.let { parseDecimal(cols[it]) }?.takeIf { it > 0.0 } ?: 0.0
            if (insulinVal > 0.0 || carbsVal > 0.0) {
                val kindLabel = listOfNotNull(
                    typeIdx.takeIf { it in cols.indices }?.let { cols[it].trim().takeIf(String::isNotEmpty) },
                    noteIdx.takeIf { it in cols.indices }?.let { cols[it].trim().takeIf(String::isNotEmpty) },
                ).joinToString(" · ")
                outTreatments += TreatmentEntry(
                    timestampMs = tsMs,
                    insulinUnits = insulinVal,
                    carbsGrams = carbsVal,
                    note = if (kindLabel.isNotBlank()) "$kindLabel (Glooko)" else "Glooko Import",
                    eventType = when {
                        carbsVal > 0.0 && insulinVal > 0.0 -> "Meal Bolus"
                        carbsVal > 0.0 -> "Meal Bolus"
                        else -> "Correction Bolus"
                    },
                )
            }
        }
    }

    private fun normalizeBg(raw: Double, explicitMmol: Boolean): Double? {
        val mgdl = if (explicitMmol || raw in 1.0..33.0) raw * MGDL_PER_MMOL else raw
        return mgdl.takeIf { it in 20.0..600.0 }
    }

    private fun parseDecimal(raw: String): Double? {
        val cleaned = raw.trim().replace(" ", "").replace(',', '.')
        if (cleaned.isEmpty() || cleaned == "-" || cleaned.equals("null", ignoreCase = true)) return null
        return cleaned.toDoubleOrNull()
    }

    internal fun parseFlexibleTimestamp(raw: String): Long? {
        val s = raw.trim().removeSurrounding("\"")
        if (s.isEmpty()) return null
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli() }

        val localPatterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "dd.MM.yyyy HH:mm:ss",
            "dd.MM.yyyy HH:mm",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
        )
        for (p in localPatterns) {
            runCatching {
                val fmt = SimpleDateFormat(p, Locale.US)
                fmt.isLenient = false
                fmt.parse(s)?.time?.let { return it }
            }
            // Fallback using DateTimeFormatter for locale consistency
            runCatching {
                val ldt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p, Locale.US))
                return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return null
    }

    private fun detectDelimiter(header: String): Char {
        val semis = header.count { it == ';' }
        val commas = header.count { it == ',' }
        return if (semis > commas) ';' else ','
    }

    private fun findColumnIndex(lowerHeaders: List<String>, vararg needles: String): Int {
        for (needle in needles) {
            val exact = lowerHeaders.indexOfFirst { it == needle }
            if (exact >= 0) return exact
            val contains = lowerHeaders.indexOfFirst { it.contains(needle) }
            if (contains >= 0) return contains
        }
        return -1
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result += cur.toString().trim()
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        result += cur.toString().trim()
        return result
    }

    private fun deduplicateTreatments(items: List<TreatmentEntry>): List<TreatmentEntry> {
        val out = mutableListOf<TreatmentEntry>()
        for (item in items.sortedBy { it.timestampMs }) {
            val dup = out.any { existing ->
                abs(existing.timestampMs - item.timestampMs) <= 60_000L &&
                    abs(existing.insulinUnits - item.insulinUnits) < 0.05 &&
                    abs(existing.carbsGrams - item.carbsGrams) < 0.5 &&
                    existing.eventType == item.eventType
            }
            if (!dup) out += item
        }
        return out
    }
}
