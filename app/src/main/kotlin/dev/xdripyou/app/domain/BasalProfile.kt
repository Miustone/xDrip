package dev.xdripyou.app.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.round

/**
 * Port of xDrip's `BasalProfile` (24 hourly basal rates in U/h across 00:00..23:59).
 */
data class BasalSchedule(
    val hourlyRates: List<Double> = List(24) { 0.8 },
) {
    init {
        require(hourlyRates.size == 24) { "BasalSchedule requires 24 hourly rates" }
    }

    val totalDailyUnits: Double
        get() = hourlyRates.sum()

    fun rateAt(timestampMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Double {
        val hour = Instant.ofEpochMilli(timestampMs).atZone(zoneId).hour.coerceIn(0, 23)
        return hourlyRates[hour]
    }

    fun withRate(hour: Int, rateUnitsPerHour: Double): BasalSchedule {
        if (hour !in 0..23) return this
        val clamped = round(rateUnitsPerHour.coerceIn(0.0, 10.0) * 100.0) / 100.0
        return BasalSchedule(hourlyRates.mapIndexed { idx, old -> if (idx == hour) clamped else old })
    }

    fun encodeWire(): String = hourlyRates.joinToString(",") { "%.2f".format(java.util.Locale.US, it) }

    companion object {
        fun decodeWire(raw: String?): BasalSchedule {
            if (raw.isNullOrBlank()) return BasalSchedule()
            val parsed = raw.split(',')
                .mapNotNull { it.trim().toDoubleOrNull()?.coerceIn(0.0, 10.0) }
            return if (parsed.size == 24) BasalSchedule(parsed) else BasalSchedule()
        }
    }
}

data class ExternalPumpStatus(
    val timestampMs: Long = 0L,
    val iobUnits: Double? = null,
    val cobGrams: Double? = null,
    val reservoirUnits: Double? = null,
    val pumpBatteryPercent: Int? = null,
    val uploaderBatteryPercent: Int? = null,
)
