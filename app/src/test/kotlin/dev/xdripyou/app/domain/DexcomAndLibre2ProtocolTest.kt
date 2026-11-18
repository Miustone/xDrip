package dev.xdripyou.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexcomAndLibre2ProtocolTest {

    @Test
    fun calculatesDeterministicAesChallengeHashAndBuildsOb1Messages() {
        val key = DexcomOb1Protocol.deriveCryptKey("8G12AB")
        assertNotNull(key)
        assertEquals(16, key!!.size)
        assertEquals("008G12AB008G12AB", String(key, Charsets.UTF_8))

        val challenge = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val hash1 = DexcomOb1Protocol.calculateChallengeHash(challenge, "8G12AB")
        val hash2 = DexcomOb1Protocol.calculateChallengeHash(challenge, "8g12ab")
        assertNotNull(hash1)
        assertEquals(8, hash1!!.size)
        assertTrue(hash1.contentEquals(hash2))

        val g7Req = DexcomOb1Protocol.buildEGlucoseTx(shortG7Mode = true)
        assertEquals(1, g7Req.size)
        assertEquals(0x4E.toByte(), g7Req[0])

        val g6Req = DexcomOb1Protocol.buildEGlucoseTx(shortG7Mode = false)
        assertEquals(3, g6Req.size)
        assertTrue(DexcomOb1Protocol.checkCrc16(g6Req))
    }

    @Test
    fun decodesDexcomG7EGlucoseRxMessageAnd9ByteBackfillFrames() {
        val nowMs = 1_700_000_000_000L
        // 19-byte Dexcom G7 EGlucoseRxMessage (opcode 0x4E)
        val buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x4E.toByte()) // opcode
        buf.put(0x00) // status_raw
        buf.putInt(3600) // clock seconds
        buf.putShort(42) // sequence
        buf.putShort(0) // reserved
        buf.putShort(15) // age = 15s
        buf.putShort(128) // glucose = 128 mg/dL, not displayOnly
        buf.put(6) // state = OK (6)
        buf.put((-8).toByte()) // trend = -0.8 mg/dL/min
        buf.putShort(121) // predicted glucose = 121 mg/dL

        val parsed = DexcomOb1Protocol.parseGlucosePacket(buf.array(), nowMs = nowMs)
        assertNotNull(parsed)
        assertEquals("Dexcom G7", parsed!!.protocol)
        assertEquals(42, parsed.sequence)
        assertEquals(15, parsed.sensorAgeSeconds)
        assertEquals(nowMs - 15_000L, parsed.timestampMs)
        assertEquals(128.0, parsed.glucoseMgdl, 1e-4)
        assertEquals(121, parsed.predictedGlucoseMgdl)
        assertEquals(-0.8, parsed.trendMgdlPerMin!!, 1e-4)
        assertEquals(DexcomCalibrationState.OK, parsed.calibrationState)
        assertFalse(parsed.isDisplayOnly)

        // G7 9-byte Backfill Frame
        val bfBuf = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        bfBuf.putInt(3300) // dexTime
        bfBuf.putShort(134) // bg
        bfBuf.put(6) // state OK
        bfBuf.put(0) // reserved
        bfBuf.put(5.toByte()) // trend +0.5

        val assembler = DexcomOb1Protocol.DexcomBackfillAssembler()
        assembler.push(bfBuf.array())
        val items = assembler.decodeAll()
        assertEquals(1, items.size)
        assertEquals(134.0, items.first().glucoseMgdl, 1e-4)
        assertEquals(0.5, items.first().trendMgdlPerMin!!, 1e-4)
    }

    @Test
    fun assembles46ByteLibre2BleFramesAndParsesOopResults() {
        val assembler = LibreHardwareParser.Libre2BleAssembler()
        val part1 = ByteArray(20) { 0x11 }
        val part2 = ByteArray(20) { 0x22 }
        val part3 = ByteArray(6) { 0x33 }

        assertEquals(null, assembler.onPacket(part1, nowMs = 1_000L))
        assertEquals(null, assembler.onPacket(part2, nowMs = 1_200L))
        val full = assembler.onPacket(part3, nowMs = 1_400L)
        assertNotNull(full)
        assertEquals(46, full!!.size)

        val oopJson = """{"timestamp":1700000000000,"oOPResultsArray":[{"timestamp":1700000000000,"currentBg":142.0,"currentTime":1200,"historicBg":[{"time":1185,"quality":0,"bg":138.0}]}]}"""
        val oopList = LibreHardwareParser.parseOopResultsJson(oopJson)
        assertEquals(2, oopList.size)
        assertEquals(142.0, oopList[0].mgdl, 1e-4)
        assertEquals(138.0, oopList[1].mgdl, 1e-4)
    }
}
