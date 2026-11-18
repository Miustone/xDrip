package dev.xdripyou.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.data.InsertOutcome
import dev.xdripyou.app.domain.BluetoothGlucoseMeterParser
import dev.xdripyou.app.domain.DexcomOb1Protocol
import dev.xdripyou.app.domain.LibreHardwareParser
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val suggestedType: String,
    val isBonded: Boolean = false,
)

data class BleCollectorStatus(
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val authenticated: Boolean = false,
    val deviceName: String = "",
    val lastStatusMessage: String = "Bereit",
    val lastCalibrationState: String = "",
    val lastPredictedMgdl: Int? = null,
    val discoveredDevices: List<DiscoveredBleDevice> = emptyList(),
)

/**
 * Direct Bluetooth Low Energy collector supporting:
 * - **Dexcom G7 / ONE+ (BlueTails bonded listener + Active OB1 `EGlucoseRx` & Backfill)**
 * - **Dexcom G6 / ONE (Full OB1 AES-ECB Auth, Bond, `GlucoseRx`/`EGlucoseRx2`, Calibration & Backfill)**
 * - **FreeStyle Libre 2 BLE (`0000fde3`/`0000f002` + OOP2 Bridge)** & **Bubble/MiaoMiao Libre Bridges**
 * - **Bluetooth SIG Glucose Meters (`0x1808` / `0x2A18` SFLOAT16)**
 */
object BleCollectorManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(BleCollectorStatus())
    val status: StateFlow<BleCollectorStatus> = _status.asStateFlow()

    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val LIBRE2_SERVICE_UUID = UUID.fromString("0000fde3-0000-1000-8000-00805f9b34fb")
    private val LIBRE2_NOTIFY_UUID = UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb")

    private val bubbleAssembler = LibreHardwareParser.BubblePacketAssembler()
    private val libre2Assembler = LibreHardwareParser.Libre2BleAssembler()
    private val dexcomBackfillAssembler = DexcomOb1Protocol.DexcomBackfillAssembler()
    private val secureRandom = SecureRandom()

    private var activeGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var transmitterClockOriginMs: Long = 0L

    fun hasBlePermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter
    }

    /**
     * Port of xDrip `BlueTails`: returns already-paired Android Bluetooth devices whose name
     * identifies a Dexcom G7 / G6 / ONE transmitter (`DXC...` or `Dex...`).
     */
    @SuppressLint("MissingPermission")
    fun loadBondedDexcomDevices(context: Context): List<DiscoveredBleDevice> {
        if (!hasBlePermissions(context)) {
            _status.value = _status.value.copy(lastStatusMessage = "Bluetooth-Berechtigungen fehlen")
            return emptyList()
        }
        val adapter = bluetoothAdapter(context) ?: return emptyList()
        val list = adapter.bondedDevices
            ?.filter { d ->
                val n = d.name.orEmpty()
                n.startsWith("DXC", ignoreCase = true) || n.startsWith("Dex", ignoreCase = true)
            }
            ?.map { d ->
                DiscoveredBleDevice(
                    name = d.name ?: d.address,
                    address = d.address,
                    rssi = 0,
                    suggestedType = if (d.name.orEmpty().startsWith("DXC", ignoreCase = true)) "dexcom_bluetails" else "dexcom",
                    isBonded = true,
                )
            }.orEmpty()
        _status.value = _status.value.copy(
            discoveredDevices = list,
            lastStatusMessage = if (list.isEmpty()) "Kein gekoppelter DXC/Dexcom-Sensor gefunden" else "${list.size} gekoppelte(r) Dexcom gefunden",
        )
        return list
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        if (!hasBlePermissions(context)) {
            _status.value = _status.value.copy(lastStatusMessage = "Bluetooth-Berechtigungen fehlen")
            return
        }
        val adapter = bluetoothAdapter(context)
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            _status.value = _status.value.copy(lastStatusMessage = "Bluetooth ist ausgeschaltet")
            return
        }

        stopScan(context)
        val foundMap = linkedMapOf<String, DiscoveredBleDevice>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val addr = device.address ?: return
                val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "BLE ($addr)"
                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() }.orEmpty()
                val suggested = when {
                    uuids.any { it.startsWith("00001808") } || name.contains("contour", true) || name.contains("accu", true) -> "meter"
                    uuids.any { it.startsWith("0000fde3") } || name.contains("ABBOTT", true) -> "libre2_ble"
                    name.startsWith("DXC", true) -> "dexcom_bluetails"
                    uuids.any { it.startsWith("f8083532") } || name.startsWith("Dex", true) -> "dexcom"
                    name.contains("bubble", true) || name.contains("miaomiao", true) -> "libre_bridge"
                    else -> "libre_bridge"
                }
                val isBonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
                foundMap[addr] = DiscoveredBleDevice(name, addr, result.rssi, suggested, isBonded)
                _status.value = _status.value.copy(
                    scanning = true,
                    discoveredDevices = foundMap.values.sortedByDescending { it.rssi },
                )
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = _status.value.copy(scanning = false, lastStatusMessage = "BLE Scan Fehler $errorCode")
            }
        }
        scanCallback = cb
        _status.value = _status.value.copy(
            scanning = true,
            discoveredDevices = emptyList(),
            lastStatusMessage = "Suche Dexcom G7/G6, Libre 2/Bridge & Messgeräte…",
        )
        scanner.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            cb,
        )
    }

    @SuppressLint("MissingPermission")
    fun stopScan(context: Context) {
        val cb = scanCallback ?: return
        scanCallback = null
        if (hasBlePermissions(context)) {
            runCatching { bluetoothAdapter(context)?.bluetoothLeScanner?.stopScan(cb) }
        }
        _status.value = _status.value.copy(scanning = false)
    }

    @SuppressLint("MissingPermission")
    fun connectToConfigured(app: XdripYouApplication) {
        scope.launch {
            val settings = app.repository.settingsStore.current()
            val addr = settings.bleDeviceAddress.trim()
            if (settings.bleDeviceType == "none" || addr.isEmpty()) return@launch
            if (!hasBlePermissions(app)) {
                _status.value = _status.value.copy(lastStatusMessage = "BLE Berechtigungen fehlen")
                return@launch
            }
            val adapter = bluetoothAdapter(app) ?: return@launch
            if (!adapter.isEnabled) return@launch
            val device: BluetoothDevice = runCatching { adapter.getRemoteDevice(addr) }.getOrNull() ?: return@launch

            stopScan(app)
            activeGatt?.disconnect()
            activeGatt?.close()

            _status.value = _status.value.copy(
                authenticated = false,
                deviceName = settings.bleDeviceName.ifBlank { addr },
                lastStatusMessage = "Verbinde mit ${settings.bleDeviceName.ifBlank { addr }}…",
            )

            activeGatt = device.connectGatt(
                app,
                settings.bleDeviceType == "dexcom_bluetails",
                createGattCallback(app, settings.bleDeviceType, settings.dexcomTransmitterId),
                BluetoothDevice.TRANSPORT_LE,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun requestDexcomBackfill(hoursBack: Int = 3): Boolean {
        val gatt = activeGatt ?: return false
        val svc = gatt.getService(UUID.fromString(DexcomOb1Protocol.SERVICE_CGM_UUID)) ?: return false
        val ctrl = svc.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_CONTROL_UUID)) ?: return false
        if (transmitterClockOriginMs <= 0L) return false
        val nowSec = ((System.currentTimeMillis() - transmitterClockOriginMs) / 1000L).toInt().coerceAtLeast(300)
        val startSec = (nowSec - hoursBack * 3600).coerceAtLeast(1)
        dexcomBackfillAssembler.reset()
        ctrl.value = DexcomOb1Protocol.buildBackfillTx2(startSec, nowSec)
        val ok = gatt.writeCharacteristic(ctrl)
        if (ok) {
            _status.value = _status.value.copy(lastStatusMessage = "Dexcom Backfill (${hoursBack}h) angefordert…")
        }
        return ok
    }

    @SuppressLint("MissingPermission")
    fun sendDexcomCalibration(bgMgdl: Int): Boolean {
        val gatt = activeGatt ?: return false
        val svc = gatt.getService(UUID.fromString(DexcomOb1Protocol.SERVICE_CGM_UUID)) ?: return false
        val ctrl = svc.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_CONTROL_UUID)) ?: return false
        if (transmitterClockOriginMs <= 0L || bgMgdl !in 40..400) return false
        val nowSec = ((System.currentTimeMillis() - transmitterClockOriginMs) / 1000L).toInt().coerceAtLeast(1)
        ctrl.value = DexcomOb1Protocol.buildCalibrateTx(bgMgdl, nowSec)
        return gatt.writeCharacteristic(ctrl)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        _status.value = _status.value.copy(connected = false, authenticated = false, lastStatusMessage = "BLE getrennt")
    }

    @SuppressLint("MissingPermission")
    private fun createGattCallback(
        app: XdripYouApplication,
        deviceType: String,
        transmitterId: String,
    ): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _status.value = _status.value.copy(connected = true, lastStatusMessage = "Verbunden · lese Dienste…")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _status.value = _status.value.copy(
                        connected = false,
                        authenticated = false,
                        lastStatusMessage = "Verbindung getrennt (Status $status)",
                    )
                    gatt.close()
                    if (activeGatt == gatt) activeGatt = null
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                when (deviceType) {
                    "meter" -> {
                        val svc = gatt.getService(UUID.fromString(BluetoothGlucoseMeterParser.SERVICE_GLUCOSE_UUID))
                        val meas = svc?.getCharacteristic(UUID.fromString(BluetoothGlucoseMeterParser.CHAR_GLUCOSE_MEASUREMENT_UUID))
                        val racp = svc?.getCharacteristic(UUID.fromString(BluetoothGlucoseMeterParser.CHAR_RECORD_ACCESS_CONTROL_POINT_UUID))
                        if (meas != null) enableNotifyOrIndicate(gatt, meas, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        if (racp != null) {
                            enableNotifyOrIndicate(gatt, racp, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                            racp.value = BluetoothGlucoseMeterParser.buildReportAllRecordsCommand()
                            gatt.writeCharacteristic(racp)
                        }
                        _status.value = _status.value.copy(lastStatusMessage = "BG Meter bereit (0x1808)")
                    }

                    "dexcom_bluetails" -> {
                        dexcomBackfillAssembler.reset()
                        val svc = gatt.getService(UUID.fromString(DexcomOb1Protocol.SERVICE_CGM_UUID))
                        val ctrl = svc?.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_CONTROL_UUID))
                        val backfill = svc?.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_BACKFILL_UUID))
                        if (ctrl != null) enableNotifyOrIndicate(gatt, ctrl, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        if (backfill != null) enableNotifyOrIndicate(gatt, backfill, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        _status.value = _status.value.copy(
                            authenticated = true,
                            lastStatusMessage = "G7 BlueTails Listener aktiv (warte auf Sensor-Zyklus)",
                        )
                    }

                    "dexcom" -> {
                        dexcomBackfillAssembler.reset()
                        val svc = gatt.getService(UUID.fromString(DexcomOb1Protocol.SERVICE_CGM_UUID))
                        val auth = svc?.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_AUTHENTICATION_UUID))
                        val ctrl = svc?.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_CONTROL_UUID))
                        val backfill = svc?.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_BACKFILL_UUID))
                        if (ctrl != null) enableNotifyOrIndicate(gatt, ctrl, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        if (backfill != null) enableNotifyOrIndicate(gatt, backfill, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        val cleanTxId = transmitterId.trim().uppercase()
                        if (auth != null && cleanTxId.length == 6) {
                            enableNotifyOrIndicate(gatt, auth, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                            val token = ByteArray(8).also { secureRandom.nextBytes(it) }
                            auth.value = DexcomOb1Protocol.buildAuthRequestTx(token)
                            gatt.writeCharacteristic(auth)
                            _status.value = _status.value.copy(lastStatusMessage = "Dexcom OB1 Authentifizierung gestartet…")
                        } else if (ctrl != null) {
                            // Short G7 mode when already bonded (G7 uses shortTxId=true 1-byte 0x4E request)
                            val shortG7 = cleanTxId.length < 6
                            ctrl.value = DexcomOb1Protocol.buildEGlucoseTx(shortG7)
                            gatt.writeCharacteristic(ctrl)
                            _status.value = _status.value.copy(lastStatusMessage = "Dexcom G7 Daten angefragt…")
                        }
                    }

                    "libre2_ble" -> {
                        val svc = gatt.getService(LIBRE2_SERVICE_UUID)
                        val notifyCh = svc?.getCharacteristic(LIBRE2_NOTIFY_UUID)
                        if (notifyCh != null) {
                            enableNotifyOrIndicate(gatt, notifyCh, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            _status.value = _status.value.copy(lastStatusMessage = "Libre 2 BLE Stream (0xFDE3) aktiv")
                        } else {
                            _status.value = _status.value.copy(lastStatusMessage = "Libre 2 Service (0xFDE3) nicht gefunden")
                        }
                    }

                    else -> { // libre_bridge UART
                        gatt.services.forEach { svc ->
                            svc.characteristics.forEach { ch ->
                                if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                    enableNotifyOrIndicate(gatt, ch, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                }
                            }
                        }
                        _status.value = _status.value.copy(lastStatusMessage = "Libre-Bridge Stream aktiv")
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val value = characteristic.value ?: return
                val uuid = characteristic.uuid.toString().lowercase()
                scope.launch {
                    when (uuid) {
                        BluetoothGlucoseMeterParser.CHAR_GLUCOSE_MEASUREMENT_UUID -> {
                            val parsed = BluetoothGlucoseMeterParser.parseGlucoseMeasurement(value) ?: return@launch
                            val out = app.repository.insertReading(
                                mgdl = parsed.mgdl,
                                timestampMs = parsed.timestampMs,
                                source = "BT Messgerät",
                                rawText = "seq=${parsed.sequenceNumber}",
                                applyDeduplication = false,
                                overrideTrend = TrendArrow.NOT_COMPUTABLE,
                            )
                            app.repository.addOrUpdateTreatment(
                                TreatmentEntry(
                                    timestampMs = parsed.timestampMs,
                                    bloodGlucoseMgdl = parsed.mgdl,
                                    note = "BT-Messgerät #${parsed.sequenceNumber}",
                                    eventType = "BG Check",
                                )
                            )
                            if (out is InsertOutcome.Inserted) {
                                app.alertEngine.refreshOngoingNotification()
                                app.alertEngine.evaluateOnNewReading(out.reading)
                            }
                            _status.value = _status.value.copy(lastStatusMessage = "BT Meter: ${parsed.mgdl.toInt()} mg/dL")
                        }

                        DexcomOb1Protocol.CHAR_AUTHENTICATION_UUID -> {
                            val svc = gatt.getService(UUID.fromString(DexcomOb1Protocol.SERVICE_CGM_UUID)) ?: return@launch
                            val authCh = svc.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_AUTHENTICATION_UUID))
                            val ctrlCh = svc.getCharacteristic(UUID.fromString(DexcomOb1Protocol.CHAR_CONTROL_UUID))

                            val challenge = DexcomOb1Protocol.parseAuthChallengeRx(value)
                            if (challenge != null && authCh != null) {
                                val hash = DexcomOb1Protocol.calculateChallengeHash(challenge.challenge, transmitterId)
                                if (hash != null) {
                                    authCh.value = DexcomOb1Protocol.buildAuthChallengeTx(hash)
                                    gatt.writeCharacteristic(authCh)
                                } else {
                                    _status.value = _status.value.copy(lastStatusMessage = "Transmitter-ID ungültig für AES Auth")
                                }
                                return@launch
                            }
                            val authStatus = DexcomOb1Protocol.parseAuthStatusRx(value)
                            if (authStatus != null) {
                                if (!authStatus.authenticated) {
                                    _status.value = _status.value.copy(authenticated = false, lastStatusMessage = "Dexcom Auth abgelehnt (TxID?)")
                                    return@launch
                                }
                                if (!authStatus.bonded && authCh != null) {
                                    authCh.value = DexcomOb1Protocol.buildBondRequestTx()
                                    gatt.writeCharacteristic(authCh)
                                    runCatching { gatt.device.createBond() }
                                    _status.value = _status.value.copy(lastStatusMessage = "Pairing / Bond angefordert…")
                                    return@launch
                                }
                                _status.value = _status.value.copy(authenticated = true, lastStatusMessage = "Dexcom Authentifiziert · frage Werte ab")
                                if (ctrlCh != null) {
                                    val shortG7 = transmitterId.trim().length < 6
                                    ctrlCh.value = DexcomOb1Protocol.buildEGlucoseTx(shortG7)
                                    gatt.writeCharacteristic(ctrlCh)
                                }
                            }
                        }

                        DexcomOb1Protocol.CHAR_CONTROL_UUID -> {
                            val now = System.currentTimeMillis()
                            val parsed = DexcomOb1Protocol.parseGlucosePacket(value, now, transmitterClockOriginMs)
                            if (parsed != null) {
                                if (parsed.sensorClockSeconds > 0L) {
                                    transmitterClockOriginMs = parsed.timestampMs - parsed.sensorClockSeconds * 1000L
                                }
                                if (parsed.calibrationState.isUsable) {
                                    val out = app.repository.insertReading(
                                        mgdl = parsed.glucoseMgdl,
                                        timestampMs = parsed.timestampMs,
                                        source = parsed.protocol,
                                        rawText = "seq=${parsed.sequence} st=${parsed.calibrationState.name} pred=${parsed.predictedGlucoseMgdl ?: -1}",
                                        applyDeduplication = true,
                                        overrideTrend = parsed.trendMgdlPerMin?.let { TrendArrow.fromSlopePerMin(it) },
                                    )
                                    if (out is InsertOutcome.Inserted) {
                                        app.alertEngine.refreshOngoingNotification()
                                        app.alertEngine.evaluateOnNewReading(out.reading)
                                    }
                                }
                                _status.value = _status.value.copy(
                                    lastCalibrationState = parsed.calibrationState.labelDe,
                                    lastPredictedMgdl = parsed.predictedGlucoseMgdl,
                                    lastStatusMessage = "${parsed.protocol}: ${parsed.glucoseMgdl.toInt()} mg/dL (${parsed.calibrationState.labelDe})",
                                )
                            } else if (value.isNotEmpty() && value[0] == 0x59.toByte()) {
                                // Backfill control ack -> wait briefly and decode backfill buffer
                                delay(1200L)
                                val entries = dexcomBackfillAssembler.decodeAll()
                                var count = 0
                                for (entry in entries) {
                                    if (transmitterClockOriginMs <= 0L) continue
                                    val ts = transmitterClockOriginMs + entry.dexTimeSeconds * 1000L
                                    val out = app.repository.insertReading(
                                        mgdl = entry.glucoseMgdl,
                                        timestampMs = ts,
                                        source = "Dexcom Backfill",
                                        rawText = null,
                                        applyDeduplication = false,
                                        overrideTrend = entry.trendMgdlPerMin?.let { TrendArrow.fromSlopePerMin(it) },
                                    )
                                    if (out is InsertOutcome.Inserted) count++
                                }
                                if (count > 0) {
                                    app.alertEngine.refreshOngoingNotification()
                                }
                                _status.value = _status.value.copy(lastStatusMessage = "Dexcom Backfill: $count neue Werte")
                            }
                        }

                        DexcomOb1Protocol.CHAR_BACKFILL_UUID -> {
                            dexcomBackfillAssembler.push(value)
                            val decoded = dexcomBackfillAssembler.decodeAll()
                            if (decoded.isNotEmpty() && transmitterClockOriginMs > 0L) {
                                var count = 0
                                for (entry in decoded) {
                                    val ts = transmitterClockOriginMs + entry.dexTimeSeconds * 1000L
                                    val out = app.repository.insertReading(
                                        mgdl = entry.glucoseMgdl,
                                        timestampMs = ts,
                                        source = "Dexcom Backfill",
                                        rawText = null,
                                        applyDeduplication = false,
                                        overrideTrend = entry.trendMgdlPerMin?.let { TrendArrow.fromSlopePerMin(it) },
                                    )
                                    if (out is InsertOutcome.Inserted) count++
                                }
                                if (count > 0) app.alertEngine.refreshOngoingNotification()
                            }
                        }

                        LIBRE2_NOTIFY_UUID.toString() -> {
                            val frame46 = libre2Assembler.onPacket(value) ?: return@launch
                            val now = System.currentTimeMillis()
                            XdripBroadcastReceiver.sendLibre2BleFrame(app, frame46, now)
                            _status.value = _status.value.copy(lastStatusMessage = "Libre 2 BLE 46B Frame an OOP2-Bridge gesendet")
                        }

                        else -> {
                            val bridge = bubbleAssembler.onPacket(value)
                            bridge.completedFram344?.let { fram ->
                                val si = app.repository.currentSlopeIntercept()
                                val parsed = LibreHardwareParser.parseFram344(fram, si) ?: return@launch
                                val out = app.repository.insertReading(
                                    mgdl = parsed.estimatedMgdl,
                                    timestampMs = System.currentTimeMillis(),
                                    source = "Libre BLE Bridge",
                                    rawText = "bat=${bridge.batteryPercent ?: -1}% raw=${parsed.currentRaw.toInt()}",
                                    applyDeduplication = true,
                                )
                                if (out is InsertOutcome.Inserted) {
                                    app.alertEngine.refreshOngoingNotification()
                                    app.alertEngine.evaluateOnNewReading(out.reading)
                                }
                                _status.value = _status.value.copy(
                                    lastStatusMessage = "Libre Bridge: ${parsed.estimatedMgdl.toInt()} mg/dL" +
                                        (bridge.batteryPercent?.let { " (Akku $it%)" } ?: ""),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifyOrIndicate(
        gatt: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        descriptorValue: ByteArray,
    ) {
        gatt.setCharacteristicNotification(ch, true)
        val desc = ch.getDescriptor(CCCD_UUID) ?: return
        desc.value = descriptorValue
        gatt.writeDescriptor(desc)
    }
}
