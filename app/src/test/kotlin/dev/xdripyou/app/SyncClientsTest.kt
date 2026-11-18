package dev.xdripyou.app

import dev.xdripyou.app.data.InfluxDbClient
import dev.xdripyou.app.data.XdripPeerSyncClient
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncClientsTest {

    @Test
    fun `builds valid influx line protocol for glucose and treatments`() {
        val readings = listOf(
            GlucoseReading(
                timestampMs = 1_700_000_000_000L,
                mgdl = 120.0,
                slopePerMin = 1.2,
                trend = TrendArrow.FORTY_FIVE_UP,
                source = "CamAPS FX",
            )
        )
        val treatments = listOf(
            TreatmentEntry(
                timestampMs = 1_700_000_060_000L,
                insulinUnits = 4.5,
                carbsGrams = 45.0,
                eventType = "Meal Bolus",
            )
        )
        val lp = InfluxDbClient.buildLineProtocol(readings, treatments)
        assertTrue(lp.contains("glucose,source=CamAPS\\ FX value_mgdl=120.00"))
        assertTrue(lp.contains("direction=\"FortyFiveUp\""))
        assertTrue(lp.contains("treatment,type=Meal\\ Bolus insulin=4.50,carbs=45.0 1700000060000"))
    }

    @Test
    fun `normalizes xdrip peer sync master address with default port 17580`() {
        assertEquals("http://192.168.1.42:17580", XdripPeerSyncClient.normalizePeerUrl("192.168.1.42"))
        assertEquals("http://192.168.1.42:17580", XdripPeerSyncClient.normalizePeerUrl("http://192.168.1.42/"))
        assertEquals("https://xdrip.local:17581", XdripPeerSyncClient.normalizePeerUrl("https://xdrip.local:17581/"))
        assertEquals("", XdripPeerSyncClient.normalizePeerUrl("   "))
    }
}
