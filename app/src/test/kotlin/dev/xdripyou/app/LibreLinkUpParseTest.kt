package dev.xdripyou.app

import dev.xdripyou.app.data.LibreLinkRegion
import dev.xdripyou.app.data.LibreLinkUpClient
import dev.xdripyou.app.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LibreLinkUpParseTest {

    @Test
    fun `maps librelinkup trend codes and hashes account id`() {
        assertEquals(TrendArrow.SINGLE_DOWN, LibreLinkUpClient.mapTrend(1))
        assertEquals(TrendArrow.FLAT, LibreLinkUpClient.mapTrend(3))
        assertEquals(TrendArrow.SINGLE_UP, LibreLinkUpClient.mapTrend(5))
        assertEquals(TrendArrow.NOT_COMPUTABLE, LibreLinkUpClient.mapTrend(99))
        assertEquals(64, LibreLinkUpClient.sha256Hex("user-123").length)
        assertEquals(LibreLinkRegion.DE, LibreLinkRegion.fromWire("de"))
    }

    @Test
    fun `parses librelinkup timestamps`() {
        assertNotNull(LibreLinkUpClient.parseTimestamp("11/18/2026 12:30:15 PM"))
        assertNotNull(LibreLinkUpClient.parseTimestamp("2026-11-18T12:30:15"))
        assertNull(LibreLinkUpClient.parseTimestamp("not-a-date"))
    }
}
