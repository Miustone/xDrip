package dev.xdripyou.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Mass
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TreatmentEntry
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Result bundle returned when pulling telemetry & activity from Android Health Connect.
 */
data class HealthConnectActivityPull(
    val readings: List<Pair<Long, Double>> = emptyList(),
    val nutritionTreatments: List<TreatmentEntry> = emptyList(),
    val workoutTreatments: List<TreatmentEntry> = emptyList(),
    val hourlySteps: List<Pair<Long, Int>> = emptyList(),
    val hourlyHeartRateBpm: List<Pair<Long, Int>> = emptyList(),
)

/**
 * Bidirectional Android Health Connect client:
 * - BloodGlucoseRecord (read/write)
 * - NutritionRecord (read/write carbohydrates)
 * - StepsRecord (deduplicated hourly aggregate read)
 * - HeartRateRecord (hourly average + latest BPM read)
 * - ExerciseSessionRecord (workout markers read)
 */
object HealthConnectSyncManager {

    private val permReadBg = HealthPermission.getReadPermission(BloodGlucoseRecord::class)
    private val permWriteBg = HealthPermission.getWritePermission(BloodGlucoseRecord::class)
    private val permReadNutrition = HealthPermission.getReadPermission(NutritionRecord::class)
    private val permWriteNutrition = HealthPermission.getWritePermission(NutritionRecord::class)
    private val permReadSteps = HealthPermission.getReadPermission(StepsRecord::class)
    private val permReadHeartRate = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val permReadExercise = HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    val readPermissions: Set<String> = setOf(
        permReadBg,
        permReadNutrition,
        permReadSteps,
        permReadHeartRate,
        permReadExercise,
    )

    val requiredPermissions: Set<String> = readPermissions + setOf(
        permWriteBg,
        permWriteNutrition,
    )

    fun isAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun getGrantedPermissions(context: Context): Set<String> {
        if (!isAvailable(context)) return emptySet()
        return runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
    }

    suspend fun hasAllPermissions(context: Context): Boolean {
        val granted = getGrantedPermissions(context)
        return granted.isNotEmpty() && granted.containsAll(requiredPermissions)
    }

    suspend fun hasAnyReadPermission(context: Context): Boolean {
        val granted = getGrantedPermissions(context)
        return granted.any { it in readPermissions }
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
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (permWriteBg !in granted) return@runCatching 0
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
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (permWriteNutrition !in granted) return@runCatching 0
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

    suspend fun readRecentReadings(context: Context, hoursBack: Long = 24L): Result<List<Pair<Long, Double>>> =
        runCatching {
            require(isAvailable(context)) { "Health Connect nicht verfügbar" }
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (permReadBg !in granted) return@runCatching emptyList()
            val end = Instant.now()
            val start = end.minusSeconds(hoursBack.coerceIn(1L, 168L) * 3600L)
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

    suspend fun pullAllRecent(context: Context, hoursBack: Long = 24L): Result<HealthConnectActivityPull> =
        runCatching {
            require(isAvailable(context)) { "Health Connect nicht verfügbar" }
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            require(granted.any { it in readPermissions }) { "Keine Health Connect Leseberechtigung erteilt" }

            val end = Instant.now()
            val start = end.minusSeconds(hoursBack.coerceIn(1L, 168L) * 3600L)
            val range = TimeRangeFilter.between(start, end)

            val readings = if (permReadBg in granted) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = BloodGlucoseRecord::class,
                            timeRangeFilter = range,
                        )
                    ).records
                        .filter { it.metadata.dataOrigin.packageName != context.packageName }
                        .map { it.time.toEpochMilli() to it.level.inMilligramsPerDeciliter }
                        .filter { (_, mgdl) -> mgdl in 20.0..600.0 }
                }.getOrDefault(emptyList())
            } else emptyList()

            val nutritionTreatments = if (permReadNutrition in granted) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = NutritionRecord::class,
                            timeRangeFilter = range,
                        )
                    ).records
                        .filter { it.metadata.dataOrigin.packageName != context.packageName }
                        .mapNotNull { rec ->
                            val carbs = rec.totalCarbohydrate?.inGrams ?: 0.0
                            if (carbs <= 0.1) return@mapNotNull null
                            val mealName = rec.name?.trim().orEmpty()
                            TreatmentEntry(
                                timestampMs = rec.startTime.toEpochMilli(),
                                carbsGrams = carbs,
                                note = if (mealName.isNotEmpty()) "$mealName (Health Connect)" else "Health Connect",
                                eventType = "Carb Correction",
                            )
                        }
                }.getOrDefault(emptyList())
            } else emptyList()

            val workoutTreatments = if (permReadExercise in granted) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = ExerciseSessionRecord::class,
                            timeRangeFilter = range,
                        )
                    ).records
                        .filter { it.metadata.dataOrigin.packageName != context.packageName }
                        .map { rec ->
                            val mins = Duration.between(rec.startTime, rec.endTime).toMinutes().coerceAtLeast(1L)
                            val title = rec.title?.trim()?.takeIf { it.isNotEmpty() }
                                ?: exerciseLabelDe(rec.exerciseType)
                            TreatmentEntry(
                                timestampMs = rec.startTime.toEpochMilli(),
                                note = "$title (${mins}m · Health Connect)",
                                eventType = "Exercise",
                            )
                        }
                }.getOrDefault(emptyList())
            } else emptyList()

            val hourlySteps = if (permReadSteps in granted) {
                readHourlySteps(client, start, end)
            } else emptyList()

            val hourlyHeartRate = if (permReadHeartRate in granted) {
                readHourlyHeartRate(client, range)
            } else emptyList()

            HealthConnectActivityPull(
                readings = readings,
                nutritionTreatments = nutritionTreatments,
                workoutTreatments = workoutTreatments,
                hourlySteps = hourlySteps,
                hourlyHeartRateBpm = hourlyHeartRate,
            )
        }

    private suspend fun resolvePreferredStepsOrigin(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): DataOrigin? = runCatching {
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )
        resp.records
            .groupBy { it.metadata.dataOrigin }
            .mapValues { (_, recs) -> recs.sumOf { it.count } }
            .entries
            .filter { (origin, total) -> total > 0 && !origin.packageName.startsWith("com.android.healthconnect.phone") }
            .maxByOrNull { it.value }
            ?.key
    }.getOrNull()

    private suspend fun readHourlySteps(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<Pair<Long, Int>> {
        val preferredOrigin = resolvePreferredStepsOrigin(client, start, end)
        val originFilter = preferredOrigin?.let { setOf(it) } ?: emptySet()

        // Prefer Health Connect aggregation using wearable origin (e.g. Mi Fitness) to match watch totals
        val aggregated = runCatching {
            val alignedStartEpoch = (start.toEpochMilli() / 1_800_000L) * 1_800_000L
            val alignedStart = Instant.ofEpochMilli(alignedStartEpoch)
            var buckets = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(alignedStart, end),
                    timeRangeSlicer = Duration.ofMinutes(30),
                    dataOriginFilter = originFilter,
                )
            )
            if (originFilter.isNotEmpty() && buckets.all { (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) == 0L }) {
                buckets = client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(alignedStart, end),
                        timeRangeSlicer = Duration.ofMinutes(30),
                    )
                )
            }
            val byHour = linkedMapOf<Long, Long>()
            for (b in buckets) {
                val count = b.result[StepsRecord.COUNT_TOTAL] ?: 0L
                if (count > 0L) {
                    val hourTs = (b.startTime.toEpochMilli() / 3_600_000L) * 3_600_000L
                    byHour[hourTs] = (byHour[hourTs] ?: 0L) + count
                }
            }
            byHour.entries.sortedBy { it.key }.map { it.key to it.value.toInt() }
        }.getOrDefault(emptyList())

        if (aggregated.isNotEmpty()) return aggregated

        // Fallback: bucket raw StepsRecords per hour if OEM Health Connect returns empty aggregates
        return runCatching {
            val raw = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            ).records
            val filtered = if (preferredOrigin != null) raw.filter { it.metadata.dataOrigin == preferredOrigin }.ifEmpty { raw } else raw
            val byHour = linkedMapOf<Long, Long>()
            for (r in filtered) {
                val hourTs = (r.startTime.toEpochMilli() / 3_600_000L) * 3_600_000L
                byHour[hourTs] = (byHour[hourTs] ?: 0L) + r.count
            }
            byHour.entries
                .filter { it.value > 0L }
                .sortedBy { it.key }
                .map { it.key to it.value.toInt() }
        }.getOrDefault(emptyList())
    }

    private suspend fun readHourlyHeartRate(
        client: HealthConnectClient,
        range: TimeRangeFilter,
    ): List<Pair<Long, Int>> = runCatching {
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = range,
            )
        )
        val bucketSumCount = linkedMapOf<Long, Pair<Long, Int>>()
        for (rec in resp.records) {
            for (sample in rec.samples) {
                val bpm = sample.beatsPerMinute
                if (bpm !in 25L..240L) continue
                val bucketTs = (sample.time.toEpochMilli() / 3_600_000L) * 3_600_000L
                val prev = bucketSumCount[bucketTs]
                bucketSumCount[bucketTs] = ((prev?.first ?: 0L) + bpm) to ((prev?.second ?: 0) + 1)
            }
        }
        bucketSumCount.entries
            .sortedBy { it.key }
            .map { (ts, sc) -> ts to (sc.first.toDouble() / sc.second).roundToInt() }
    }.getOrDefault(emptyList())

    private fun exerciseLabelDe(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Laufen"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Gehen"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Radfahren"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Schwimmen"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Wandern"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Krafttraining"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        else -> "Training"
    }
}
