package dev.xdripyou.app.domain

import dev.xdripyou.app.data.WebFollowClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebFollowAndPresetTest {

    @Test
    fun webFollowParsesArrayAndConvertsMmol() {
        val json = """
            [
              {"sgv": 132, "date": 1700000000000, "direction": "Flat"},
              {"sgv": 144, "date": 1700000300000, "direction": "FortyFiveUp"}
            ]
        """.trimIndent()
        val parsed = WebFollowClient.parsePayload(json, "sgv").getOrThrow()
        assertEquals(2, parsed.size)
        assertEquals(132.0, parsed[0].mgdl, 1e-3)
        assertEquals(TrendArrow.FLAT, parsed[0].trend)
        assertEquals(144.0, parsed[1].mgdl, 1e-3)
        assertEquals(TrendArrow.FORTY_FIVE_UP, parsed[1].trend)

        val mmolPayload = """{"bg": 7.0}"""
        val mmolParsed = WebFollowClient.parsePayload(mmolPayload, "bg", nowMs = 1_000_000L).getOrThrow()
        assertEquals(126.1, mmolParsed.first().mgdl, 0.3)
    }

    @Test
    fun insulinPresetAppliesFasterPeakForFiaspAndLyumjev() {
        val now = 10_000_000L
        val treatments = listOf(TreatmentEntry(timestampMs = now - 60 * 60_000L, insulinUnits = 4.0))
        val lyumjevSnap = IobCobCalculator.snapshot(
            treatments = treatments,
            atMs = now,
            settings = AppSettings(insulinPreset = InsulinPreset.ULTRA_RAPID_LYUMJEV.wireValue),
        )
        val novorapidSnap = IobCobCalculator.snapshot(
            treatments = treatments,
            atMs = now,
            settings = AppSettings(insulinPreset = InsulinPreset.RAPID_NOVORAPID.wireValue),
        )
        assertTrue(lyumjevSnap.iobUnits < novorapidSnap.iobUnits)
    }
}
