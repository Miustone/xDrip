package dev.xdripyou.app.domain

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.pow

/**
 * Pure-Kotlin implementations of legacy xDrip hardware wire protocols for unit-testable decoding:
 * - Bluetooth SIG Glucose Profile (0x1808 / 0x2A18 SFLOAT16)
 * - Freestyle Libre NFC FRAM (344 bytes) + Bubble BLE Bridge framing + ISO-15693 serial decoder
 * - Dexcom G5/G6/ONE BLE GATT GlucoseRx message decoder
 */
object BluetoothGlucoseMeterParser {

    const val SERVICE_GLUCOSE_UUID = "00001808-0000-1000-8000-00805f9b34fb"
    const val CHAR_GLUCOSE_MEASUREMENT_UUID = "00002a18-0000-1000-8000-00805f9b34fb"
    const val CHAR_RECORD_ACCESS_CONTROL_POINT_UUID = "00002a52-0000-1000-8000-00805f9b34fb"

    data class MeterReading(
        val sequenceNumber: Int,
        val timestampMs: Long,
        val mgdl: Double,
        val wasMmolSource: Boolean,
    )

    /**
     * Builds RACP "Report All Stored Records" command (`0x01, 0x01`) matching `BluetoothGlucoseMeter.java`.
     */
    fun buildReportAllRecordsCommand(): ByteArray = byteArrayOf(0x01, 0x01)

    /**
     * Decodes IEEE-11073 16-bit SFLOAT from two little-endian bytes (`b0 = LSB`, `b1 = MSB`).
     */
    fun decodeSfloat16(lsb: Byte, msb: Byte): Double {
        val raw = ((msb.toInt() and 0xFF) shl 8) or (lsb.toInt() and 0xFF)
        var mantissa = raw and 0x0FFF
        if (mantissa >= 0x0800) mantissa -= 0x1000 // sign-extend 12-bit
        var exponent = (raw shr 12) and 0x0F
        if (exponent >= 0x08) exponent -= 0x10 // sign-extend 4-bit
        // Special IEEE 11073 NaN/NRes/Infinity markers
        if (raw == 0x07FF || raw == 0x0800 || raw == 0x0801 || raw == 0x07FE || raw == 0x0802) {
            return Double.NaN
        }
        return mantissa * 10.0.pow(exponent.toDouble())
    }

    fun parseGlucoseMeasurement(
        packet: ByteArray,
        fallbackTimeMs: Long = System.currentTimeMillis(),
    ): MeterReading? {
        if (packet.size < 10) return null
        val flags = packet[0].toInt() and 0xFF
        val timeOffsetPresent = (flags and 0x01) != 0
        val concentrationPresent = (flags and 0x02) != 0
        val isMolPerL = (flags and 0x04) != 0

        if (!concentrationPresent) return null

        val seq = ((packet[2].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        val year = ((packet[4].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        val month = (packet[5].toInt() and 0xFF)
        val day = (packet[6].toInt() and 0xFF)
        val hour = (packet[7].toInt() and 0xFF)
        val minute = (packet[8].toInt() and 0xFF)
        val second = (packet[9].toInt() and 0xFF)

        var offset = 10
        var offsetMinutes = 0
        if (timeOffsetPresent) {
            if (packet.size < offset + 2) return null
            offsetMinutes = ((packet[offset + 1].toInt() shl 8) or (packet[offset].toInt() and 0xFF)).toShort().toInt()
            offset += 2
        }
        if (packet.size < offset + 2) return null

        val sfloatVal = decodeSfloat16(packet[offset], packet[offset + 1])
        if (sfloatVal.isNaN()) return null

        val mgdl = if (isMolPerL) {
            // mol/L -> mmol/L (* 1000) -> mg/dL
            (sfloatVal * 1000.0) * GlucoseUnit.MMOL_TO_MGDL
        } else {
            // kg/L -> mg/dL (* 100_000)
            sfloatVal * 100_000.0
        }
        if (mgdl !in 20.0..700.0) return null

        val timestampMs = if (year in 2000..2100 && month in 1..12 && day in 1..31) {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                clear()
                set(year, month - 1, day, hour.coerceIn(0, 23), minute.coerceIn(0, 59), second.coerceIn(0, 59))
                add(Calendar.MINUTE, offsetMinutes)
            }
            cal.timeInMillis
        } else {
            fallbackTimeMs
        }

        return MeterReading(
            sequenceNumber = seq,
            timestampMs = timestampMs,
            mgdl = mgdl,
            wasMmolSource = isMolPerL,
        )
    }
}

object LibreHardwareParser {

    private const val SERIAL_LOOKUP = "0123456789ACDEFGHJKLMNPQRTUVWXYZ"

    private val crc16Table = intArrayOf(
        0x0000, 0x1189, 0x2312, 0x329B, 0x4624, 0x57AD, 0x6536, 0x74BF,
        0x8C48, 0x9DC1, 0xAF5A, 0xBED3, 0xCA6C, 0xDBE5, 0xE97E, 0xF8F7,
        0x1081, 0x0108, 0x3393, 0x221A, 0x56A5, 0x472C, 0x75B7, 0x643E,
        0x9CC9, 0x8D40, 0xBFDB, 0xAE52, 0xDAED, 0xCB64, 0xF9FF, 0xE876,
        0x2102, 0x308B, 0x0210, 0x1399, 0x6726, 0x76AF, 0x4434, 0x55BD,
        0xAD4A, 0xBCC3, 0x8E58, 0x9FD1, 0xEB6E, 0xFAE7, 0xC87C, 0xD9F5,
        0x3183, 0x200A, 0x1291, 0x0318, 0x77A7, 0x662E, 0x54B5, 0x453C,
        0xBDCB, 0xAC42, 0x9ED9, 0x8F50, 0xFBEF, 0xEA66, 0xD8FD, 0xC974,
        0x4204, 0x538D, 0x6116, 0x709F, 0x0420, 0x15A9, 0x2732, 0x36BB,
        0xCE4C, 0xDFC5, 0xED5E, 0xFCD7, 0x8868, 0x99E1, 0xAB7A, 0xBAF3,
        0x5285, 0x430C, 0x7197, 0x601E, 0x14A1, 0x0528, 0x37B3, 0x263A,
        0xDECD, 0xCF44, 0xFDDF, 0xEC56, 0x98E9, 0x8960, 0xBBFB, 0xAA72,
        0x6306, 0x728F, 0x4014, 0x519D, 0x2522, 0x34AB, 0x0630, 0x17B9,
        0xEF4E, 0xFEC7, 0xCC5C, 0xDDD5, 0xA96A, 0xB8E3, 0x8A78, 0x9BF1,
        0x7387, 0x620E, 0x5095, 0x411C, 0x35A3, 0x242A, 0x16B1, 0x0738,
        0xFFCF, 0xEE46, 0xDCDD, 0xCD54, 0xB9EB, 0xA862, 0x9AF9, 0x8B70,
        0x8408, 0x9581, 0xA71A, 0xB693, 0xC22C, 0xD3A5, 0xE13E, 0xF0B7,
        0x0840, 0x19C9, 0x2B52, 0x3ADB, 0x4E64, 0x5FED, 0x6D76, 0x7CFF,
        0x9489, 0x8500, 0xB79B, 0xA612, 0xD2AD, 0xC324, 0xF1BF, 0xE036,
        0x18C1, 0x0948, 0x3BD3, 0x2A5A, 0x5EE5, 0x4F6C, 0x7DF7, 0x6C7E,
        0xA50A, 0xB483, 0x8618, 0x9791, 0xE32E, 0xF2A7, 0xC03C, 0xD1B5,
        0x2942, 0x38CB, 0x0A50, 0x1BD9, 0x6F66, 0x7EEF, 0x4C74, 0x5DFD,
        0xB58B, 0xA402, 0x9699, 0x8710, 0xF3AF, 0xE226, 0xD0BD, 0xC134,
        0x39C3, 0x284A, 0x1AD1, 0x0B58, 0x7FE7, 0x6E6E, 0x5CF5, 0x4D7C,
        0xC60C, 0xD785, 0xE51E, 0xF497, 0x8028, 0x91A1, 0xA33A, 0xB2B3,
        0x4A44, 0x5BCD, 0x6956, 0x78DF, 0x0C60, 0x1DE9, 0x2F72, 0x3EFB,
        0xD68D, 0xC704, 0xF59F, 0xE416, 0x90A9, 0x8120, 0xB3BB, 0xA232,
        0x5AC5, 0x4B4C, 0x79D7, 0x685E, 0x1CE1, 0x0D68, 0x3FF3, 0x2E7A,
        0xE70E, 0xF687, 0xC41C, 0xD595, 0xA12A, 0xB0A3, 0x8238, 0x93B1,
        0x6B46, 0x7ACF, 0x4854, 0x59DD, 0x2D62, 0x3CEB, 0x0E70, 0x1FF9,
        0xF78F, 0xE606, 0xD49D, 0xC514, 0xB1AB, 0xA022, 0x92B9, 0x8330,
        0x7BC7, 0x6A4E, 0x58D5, 0x495C, 0x3DE3, 0x2C6A, 0x1EF1, 0x0F78,
    )

    fun computeCrc16(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFF
        for (i in offset until (offset + length)) {
            val b = data[i].toInt() and 0xFF
            crc = (crc ushr 8) xor crc16Table[(crc xor b) and 0xFF]
        }
        return ((crc and 0xFF) shl 8) or ((crc ushr 8) and 0xFF)
    }

    fun checkCrc16(data: ByteArray): Boolean {
        if (data.size < 4) return false
        val expected = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
        var crc = 0xFFFF
        for (i in 2 until data.size) {
            val b = data[i].toInt() and 0xFF
            crc = (crc ushr 8) xor crc16Table[(crc xor b) and 0xFF]
        }
        return (crc and 0xFFFF) == expected
    }

    fun verifyFram344(fram: ByteArray): Boolean {
        if (fram.size < 344) return false
        val header = fram.copyOfRange(0, 24)
        val body = fram.copyOfRange(24, 320)
        val footer = fram.copyOfRange(320, 344)
        return checkCrc16(header) && checkCrc16(body) && checkCrc16(footer)
    }

    /**
     * Port of xDrip `LibreUtils.decodeSerialNumberKey(byte[] uid)`:
     * Converts an 8-byte ISO-15693 tag UID into the 11-character Freestyle Libre serial string.
     */
    fun decodeSerialNumber(uid: ByteArray): String? {
        if (uid.size != 8) return null
        val bytes = ByteArray(6) { idx -> uid[5 - idx] }
        val bits = StringBuilder(48)
        for (b in bytes) {
            bits.append(String.format("%8s", Integer.toBinaryString(b.toInt() and 0xFF)).replace(' ', '0'))
        }
        val fiveBits = IntArray(8)
        for (i in 0 until 8) {
            val chunk = bits.substring(i * 5, i * 5 + 5)
            fiveBits[i] = chunk.toInt(2)
        }
        return buildString {
            append("0M")
            for (v in fiveBits) {
                append(SERIAL_LOOKUP.getOrElse(v) { '0' })
            }
        }
    }

    data class LibreFramReading(
        val sensorMinutes: Int,
        val sensorState: Int,
        val currentRaw: Double,
        val estimatedMgdl: Double,
        val trendRawHistory: List<Double>,
    )

    /**
     * Extracts current glucose & sensor age from standard 344-byte unencrypted Libre FRAM.
     */
    fun parseFram344(
        fram: ByteArray,
        slopeIntercept: SlopeIntercept = SlopeIntercept(),
    ): LibreFramReading? {
        if (fram.size < 344) return null
        val sensorState = fram[4].toInt() and 0xFF
        val trendIdx = (fram[26].toInt() and 0xFF) % 16
        val sensorMinutes = ((fram[317].toInt() and 0xFF) shl 8) or (fram[316].toInt() and 0xFF)

        val trendValues = mutableListOf<Double>()
        for (i in 0 until 16) {
            val slot = (trendIdx + i) % 16
            val pos = 28 + slot * 6
            val raw = (((fram[pos + 1].toInt() and 0x1F) shl 8) or (fram[pos].toInt() and 0xFF)).toDouble()
            if (raw > 0.0) trendValues += raw
        }
        val currentSlot = (trendIdx + 15) % 16
        val currentPos = 28 + currentSlot * 6
        val currentRaw = (((fram[currentPos + 1].toInt() and 0x1F) shl 8) or (fram[currentPos].toInt() and 0xFF)).toDouble()
        if (currentRaw <= 0.0) return null

        val factoryEstimate = (currentRaw / 8.5).coerceIn(20.0, 600.0)
        val estimatedMgdl = if (slopeIntercept.calibrationCount > 0) {
            slopeIntercept.rawToBg(currentRaw / 8.5)
        } else {
            factoryEstimate
        }

        return LibreFramReading(
            sensorMinutes = sensorMinutes,
            sensorState = sensorState,
            currentRaw = currentRaw,
            estimatedMgdl = estimatedMgdl,
            trendRawHistory = trendValues,
        )
    }

    data class BubbleBridgeStatus(
        val batteryPercent: Int? = null,
        val firmwareVersion: String? = null,
        val completedFram344: ByteArray? = null,
    )

    /**
     * Port of xDrip `Bubble.java` multi-packet BLE bridge assembler (`0x80` status, `0x82` 20B FRAM chunks).
     */
    class BubblePacketAssembler {
        private val buffer = ByteArray(360)
        private var writeIndex = 0
        var lastBatteryPercent: Int? = null
            private set

        fun onPacket(packet: ByteArray): BubbleBridgeStatus {
            if (packet.isEmpty()) return BubbleBridgeStatus()
            val header = packet[0].toInt() and 0xFF
            return when (header) {
                0x80 -> { // hardware / firmware / battery
                    val battery = if (packet.size > 4) (packet[4].toInt() and 0xFF).coerceIn(0, 100) else null
                    val fw = if (packet.size >= 4) "${packet[2].toInt() and 0xFF}.${packet[3].toInt() and 0xFF}" else null
                    lastBatteryPercent = battery
                    writeIndex = 0
                    BubbleBridgeStatus(batteryPercent = battery, firmwareVersion = fw)
                }
                0x82 -> { // FRAM chunk (bytes 4..19 -> up to 16 or 20 bytes depending on proto)
                    val payloadStart = 4
                    val payloadLen = (packet.size - payloadStart).coerceAtLeast(0)
                    val copyLen = minOf(payloadLen, buffer.size - writeIndex)
                    if (copyLen > 0) {
                        System.arraycopy(packet, payloadStart, buffer, writeIndex, copyLen)
                        writeIndex += copyLen
                    }
                    if (writeIndex >= 344) {
                        val completed = buffer.copyOfRange(0, 344)
                        writeIndex = 0
                        BubbleBridgeStatus(batteryPercent = lastBatteryPercent, completedFram344 = completed)
                    } else {
                        BubbleBridgeStatus(batteryPercent = lastBatteryPercent)
                    }
                }
                else -> BubbleBridgeStatus(batteryPercent = lastBatteryPercent)
            }
        }

        fun buildRequestDataCommand(): ByteArray = byteArrayOf(0x00, 0x00, 0x05)
    }
}

object DexcomBleParser {

    const val SERVICE_G6_UUID = "f8083532-849e-531c-c594-30f1f86a4ea5"
    const val CHAR_COMMUNICATION_UUID = "f8083533-849e-531c-c594-30f1f86a4ea5"
    const val CHAR_CONTROL_UUID = "f8083534-849e-531c-c594-30f1f86a4ea5"
    const val CHAR_AUTHENTICATION_UUID = "f8083535-849e-531c-c594-30f1f86a4ea5"

    data class DexcomBleReading(
        val status: Int,
        val glucoseMgdl: Double,
        val stateByte: Int,
        val trendRateMgdlPerMin: Double,
        val isDisplayOnly: Boolean,
    )

    fun isValidTransmitterId(id: String): Boolean {
        val clean = id.trim().uppercase()
        return clean.length == 6 && clean.all { it in '0'..'9' || it in 'A'..'Z' }
    }

    /**
     * Port of xDrip `GlucoseRxMessage.java` (opcode `0x31` / `0x4E` 14-16 byte response packet).
     */
    fun parseGlucoseRx(packet: ByteArray): DexcomBleReading? {
        if (packet.size < 14) return null
        val opcode = packet[0].toInt() and 0xFF
        if (opcode != 0x31 && opcode != 0x4E) return null
        val status = packet[1].toInt() and 0xFF
        val glucoseRaw = ((packet[11].toInt() and 0xFF) shl 8) or (packet[10].toInt() and 0xFF)
        val displayOnly = (glucoseRaw and 0xF000) != 0
        val glucoseMgdl = (glucoseRaw and 0x0FFF).toDouble()
        if (glucoseMgdl !in 20.0..600.0) return null
        val state = packet[12].toInt() and 0xFF
        val trendSigned = packet[13].toInt() // signed byte * 0.1 mg/dL/min
        val trendRate = trendSigned / 10.0

        return DexcomBleReading(
            status = status,
            glucoseMgdl = glucoseMgdl,
            stateByte = state,
            trendRateMgdlPerMin = trendRate,
            isDisplayOnly = displayOnly,
        )
    }
}
