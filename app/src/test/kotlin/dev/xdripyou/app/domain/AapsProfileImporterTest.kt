package dev.xdripyou.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AapsProfileImporterTest {

    @Test
    fun parsesStandardAapsProfileJsonCorrectly() {
        val json = """
            {
              "units": "mg/dl",
              "dia": 5.0,
              "carbratio": [{"time": "00:00", "value": 12.0}],
              "sens": [{"time": "00:00", "value": 45.0}],
              "basal": [
                {"time": "00:00", "timeAsMinutes": 0, "value": 0.65},
                {"time": "06:00", "value": 1.15},
                {"time": "14:00", "timeAsMinutes": 840, "value": 0.90}
              ]
            }
        """.trimIndent()

        val result = AapsProfileImporter.parseProfileJson(json)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(300, profile.insulinDurationMinutes)
        assertEquals(12.0, profile.carbRatioGramsPerUnit!!, 1e-4)
        assertEquals(45.0, profile.isfMgdlPerUnit!!, 1e-4)
        assertEquals(3, profile.basalBlocks.size)
        assertEquals(0, profile.basalBlocks[0].startMinuteOfDay)
        assertEquals(0.65, profile.basalBlocks[0].unitsPerHour, 1e-4)
        assertEquals(360, profile.basalBlocks[1].startMinuteOfDay)
        assertEquals(1.15, profile.basalBlocks[1].unitsPerHour, 1e-4)
    }

    @Test
    fun convertsMmolIsfToMgdlWhenUnitsAreMmol() {
        val json = """
            {
              "units": "mmol/L",
              "dia": 4.0,
              "sens": [{"time": "00:00", "value": 2.5}],
              "basal": [{"time": "00:00", "value": 0.8}]
            }
        """.trimIndent()
        val profile = AapsProfileImporter.parseProfileJson(json).getOrThrow()
        assertTrue(profile.isMmolUnits)
        assertEquals(2.5 * GlucoseUnit.MMOL_TO_MGDL, profile.isfMgdlPerUnit!!, 0.2)
    }
}
