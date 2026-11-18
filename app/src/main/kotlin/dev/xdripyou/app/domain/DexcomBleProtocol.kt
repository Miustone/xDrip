package dev.xdripyou.app.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

enum class DexcomCalibrationState(val code: Int, val labelDe: String, val isUsable: Boolean) {
    UNKNOWN(0, "Unbekannt", false),
    STOPPED(1, "Gestoppt", false),
    WARMING_UP(2, "Aufwärmphase", false),
    EXCESS_NOISE(3, "Zu viel Rauschen", false),
    NEEDS_FIRST_CAL(4, "Erste Kalibrierung nötig", false),
    NEEDS_SECOND_CAL(5, "Zweite Kalibrierung nötig", false),
    OK(6, "OK", true),
    NEEDS_CALIBRATION(7, "Kalibrierung nötig", true),
    INSUFFICIENT_CAL(14, "Unzureichende Kalibrierung", true),
    ENDED(15, "Beendet", false),
    ERRORS(18, "Sensorfehler", false),
    SENSOR_EXPIRED(24, "Sensor abgelaufen", false);

    companion object {
        fun fromCode(code: Int): DexcomCalibrationState =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * Pure-Kotlin port of xDrip's Dexcom G5 / G6 / ONE / G7 / ONE+ OB1 & BlueTails BLE protocol
 * (`BluetoothServices`, `FastCRC16`, `Ob1G5StateMachine`, `EGlucoseRxMessage`, `BackFillStream`).
 */
object DexcomOb1Protocol {

    const val SERVICE_CGM_UUID = "f8083532-849e-531c-c594-30f1f86a4ea5"
    const val SERVICE_B_UUID = "f8084532-849e-531c-c594-30f1f86a4ea5"
    const val CHAR_COMMUNICATION_UUID = "f8083533-849e-531c-c594-30f1f86a4ea5"
    const val CHAR_CONTROL_UUID = "f8083534-849e-531c-c594-30f1f86a4ea5"
    const val CHAR_AUTHENTICATION_UUID = "f8083535-849e-531c-c594-30f1f86a4ea5"
    const val CHAR_BACKFILL_UUID = "f8083536-849e-531c-c594-30f1f86a4ea5"
    const val CHAR_EXTRA_DATA_UUID = "f8083538-849e-531c-c594-30f1f86a4ea5"

    private val crcTable = intArrayOf(
        0, 4129, 8258, 12387, 16516, 20645, 24774, 28903, 33032, 37161, 41290, 45419, 49548, 53677, 57806, 61935,
        4657, 528, 12915, 8786, 21173, 17044, 29431, 25302, 37689, 33560, 45947, 41818, 54205, 50076, 62463, 58334,
        9314, 13379, 1056, 5121, 25830, 29895, 17572, 21637, 42346, 46411, 34088, 38153, 58862, 62927, 50604, 54669,
        13907, 9842, 5649, 1584, 30423, 26358, 22165, 18100, 46939, 42874, 38681, 34616, 63455, 59390, 55197, 51132,
        18628, 22757, 26758, 30887, 2112, 6241, 10242, 14371, 51660, 55789, 59790, 63919, 35144, 39273, 43274, 47403,
        23285, 19156, 31415, 27286, 6769, 2640, 14899, 10770, 56317, 52188, 64447, 60318, 39801, 35672, 47931, 43802,
        27814, 31879, 19684, 23749, 11298, 15363, 3168, 7233, 60846, 64911, 52716, 56781, 44330, 48395, 36200, 40265,
        32407, 28342, 24277, 20212, 15891, 11826, 7761, 3696, 65439, 61374, 57309, 53244, 48923, 44858, 40793, 36728,
        37256, 33193, 45514, 41451, 53516, 49453, 61774, 57711, 4224, 161, 12482, 8419, 20484, 16421, 28742, 24679,
        33721, 37784, 41979, 46042, 49981, 54044, 58239, 62302, 689, 4752, 8947, 13010, 16949, 21012, 25207, 29270,
        46570, 42443, 38312, 34185, 62830, 58703, 54572, 50445, 13538, 9411, 5280, 1153, 29798, 25671, 21540, 17413,
        42971, 47098, 34713, 38840, 59231, 63358, 50973, 55100, 9939, 14066, 1681, 5808, 26199, 30326, 17941, 22068,
        55628, 51565, 63758, 59695, 39368, 35305, 47498, 43435, 22596, 18533, 30726, 26663, 6336, 2273, 14466, 10403,
        52093, 56156, 60223, 64286, 35833, 39896, 43963, 48026, 19061, 23124, 27191, 31254, 2801, 6864, 10931, 14994,
        64814, 60687, 56684, 52557, 48554, 44427, 40424, 36297, 31782, 27655, 23652, 19525, 15522, 11395, 7392, 3265,
        61215, 65342, 53085, 57212, 44955, 49082, 36825, 40952, 28183, 32310, 20053, 24180, 11923, 16050, 3793, 7920,
    )

    fun calculateCrc16(bytes: ByteArray, lengthWithoutCrc: Int = bytes.size): ByteArray {
        var crc = 0
        for (i in 0 until lengthWithoutCrc) {
            val idx = ((crc ushr 8) xor (bytes[i].toInt() and 0xFF)) and 0xFF
            crc = (crcTable[idx] xor (crc shl 8)) and 0xFFFF
        }
        return byteArrayOf((crc and 0xFF).toByte(), ((crc ushr 8) and 0xFF).toByte())
    }

    fun checkCrc16(packet: ByteArray): Boolean {
        if (packet.size < 3) return false
        val calc = calculateCrc16(packet, packet.size - 2)
        return calc[0] == packet[packet.size - 2] && calc[1] == packet[packet.size - 1]
    }

    fun appendCrc16(payloadWithoutCrc: ByteArray): ByteArray {
        val crc = calculateCrc16(payloadWithoutCrc, payloadWithoutCrc.size)
        return payloadWithoutCrc + crc
    }

    /**
     * Derives the 16-byte AES-ECB key `"00" + txId + "00" + txId` used by Dexcom OB1 auth.
     */
    fun deriveCryptKey(transmitterId: String): ByteArray? {
        val clean = transmitterId.trim().uppercase()
        if (clean.length != 6) return null
        val keyBytes = "00${clean}00$clean".toByteArray(Charsets.UTF_8)
        return keyBytes.takeIf { it.size == 16 }
    }

    fun calculateChallengeHash(challenge8: ByteArray, transmitterId: String): ByteArray? {
        if (challenge8.size != 8) return null
        val keyBytes = deriveCryptKey(transmitterId) ?: return null
        return runCatching {
            val input = ByteArray(16)
            System.arraycopy(challenge8, 0, input, 0, 8)
            System.arraycopy(challenge8, 0, input, 8, 8)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
            cipher.doFinal(input, 0, 16).copyOfRange(0, 8)
        }.getOrNull()
    }

    fun buildAuthRequestTx(singleUseToken8: ByteArray, slot: Byte = 0x02): ByteArray {
        require(singleUseToken8.size == 8)
        return byteArrayOf(0x01) + singleUseToken8 + byteArrayOf(slot)
    }

    fun buildAuthChallengeTx(hash8: ByteArray): ByteArray {
        require(hash8.size == 8)
        return byteArrayOf(0x04) + hash8
    }

    fun buildBondRequestTx(): ByteArray = byteArrayOf(0x07)

    fun buildEGlucoseTx(shortG7Mode: Boolean): ByteArray =
        if (shortG7Mode) byteArrayOf(0x4E) else appendCrc16(byteArrayOf(0x4E))

    fun buildBackfillTx2(dexStartSeconds: Int, dexEndSeconds: Int): ByteArray {
        val buf = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x59.toByte())
        buf.putInt(dexStartSeconds)
        buf.putInt(dexEndSeconds)
        return buf.array()
    }

    fun buildCalibrateTx(bgMgdl: Int, dexTimeSeconds: Int): ByteArray {
        val buf = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x34.toByte())
        buf.putShort(bgMgdl.toShort())
        buf.putInt(dexTimeSeconds)
        return appendCrc16(buf.array())
    }

    data class AuthChallengeRx(val tokenHash: ByteArray, val challenge: ByteArray)
    data class AuthStatusRx(val authenticated: Boolean, val bonded: Boolean)

    fun parseAuthChallengeRx(packet: ByteArray): AuthChallengeRx? {
        if (packet.size < 17 || packet[0] != 0x03.toByte()) return null
        return AuthChallengeRx(
            tokenHash = packet.copyOfRange(1, 9),
            challenge = packet.copyOfRange(9, 17),
        )
    }

    fun parseAuthStatusRx(packet: ByteArray): AuthStatusRx? {
        if (packet.size < 3 || packet[0] != 0x05.toByte()) return null
        return AuthStatusRx(
            authenticated = packet[1].toInt() == 1,
            bonded = packet[2].toInt() == 1,
        )
    }

    data class DexcomGlucosePacket(
        val protocol: String,
        val sequence: Int,
        val timestampMs: Long,
        val sensorAgeSeconds: Int,
        val glucoseMgdl: Double,
        val predictedGlucoseMgdl: Int?,
        val trendMgdlPerMin: Double?,
        val calibrationState: DexcomCalibrationState,
        val isDisplayOnly: Boolean,
        val sensorClockSeconds: Long = 0L,
    )

    /**
     * Decodes Dexcom G7 `EGlucoseRxMessage` (`0x4E`, >= 19 bytes), G6 `EGlucoseRxMessage2` (`0x4F`),
     * and G5/G6 `GlucoseRxMessage` (`0x31`).
     */
    fun parseGlucosePacket(
        packet: ByteArray,
        nowMs: Long = System.currentTimeMillis(),
        transmitterClockOriginMs: Long = 0L,
    ): DexcomGlucosePacket? {
        if (packet.isEmpty()) return null
        val opcode = packet[0].toInt() and 0xFF
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Dexcom G7 / ONE+ EGlucoseRxMessage (opcode 0x4E = 78, len >= 19)
        if (opcode == 0x4E && packet.size >= 19) {
            buf.get() // opcode
            buf.get() // status_raw
            val clock = buf.getInt().toLong() and 0xFFFFFFFFL
            val seq = buf.getShort().toInt() and 0xFFFF
            buf.getShort() // reserved
            val ageSec = buf.getShort().toInt() and 0xFFFF
            val glucoseRaw = buf.getShort().toInt() and 0xFFFF
            val displayOnly = (glucoseRaw and 0xF000) != 0
            val glucose = (glucoseRaw and 0x0FFF).toDouble()
            val state = DexcomCalibrationState.fromCode(buf.get().toInt() and 0xFF)
            val trendByte = buf.get().toInt()
            val trend = if (trendByte == 127) null else trendByte / 10.0
            val predRaw = buf.getShort().toInt() and 0x03FF
            val predicted = if (predRaw == 1023 || predRaw < 20) null else predRaw
            if (glucose !in 20.0..600.0) return null
            val readingTs = nowMs - ageSec * 1000L
            return DexcomGlucosePacket(
                protocol = "Dexcom G7",
                sequence = seq,
                timestampMs = readingTs,
                sensorAgeSeconds = ageSec,
                glucoseMgdl = glucose,
                predictedGlucoseMgdl = predicted,
                trendMgdlPerMin = trend,
                calibrationState = state,
                isDisplayOnly = displayOnly,
                sensorClockSeconds = clock,
            )
        }

        // 2. Dexcom G6/G5 GlucoseRxMessage (0x31) or EGlucoseRxMessage2 (0x4F)
        if ((opcode == 0x31 || opcode == 0x4F) && packet.size >= 14) {
            if (opcode == 0x4F && packet.size >= 16 && !checkCrc16(packet)) return null
            buf.get() // opcode
            buf.get() // status
            val seq = buf.getInt()
            val dexTime = buf.getInt().toLong() and 0xFFFFFFFFL
            val glucoseRaw = buf.getShort().toInt() and 0xFFFF
            val displayOnly = (glucoseRaw and 0xF000) != 0
            val glucose = (glucoseRaw and 0x0FFF).toDouble()
            val state = DexcomCalibrationState.fromCode(buf.get().toInt() and 0xFF)
            val trendByte = buf.get().toInt()
            val trend = if (trendByte == 127) null else trendByte / 10.0
            val pred = if (opcode == 0x4F && packet.size >= 16) (buf.getShort().toInt() and 0x03FF).takeIf { it in 20..600 } else null
            if (glucose !in 20.0..600.0) return null
            val ts = if (transmitterClockOriginMs > 0L) transmitterClockOriginMs + dexTime * 1000L else nowMs
            return DexcomGlucosePacket(
                protocol = if (opcode == 0x4F) "Dexcom G6 Extended" else "Dexcom G6/G5",
                sequence = seq,
                timestampMs = ts,
                sensorAgeSeconds = 0,
                glucoseMgdl = glucose,
                predictedGlucoseMgdl = pred,
                trendMgdlPerMin = trend,
                calibrationState = state,
                isDisplayOnly = displayOnly,
                sensorClockSeconds = dexTime,
            )
        }

        return null
    }

    data class BackfillReading(
        val dexTimeSeconds: Int,
        val glucoseMgdl: Double,
        val trendMgdlPerMin: Double?,
    )

    /**
     * Port of xDrip's `BackFillStream`: assembles 9-byte G7 or sequenced G6 backfill chunks
     * arriving on `CHAR_BACKFILL_UUID` (`F8083536-...`).
     */
    class DexcomBackfillAssembler {
        private val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        private var lastSeq = 0
        private var isNineByteMode = false

        @Synchronized
        fun reset() {
            buffer.clear()
            lastSeq = 0
            isNineByteMode = false
        }

        @Synchronized
        fun push(packet: ByteArray) {
            if (packet.isEmpty()) return
            if (packet.size == 9) {
                isNineByteMode = true
                if (buffer.remaining() >= 9) buffer.put(packet)
                return
            }
            val seq = packet[0].toInt() and 0xFF
            if (seq == lastSeq + 1 && packet.size > 2) {
                lastSeq++
                val payloadLen = packet.size - 2
                if (buffer.remaining() >= payloadLen) {
                    buffer.put(packet, 2, payloadLen)
                }
            }
        }

        @Synchronized
        fun decodeAll(): List<BackfillReading> {
            val limit = buffer.position()
            if (limit < 8) return emptyList()
            buffer.rewind()
            if (!isNineByteMode) {
                if (limit < 12) return emptyList()
                buffer.getInt() // 4-byte stream header
            }
            val out = mutableListOf<BackfillReading>()
            while (buffer.position() + (if (isNineByteMode) 9 else 8) <= limit) {
                val dexTime = buffer.getInt()
                val rawBg = buffer.getShort().toInt() and 0x0FFF
                val stateCode = buffer.get().toInt() and 0xFF
                if (isNineByteMode) buffer.get() // reserved byte in 9-byte G7 frames
                val trendByte = buffer.get().toInt()
                val state = DexcomCalibrationState.fromCode(stateCode)
                if (dexTime > 0 && rawBg in 20..600 && state.isUsable) {
                    out += BackfillReading(
                        dexTimeSeconds = dexTime,
                        glucoseMgdl = rawBg.toDouble(),
                        trendMgdlPerMin = if (trendByte == 127) null else trendByte / 10.0,
                    )
                }
            }
            return out
        }
    }
}
