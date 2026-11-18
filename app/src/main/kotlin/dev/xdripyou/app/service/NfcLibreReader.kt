package dev.xdripyou.app.service

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.data.InsertOutcome
import dev.xdripyou.app.domain.LibreHardwareParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Direct ISO-15693 (`NfcV`) Freestyle Libre 1/2 FRAM NFC reader.
 * Reads 43 blocks (344 bytes) of FRAM on tag discovery, verifies CRC16, decodes sensor serial,
 * applies active WLS Calibration slope/intercept, and feeds readings into `GlucoseRepository`.
 */
object NfcLibreReader {

    suspend fun handleTagIntent(
        app: XdripYouApplication,
        intent: Intent?,
    ): Result<String>? {
        val action = intent?.action ?: return null
        if (action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            return null
        }

        val settings = app.repository.settingsStore.current()
        if (!settings.nfcLibreEnabled) return null

        @Suppress("DEPRECATION")
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return null
        val nfcV = NfcV.get(tag) ?: return Result.failure(IllegalStateException("Kein Libre NfcV Tag"))

        return withContext(Dispatchers.IO) {
            runCatching {
                val uid = tag.id ?: ByteArray(8)
                val serial = LibreHardwareParser.decodeSerialNumber(uid) ?: "LIBRE-NFC"
                val fram = ByteArray(344)

                nfcV.use { tech ->
                    tech.connect()
                    for (block in 0 until 43) {
                        val cmd = byteArrayOf(
                            0x02, // high data rate, unaddressed single block read
                            0x20, // ISO15693 READ_SINGLE_BLOCK
                            block.toByte(),
                        )
                        val resp = tech.transceive(cmd)
                        // Response format: byte 0 = flags (0x00 OK), bytes 1..8 = block data
                        if (resp.size < 9 || (resp[0].toInt() and 0x01) != 0) {
                            error("NFC Lesefehler bei Block $block")
                        }
                        System.arraycopy(resp, 1, fram, block * 8, 8)
                    }
                }

                val crcValid = LibreHardwareParser.verifyFram344(fram)
                val now = System.currentTimeMillis()
                if (settings.libreOopBridgeEnabled) {
                    XdripBroadcastReceiver.sendLibreOopFrame(
                        context = app,
                        fram344 = fram,
                        patchUid = uid,
                        serial = serial,
                        timestampMs = now,
                    )
                }
                val si = app.repository.currentSlopeIntercept()
                val parsed = LibreHardwareParser.parseFram344(fram, si)
                    ?: error("Ungültiger Libre FRAM Inhalt")

                val out = app.repository.insertReading(
                    mgdl = parsed.estimatedMgdl,
                    timestampMs = now,
                    source = "NFC Libre ($serial)",
                    rawText = "raw=${parsed.currentRaw.toInt()} crc=$crcValid min=${parsed.sensorMinutes}",
                    applyDeduplication = false,
                )
                app.repository.recordCollectorEvent(
                    packageName = "nfc.libre.$serial",
                    status = if (crcValid) "nfc_ok" else "nfc_crc_warn",
                    rawTexts = "serial=$serial raw=${parsed.currentRaw.toInt()} state=${parsed.sensorState}",
                    parsedMgdl = parsed.estimatedMgdl,
                    message = "NFC Scan $serial (${parsed.sensorMinutes / 1440}T ${(parsed.sensorMinutes % 1440) / 60}h)",
                )
                if (out is InsertOutcome.Inserted) {
                    app.alertEngine.refreshOngoingNotification()
                    app.alertEngine.evaluateOnNewReading(out.reading)
                }
                "Libre NFC $serial: ${parsed.estimatedMgdl.toInt()} mg/dL (CRC ${if (crcValid) "OK" else "Warn"})"
            }
        }
    }
}
