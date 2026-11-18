package dev.xdripyou.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareProtocolsTest {

    @Test
    fun parsesBluetoothGlucoseMeterMgdlAndMmolPackets() {
        // SFLOAT: mantissa = 135, exponent = -3 -> 0.135 kg/L? Wait: 135 mg/dL = 0.00135 kg/L -> mantissa 135, exp -5 (0xB)
        // (-5 & 0x0F) = 0x0B -> high nibble 0xB, 135 = 0x087 -> raw = 0xB087 -> LSB=0x87, MSB=0xB0
        val kgPerLPacket = byteArrayOf(
            0x02, // flags: concentration present, kg/L (bit 2 = 0)
            0x07, 0x00, // seq = 7
            (2026 and 0xFF).toByte(), (2026 shr 8).toByte(), // year 2026
            11, 18, 13, 30, 0, // month 11, day 18, 13:30:00
            0x87.toByte(), 0xB0.toByte(), // 0.00135 kg/L = 135 mg/dL
        )
        val kgParsed = BluetoothGlucoseMeterParser.parseGlucoseMeasurement(kgPerLPacket)
        assertNotNull(kgParsed)
        assertEquals(7, kgParsed!!.sequenceNumber)
        assertEquals(135.0, kgParsed.mgdl, 0.2)
        assertFalse(kgParsed.wasMmolSource)

        // mmol/L mode (bit 2 = 1 -> mol/L): 7.2 mmol/L = 0.0072 mol/L -> mantissa 72 (0x048), exp -4 (0xC) -> 0xC048
        val molPerLPacket = byteArrayOf(
            0x06, // flags: concentration present + mol/L
            0x08, 0x00,
            (2026 and 0xFF).toByte(), (2026 shr 8).toByte(),
            11, 18, 13, 35, 0,
            0x48, 0xC0.toByte(),
        )
        val molParsed = BluetoothGlucoseMeterParser.parseGlucoseMeasurement(molPerLPacket)
        assertNotNull(molParsed)
        assertTrue(molParsed!!.wasMmolSource)
        assertEquals(7.2 * GlucoseUnit.MMOL_TO_MGDL, molParsed.mgdl, 0.5)
    }

    @Test
    fun verifiesLibreSerialDecodeCrc16AndBubbleFramAssembly() {
        val sampleUid = byteArrayOf(
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0xE0.toByte()
        )
        val serial = LibreHardwareParser.decodeSerialNumber(sampleUid)
        assertNotNull(serial)
        assertTrue(serial!!.startsWith("0M"))
        assertEquals(10, serial.length)

        // Build synthetic 344-byte Libre FRAM with valid CRC16 blocks and raw reading = 1020 (120 mg/dL at /8.5)
        val fram = ByteArray(344)
        fram[4] = 0x03 // READY state
        fram[26] = 0x01 // trend index = 1 -> latest slot is (1 + 15) % 16 = 0
        fram[316] = 0xA0.toByte()
        fram[317] = 0x05 // 1440 min (1 day)
        // Slot 0 at offset 28: raw = 1020 = 0x03FC -> [FC, 03]
        fram[28] = 0xFC.toByte()
        fram[29] = 0x03

        fun writeBlockCrc(start: Int, length: Int) {
            var crc = 0xFFFF
            for (i in (start + 2) until (start + length)) {
                val b = fram[i].toInt() and 0xFF
                val tableVal = LibreHardwareParser.computeCrc16(fram, start + 2, length - 2)
                // reuse computeCrc16 directly below
                crc = tableVal
                break
            }
            fram[start] = ((crc ushr 8) and 0xFF).toByte()
            fram[start + 1] = (crc and 0xFF).toByte()
        }
        writeBlockCrc(0, 24)
        writeBlockCrc(24, 296)
        writeBlockCrc(320, 24)

        assertTrue(LibreHardwareParser.verifyFram344(fram))

        // Stream via BubblePacketAssembler in 16-byte chunks
        val assembler = LibreHardwareParser.BubblePacketAssembler()
        assembler.onPacket(byteArrayOf(0x80.toByte(), 0x00, 0x02, 0x06, 88))
        assertEquals(88, assembler.lastBatteryPercent)

        var completedFram: ByteArray? = null
        var pos = 0
        while (pos < 344) {
            val end = minOf(pos + 16, 344)
            val chunk = ByteArray(4 + (end - pos))
            chunk[0] = 0x82.toByte()
            System.arraycopy(fram, pos, chunk, 4, end - pos)
            val res = assembler.onPacket(chunk)
            if (res.completedFram344 != null) completedFram = res.completedFram344
            pos = end
        }
        assertNotNull(completedFram)
        val parsed = LibreHardwareParser.parseFram344(completedFram!!)
        assertNotNull(parsed)
        assertEquals(120.0, parsed!!.estimatedMgdl, 0.5)
        assertEquals(1440, parsed.sensorMinutes)
    }

    @Test
    fun parsesDexcomBleGlucoseRxAndTransmitterId() {
        assertTrue(DexcomBleParser.isValidTransmitterId("8G12AB"))
        assertFalse(DexcomBleParser.isValidTransmitterId("123"))

        // Opcode 0x4E, glucose = 152 mg/dL (0x0098), trend = +15 (+1.5 mg/dL/min)
        val packet = ByteArray(16)
        packet[0] = 0x4E
        packet[1] = 0x00
        packet[10] = 0x98.toByte()
        packet[11] = 0x00
        packet[12] = 0x06
        packet[13] = 15

        val parsed = DexcomBleParser.parseGlucoseRx(packet)
        assertNotNull(parsed)
        assertEquals(152.0, parsed!!.glucoseMgdl, 1e-4)
        assertEquals(1.5, parsed.trendRateMgdlPerMin, 1e-4)
        assertFalse(parsed.isDisplayOnly)
    }
}
