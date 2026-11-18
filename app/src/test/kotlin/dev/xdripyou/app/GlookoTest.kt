package dev.xdripyou.app

import dev.xdripyou.app.data.GlookoClient
import dev.xdripyou.app.domain.GlookoExportImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GlookoTest {

    @Test
    fun parseGraphDataJson_extractsCgmInsulinCarbsAndBg() {
        val sampleJson = """
            {
              "units": "mg/dL",
              "series": {
                "cgmNormal": [
                  { "timestamp": "2026-11-18T10:00:00.000Z", "y": 118 },
                  { "timestamp": "2026-11-18T10:05:00.000Z", "y": 124 }
                ],
                "cgmHigh": [
                  { "timestamp": "2026-11-18T10:10:00.000Z", "y": 196 }
                ],
                "bolus": [
                  { "timestamp": "2026-11-18T10:12:00.000Z", "y": 4.5, "carbs": 45, "label": "Normal" }
                ],
                "carbs": [
                  { "timestamp": "2026-11-18T11:30:00.000Z", "y": 18, "name": "Snack" }
                ],
                "bgNormal": [
                  { "timestamp": "2026-11-18T09:50:00.000Z", "y": 115 }
                ]
              }
            }
        """.trimIndent()

        val parsed = GlookoClient.parseGraphDataJson(sampleJson)
        assertEquals(3, parsed.readings.size)
        assertEquals(118.0, parsed.readings[0].mgdl, 0.01)
        assertEquals(196.0, parsed.readings[2].mgdl, 0.01)

        assertEquals(3, parsed.treatments.size)
        val bgCheck = parsed.treatments.first { it.eventType == "BG Check" }
        assertEquals(115.0, bgCheck.bloodGlucoseMgdl ?: 0.0, 0.01)

        val bolus = parsed.treatments.first { it.insulinUnits > 0.0 }
        assertEquals(4.5, bolus.insulinUnits, 0.01)
        assertEquals(45.0, bolus.carbsGrams, 0.01)

        val snack = parsed.treatments.first { it.note.contains("Snack") }
        assertEquals(18.0, snack.carbsGrams, 0.01)
    }

    @Test
    fun parseV2ReadingsAndBolus_convertsMmolWhenFlagged() {
        val readingsJson = """
            {
              "cgmReadings": [
                { "systemTime": "2026-11-18T12:00:00Z", "value": 6.5, "units": "mmol/L", "trend": "FORTY_FIVE_UP" }
              ]
            }
        """.trimIndent()
        val readings = GlookoClient.parseV2ReadingsJson(readingsJson)
        assertEquals(1, readings.size)
        assertTrue(readings[0].mgdl in 116.0..118.0)

        val bolusJson = """
            {
              "normalBoluses": [
                { "displayTime": "2026-11-18T12:05:00Z", "insulinDelivered": 3.2, "carbsInput": 32, "bolusType": "Normal" }
              ]
            }
        """.trimIndent()
        val treatments = GlookoClient.parseV2TreatmentsJson(bolusJson, "bolus")
        assertEquals(1, treatments.size)
        assertEquals(3.2, treatments[0].insulinUnits, 0.01)
        assertEquals(32.0, treatments[0].carbsGrams, 0.01)
    }

    @Test
    fun parseGlookoZipArchive_importsGermanAndEnglishCsvFiles() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("cgm_data_1.csv"))
            val cgmCsv = """
                Name:Test User	Date Range:2026-11-01 - 2026-11-18
                Timestamp,CGM Glucose Value (mg/dl)
                2026-11-18 08:00:00,110
                2026-11-18 08:05:00,116
            """.trimIndent()
            zos.write(cgmCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("bolus_data_1.csv"))
            val bolusCsv = """
                Zeitstempel;Bolus Typ;Abgegebenes Insulin (E);Kohlenhydrate (g)
                18.11.2026 08:10;Normal;5,5;55
            """.trimIndent()
            zos.write(bolusCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("basal_data_1.csv"))
            val basalCsv = """
                Timestamp,Rate (Units/hr)
                2026-11-18 08:00:00,0.95
                2026-11-18 09:00:00,1.05
            """.trimIndent()
            zos.write(basalCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("exercise_data_1.csv"))
            val exerciseCsv = """
                Timestamp,Activity,Duration
                2026-11-18 14:00:00,Laufen,35
            """.trimIndent()
            zos.write(exerciseCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val res = GlookoExportImporter.parseBytes(baos.toByteArray(), "glooko_export.zip").getOrThrow()
        assertEquals(4, res.processedFiles)
        assertEquals(2, res.readings.size)
        assertEquals(110.0, res.readings[0].mgdl, 0.01)
        assertEquals(116.0, res.readings[1].mgdl, 0.01)

        assertEquals(2, res.treatments.size)
        val bolus = res.treatments.first { it.insulinUnits > 0.0 }
        assertEquals(5.5, bolus.insulinUnits, 0.01)
        assertEquals(55.0, bolus.carbsGrams, 0.01)

        val exercise = res.treatments.first { it.eventType == "Exercise" }
        assertTrue(exercise.note.contains("35m"))

        assertNotNull(res.basalHourlyRates)
        assertEquals(24, res.basalHourlyRates!!.size)
    }

    @Test
    fun extractGlookoCode_handlesUserPayloadFormats() {
        assertEquals("abc123", GlookoClient.extractGlookoCode("""{"user":{"glookoCode":"abc123"}}"""))
        assertEquals("xyz789", GlookoClient.extractGlookoCode("""[{"glookoCode":"xyz789"}]"""))
    }

    @Test
    fun signInUrlAndCsrfExtraction_matchLiveGlookoEndpoints() {
        assertEquals(
            "https://de-fr.api.glooko.com/api/v2/users/sign_in",
            GlookoClient.signInApiUrl(dev.xdripyou.app.data.GlookoRegion.EU_DE),
        )
        val htmlMeta = """<html><head><meta name="csrf-token" content="meta-tok-123" /></head></html>"""
        assertEquals("meta-tok-123", GlookoClient.extractCsrfToken(htmlMeta))
        val htmlForm = """<form><input type="hidden" name="authenticity_token" value="form-tok-456" /></form>"""
        assertEquals("form-tok-456", GlookoClient.extractCsrfToken(htmlForm))
    }
}
