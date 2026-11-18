package dev.xdripyou.app

import dev.xdripyou.app.domain.BasalSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BasalScheduleTest {

    @Test
    fun `encodes decodes and updates hourly basal rates`() {
        val schedule = BasalSchedule()
            .withRate(8, 1.25)
            .withRate(22, 0.55)
        val decoded = BasalSchedule.decodeWire(schedule.encodeWire())
        assertEquals(1.25, decoded.hourlyRates[8], 1e-4)
        assertEquals(0.55, decoded.hourlyRates[22], 1e-4)
        assertEquals(22 * 0.8 + 1.25 + 0.55, decoded.totalDailyUnits, 1e-3)

        val zone = ZoneOffset.UTC
        val ts08 = ZonedDateTime.of(2026, 11, 18, 8, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(1.25, decoded.rateAt(ts08, zone), 1e-4)
    }
}
