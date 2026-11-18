package dev.xdripyou.app

import dev.xdripyou.app.domain.CamApsParser
import dev.xdripyou.app.domain.SourceUnitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CamApsParserTest {

    @Test
    fun `parses mgdl notification with unicode arrows and nbsp`() {
        val result = CamApsParser.tryExtractGlucose(
            texts = listOf("CamAPS FX", "118\u00A0mg/dL \u2192\u2060"),
            unitMode = SourceUnitMode.AUTO,
        )
        assertNotNull(result)
        assertEquals(118.0, result!!.mgdl, 0.01)
        assertEquals("mg/dL", result.detectedUnit)
    }

    @Test
    fun `parses mmol notification with german comma`() {
        val result = CamApsParser.tryExtractGlucose(
            texts = listOf("5,8 mmol/L ↗"),
            unitMode = SourceUnitMode.AUTO,
        )
        assertNotNull(result)
        assertEquals(105.0, result!!.mgdl, 0.01)
        assertEquals("mmol/L", result.detectedUnit)
    }

    @Test
    fun `parses plain integer when forced mgdl`() {
        val result = CamApsParser.tryExtractGlucose(
            texts = listOf("≥ 72"),
            unitMode = SourceUnitMode.MGDL,
        )
        assertNotNull(result)
        assertEquals(72.0, result!!.mgdl, 0.01)
    }

    @Test
    fun `rejects out of bounds readings`() {
        assertNull(CamApsParser.tryExtractGlucose(listOf("39 mg/dL"), SourceUnitMode.AUTO))
        assertNull(CamApsParser.tryExtractGlucose(listOf("406 mg/dL"), SourceUnitMode.AUTO))
    }

    @Test
    fun `rejects ambiguous conflicting glucose numbers`() {
        val result = CamApsParser.tryExtractGlucose(
            texts = listOf("95 mg/dL", "140 mg/dL"),
            unitMode = SourceUnitMode.AUTO,
        )
        assertNull(result)
    }
}
