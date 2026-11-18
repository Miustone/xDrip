package dev.xdripyou.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Mass
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TreatmentEntry
import java.time.Instant
import java.time.ZoneOffset

/**
 * Port of xDrip's `HealthGamut` / `HealthConnectEntry` using `androidx.health.connect:connect-client`.
 * Supports bidirectional `BloodGlucoseRecord` sync and `NutritionRecord` carb export.
 */
object HealthConnectSyncManager {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
    )

    fun isAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    fun openInstallOrSettings(context: Context) {
        val status = HealthConnectClient.getSdkStatus(context)
        val intent = if (status == HealthConnectClient.SDK_AVAILABLE) {
            if (Build.VERSION.SDK_INT >= 34) {
                Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                    .putExtra("android.intent.extra.PACKAGE_NAME", context.packageName)
            } else {
                Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            }
        } else {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect://onboarding"),
            )
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    suspend fun writeReadings(context: Context, readings: List<GlucoseReading>): Result<Int> = runCatching {
        if (readings.isEmpty()) return@runCatching 0
        require(isAvailable(context)) { "Health Connect nicht verfügbar" }
        require(hasAllPermissions(context)) { "Health Connect Berechtigung fehlt" }
        val client = HealthConnectClient.getOrCreate(context)
        val records = readings.map { r ->
            val instant = Instant.ofEpochMilli(r.timestampMs)
            BloodGlucoseRecord(
                time = instant,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
                level = BloodGlucose.milligramsPerDeciliter(r.mgdl.coerceIn(20.0, 600.0)),
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                mealType = MealType.MEAL_TYPE_UNKNOWN,
                relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                metadata = Metadata(clientRecordId = "xdrip2you_bg_${r.timestampMs}"),
            )
        }
        client.insertRecords(records)
        records.size
    }

    suspend fun writeCarbTreatments(context: Context, treatments: List<TreatmentEntry>): Result<Int> = runCatching {
        val carbsOnly = treatments.filter { it.carbsGrams > 0.0 }
        if (carbsOnly.isEmpty()) return@runCatching 0
        require(isAvailable(context)) { "Health Connect nicht verfügbar" }
        require(hasAllPermissions(context)) { "Health Connect Berechtigung fehlt" }
        val client = HealthConnectClient.getOrCreate(context)
        val records = carbsOnly.map { t ->
            val start = Instant.ofEpochMilli(t.timestampMs)
            val end = start.plusSeconds(60)
            val offset = ZoneOffset.systemDefault().rules.getOffset(start)
            NutritionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                totalCarbohydrate = Mass.grams(t.carbsGrams),
                name = t.note.ifBlank { "Kohlenhydrate" },
                metadata = Metadata(clientRecordId = "xdrip2you_carb_${t.timestampMs}_${t.id}"),
            )
        }
        client.insertRecords(records)
        records.size
    }

    suspend fun readRecentReadings(context: Context, hoursBack: Long = 12L): Result<List<Pair<Long, Double>>> =
        runCatching {
            require(isAvailable(context)) { "Health Connect nicht verfügbar" }
            require(hasAllPermissions(context)) { "Health Connect Berechtigung fehlt" }
            val client = HealthConnectClient.getOrCreate(context)
            val end = Instant.now()
            val start = end.minusSeconds(hoursBack.coerceIn(1L, 72L) * 3600L)
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = BloodGlucoseRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            resp.records
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
                .map { rec -> rec.time.toEpochMilli() to rec.level.inMilligramsPerDeciliter }
                .filter { (_, mgdl) -> mgdl in 20.0..600.0 }
        }
}
