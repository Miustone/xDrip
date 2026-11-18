package dev.xdripyou.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.xdripyou.app.XdripYouApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hardware `TYPE_STEP_COUNTER` listener recording incremental steps in 5-minute buckets
 * into Room `activity_samples` (analogous to xDrip `StepCounter` / `PebbleMovement`).
 */
class StepTrackerManager(private val app: XdripYouApplication) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sensorManager: SensorManager? = null
    private var registered = false
    private var lastRawTotal: Float? = null
    private var pendingDelta = 0
    private var lastFlushMs = 0L

    fun startIfPermitted() {
        if (registered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sensorManager = sm
        registered = sm.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (registered) {
            runCatching { sensorManager?.unregisterListener(this) }
            registered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val totalSinceBoot = event?.values?.firstOrNull() ?: return
        val prev = lastRawTotal
        lastRawTotal = totalSinceBoot
        if (prev == null || totalSinceBoot < prev) return

        val delta = (totalSinceBoot - prev).roundToInt()
        if (delta <= 0) return
        pendingDelta += delta

        val now = System.currentTimeMillis()
        if (lastFlushMs == 0L) lastFlushMs = now
        if (now - lastFlushMs >= 300_000L || pendingDelta >= 40) {
            val toSave = pendingDelta
            pendingDelta = 0
            lastFlushMs = now
            // Bucket timestamp rounded to 5 min
            val bucketTs = (now / 300_000L) * 300_000L
            scope.launch {
                val settings = app.repository.settingsStore.current()
                if (settings.showStepsCounter) {
                    app.repository.recordActivitySample(
                        timestampMs = bucketTs,
                        steps = toSave,
                        source = "Schrittzähler",
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
