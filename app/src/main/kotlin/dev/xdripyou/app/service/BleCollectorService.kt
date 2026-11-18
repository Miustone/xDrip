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
import dev.xdripyou.app.domain.DexcomBleParser
import dev.xdripyou.app.domain.LibreHardwareParser
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val suggestedType: String,
)

data class BleCollectorStatus(
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String = "",
    val lastStatusMessage: String = "Bereit",
    val discoveredDevices: List<DiscoveredBleDevice> = emptyList(),
)

/**
 * Direct Bluetooth Low Energy collector supporting:
 * - Bluetooth Glucose Meters (`0x1808` / `0x2A18` SFLOAT16)
 * - Libre BLE Bridges (Bubble / MiaoMiao / Tomato UART characteristics)
 * - Dexcom G5 / G6 / ONE GATT streams (`F8083532-...`)
 */
object BleCollectorManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(BleCollectorStatus())
    val status: StateFlow<BleCollectorStatus> = _status.asStateFlow()

    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val bubbleAssembler = LibreHardwareParser.BubblePacketAssembler()

    private var activeGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null

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
                    uuids.any { it.startsWith("f8083532") } || name.startsWith("Dexcom", true) -> "dexcom"
                    name.contains("bubble", true) || name.contains("miaomiao", true) -> "libre_bridge"
                    else -> "libre_bridge"
                }
                foundMap[addr] = DiscoveredBleDevice(name, addr, result.rssi, suggested)
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
            lastStatusMessage = "Suche BLE-Sensoren & Blutzuckermessgeräte…",
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
                deviceName = settings.bleDeviceName.ifBlank { addr },
                lastStatusMessage = "Verbinde mit ${settings.bleDeviceName.ifBlank { addr }}…",
            )

            activeGatt = device.connectGatt(
                app,
                false,
                createGattCallback(app, settings.bleDeviceType),
                BluetoothDevice.TRANSPORT_LE,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        _status.value = _status.value.copy(connected = false, lastStatusMessage = "BLE getrennt")
    }

    @SuppressLint("MissingPermission")
    private fun createGattCallback(app: XdripYouApplication, deviceType: String): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _status.value = _status.value.copy(connected = true, lastStatusMessage = "Verbunden · lese Dienste…")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _status.value = _status.value.copy(connected = false, lastStatusMessage = "Verbindung getrennt (Status $status)")
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
                        if (meas != null) {
                            enableNotifyOrIndicate(gatt, meas, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        }
                        if (racp != null) {
                            enableNotifyOrIndicate(gatt, racp, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                            racp.value = BluetoothGlucoseMeterParser.buildReportAllRecordsCommand()
                            gatt.writeCharacteristic(racp)
                        }
                        _status.value = _status.value.copy(lastStatusMessage = "BG Meter bereit (0x1808)")
                    }
                    "dexcom" -> {
                        val svc = gatt.getService(UUID.fromString(DexcomBleParser.SERVICE_G6_UUID))
                        val ctrl = svc?.getCharacteristic(UUID.fromString(DexcomBleParser.CHAR_CONTROL_UUID))
                        if (ctrl != null) {
                            enableNotifyOrIndicate(gatt, ctrl, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        }
                        _status.value = _status.value.copy(lastStatusMessage = "Dexcom GATT aktiv")
                    }
                    else -> { // libre_bridge UART
                        gatt.services.forEach { svc ->
                            svc.characteristics.forEach { ch ->
                                val props = ch.properties
                                if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
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
                    when {
                        uuid == BluetoothGlucoseMeterParser.CHAR_GLUCOSE_MEASUREMENT_UUID -> {
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
                                dev.xdripyou.app.domain.TreatmentEntry(
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
                        uuid == DexcomBleParser.CHAR_CONTROL_UUID -> {
                            val parsed = DexcomBleParser.parseGlucoseRx(value) ?: return@launch
                            val out = app.repository.insertReading(
                                mgdl = parsed.glucoseMgdl,
                                timestampMs = System.currentTimeMillis(),
                                source = "Dexcom BLE",
                                rawText = "state=${parsed.stateByte}",
                                applyDeduplication = true,
                                overrideTrend = TrendArrow.fromSlopePerMin(parsed.trendRateMgdlPerMin),
                            )
                            if (out is InsertOutcome.Inserted) {
                                app.alertEngine.refreshOngoingNotification()
                                app.alertEngine.evaluateOnNewReading(out.reading)
                            }
                            _status.value = _status.value.copy(lastStatusMessage = "Dexcom BLE: ${parsed.glucoseMgdl.toInt()} mg/dL")
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
