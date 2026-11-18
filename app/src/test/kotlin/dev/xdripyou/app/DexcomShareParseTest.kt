package dev.xdripyou.app

import dev.xdripyou.app.data.DexcomShareClient
import dev.xdripyou.app.domain.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DexcomShareParseTest {

    @Test
    fun `parses dotnet date wrapper into epoch millis`() {
        assertEquals(1700000000000L, DexcomShareClient.parseDotNetTimestamp("/Date(1700000000000)/"))
        assertEquals(1700000000000L, DexcomShareClient.parseDotNetTimestamp("Date(1700000000000)"))
        assertNull(DexcomShareClient.parseDotNetTimestamp("invalid"))
    }

    @Test
    fun `maps numeric and string dexcom share trends`() {
        assertEquals(TrendArrow.DOUBLE_UP, DexcomShareClient.parseShareTrend(1))
        assertEquals(TrendArrow.FLAT, DexcomShareClient.parseShareTrend(4))
        assertEquals(TrendArrow.DOUBLE_DOWN, DexcomShareClient.parseShareTrend(7))
        assertEquals(TrendArrow.FORTY_FIVE_UP, DexcomShareClient.parseShareTrend("FortyFiveUp"))
    }

    @Test
    fun `selects us and international dexcom share endpoints`() {
        assertEquals("https://share2.dexcom.com/ShareWebServices/Services", DexcomShareClient.baseUrl(true))
        assertEquals("https://shareous1.dexcom.com/ShareWebServices/Services", DexcomShareClient.baseUrl(false))
    }
}
