package dev.xdripyou.app.data

import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

data class BackupPayload(
    val readings: List<GlucoseReading>,
    val treatments: List<TreatmentEntry>,
    val alertRules: List<AlertRule>,
)

object BackupManager {
    fun encodeToJson(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("app", "xDrip 2 You")
        root.put("version", 1)
        root.put("exportedAtMs", System.currentTimeMillis())

        val readingsArr = JSONArray()
        for (r in payload.readings) {
            readingsArr.put(
                JSONObject().apply {
                    put("timestampMs", r.timestampMs)
                    put("mgdl", r.mgdl)
                    put("slopePerMin", r.slopePerMin)
                    put("trendName", r.trend.wireName)
                    put("source", r.source)
                    if (r.rawText != null) put("rawText", r.rawText)
                }
            )
        }
        root.put("readings", readingsArr)

        val treatmentsArr = JSONArray()
        for (t in payload.treatments) {
            treatmentsArr.put(
                JSONObject().apply {
                    put("timestampMs", t.timestampMs)
                    put("insulinUnits", t.insulinUnits)
                    put("carbsGrams", t.carbsGrams)
                    if (t.bloodGlucoseMgdl != null) put("bloodGlucoseMgdl", t.bloodGlucoseMgdl)
                    put("note", t.note)
                    put("eventType", t.eventType)
                }
            )
        }
        root.put("treatments", treatmentsArr)

        val rulesArr = JSONArray()
        for (rule in payload.alertRules) {
            rulesArr.put(
                JSONObject().apply {
                    put("uuid", rule.uuid)
                    put("name", rule.name)
                    put("isHigh", rule.isHigh)
                    put("thresholdMgdl", rule.thresholdMgdl)
                    put("enabled", rule.enabled)
                    put("allDay", rule.allDay)
                    put("startMinutes", rule.startMinutes)
                    put("endMinutes", rule.endMinutes)
                    put("minutesBetween", rule.minutesBetween)
                    put("defaultSnoozeMinutes", rule.defaultSnoozeMinutes)
                    put("overrideSilent", rule.overrideSilent)
                    put("vibrate", rule.vibrate)
                }
            )
        }
        root.put("alertRules", rulesArr)
        return root.toString(2)
    }

    fun decodeFromJson(jsonText: String): BackupPayload {
        val root = JSONObject(jsonText)
        val readings = mutableListOf<GlucoseReading>()
        val readingsArr = root.optJSONArray("readings") ?: JSONArray()
        for (i in 0 until readingsArr.length()) {
            val o = readingsArr.optJSONObject(i) ?: continue
            readings.add(
                GlucoseReading(
                    timestampMs = o.getLong("timestampMs"),
                    mgdl = o.getDouble("mgdl"),
                    slopePerMin = o.optDouble("slopePerMin", 0.0),
                    trend = TrendArrow.fromWireName(o.optString("trendName", "Flat")),
                    source = o.optString("source", "Backup"),
                    rawText = if (o.has("rawText")) o.optString("rawText") else null,
                )
            )
        }

        val treatments = mutableListOf<TreatmentEntry>()
        val treatmentsArr = root.optJSONArray("treatments") ?: JSONArray()
        for (i in 0 until treatmentsArr.length()) {
            val o = treatmentsArr.optJSONObject(i) ?: continue
            treatments.add(
                TreatmentEntry(
                    timestampMs = o.getLong("timestampMs"),
                    insulinUnits = o.optDouble("insulinUnits", 0.0),
                    carbsGrams = o.optDouble("carbsGrams", 0.0),
                    bloodGlucoseMgdl = if (o.has("bloodGlucoseMgdl")) o.optDouble("bloodGlucoseMgdl") else null,
                    note = o.optString("note", ""),
                    eventType = o.optString("eventType", "Meal Bolus"),
                )
            )
        }

        val rules = mutableListOf<AlertRule>()
        val rulesArr = root.optJSONArray("alertRules") ?: JSONArray()
        for (i in 0 until rulesArr.length()) {
            val o = rulesArr.optJSONObject(i) ?: continue
            rules.add(
                AlertRule(
                    uuid = o.getString("uuid"),
                    name = o.getString("name"),
                    isHigh = o.getBoolean("isHigh"),
                    thresholdMgdl = o.getDouble("thresholdMgdl"),
                    enabled = o.optBoolean("enabled", true),
                    allDay = o.optBoolean("allDay", true),
                    startMinutes = o.optInt("startMinutes", 0),
                    endMinutes = o.optInt("endMinutes", 0),
                    minutesBetween = o.optInt("minutesBetween", 10),
                    defaultSnoozeMinutes = o.optInt("defaultSnoozeMinutes", 20),
                    overrideSilent = o.optBoolean("overrideSilent", false),
                    vibrate = o.optBoolean("vibrate", true),
                )
            )
        }
        return BackupPayload(readings = readings, treatments = treatments, alertRules = rules)
    }

    fun encodeReadingsToCsv(readings: List<GlucoseReading>): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp_iso,timestamp_ms,mgdl,mmol,trend,slope_mgdl_per_min,source")
        for (r in readings.sortedByDescending { it.timestampMs }) {
            val iso = Instant.ofEpochMilli(r.timestampMs).toString()
            val mmol = String.format(Locale.US, "%.1f", r.mgdl * GlucoseUnit.MGDL_TO_MMOL)
            val slope = String.format(Locale.US, "%.2f", r.slopePerMin)
            val safeSource = r.source.replace(",", " ")
            sb.appendLine("$iso,${r.timestampMs},${r.mgdl.toInt()},$mmol,${r.trend.wireName},$slope,$safeSource")
        }
        return sb.toString()
    }
}
