package dev.xdripyou.app

import dev.xdripyou.app.data.NightscoutClient
import org.junit.Assert.assertEquals
import org.junit.Test

class NightscoutFormatTest {

    @Test
    fun `normalizes nightscout base url`() {
        assertEquals("https://my.ns.org", NightscoutClient.normalizeBaseUrl("my.ns.org/"))
        assertEquals("https://my.ns.org", NightscoutClient.normalizeBaseUrl(" https://my.ns.org/// "))
        assertEquals("http://localhost:1337", NightscoutClient.normalizeBaseUrl("http://localhost:1337/"))
    }

    @Test
    fun `hashes api secret with sha1 hex`() {
        // Known SHA-1 of "mysecret1234"
        assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", NightscoutClient.sha1Hex("test"))
    }
}
