package dev.xdripyou.app.data

import dev.xdripyou.app.data.db.ActiveAlertEntity
import dev.xdripyou.app.data.db.AlertRuleEntity
import dev.xdripyou.app.data.db.AppDatabase
import dev.xdripyou.app.data.db.BgReadingEntity
import dev.xdripyou.app.data.db.CollectorEventEntity
import dev.xdripyou.app.data.db.TreatmentEntity
import dev.xdripyou.app.domain.ActiveAlertState
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.CamApsParser
import dev.xdripyou.app.domain.CollectorEvent
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

sealed class InsertOutcome {
    data class Inserted(val reading: GlucoseReading) : InsertOutcome()
    data class Deduplicated(val existingTimestampMs: Long) : InsertOutcome()
    data class Jammed(val valueMgdl: Double, val repeatCount: Long) : InsertOutcome()
}

class GlucoseRepository(
    private val db: AppDatabase,
    val settingsStore: SettingsStore,
) {
    private val insertMutex = Mutex()

    fun observeReadingsSince(sinceMs: Long, limit: Int = 600): Flow<List<GlucoseReading>> =
        db.bgReadingDao().observeSince(sinceMs, limit).map { list -> list.map { it.toDomain() } }

    fun observeTreatmentsSince(sinceMs: Long, limit: Int = 300): Flow<List<TreatmentEntry>> =
        db.treatmentDao().observeSince(sinceMs, limit).map { list -> list.map { it.toDomain() } }

    fun observeAlertRules(): Flow<List<AlertRule>> =
        db.alertRuleDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveAlert(): Flow<ActiveAlertState?> =
        db.activeAlertDao().observeActive().map { it?.toDomain() }

    fun observeCollectorEvents(limit: Int = 50): Flow<List<CollectorEvent>> =
        db.collectorEventDao().observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getLatestReadings(limit: Int): List<GlucoseReading> =
        db.bgReadingDao().getLatest(limit).map { it.toDomain() }

    suspend fun addOrUpdateTreatment(entry: TreatmentEntry): Long =
        db.treatmentDao().upsert(TreatmentEntity.fromDomain(entry))

    suspend fun deleteTreatment(id: Long) =
        db.treatmentDao().deleteById(id)

    suspend fun getAlertRules(): List<AlertRule> {
        val existing = db.alertRuleDao().getAll()
        if (existing.isEmpty()) {
            db.alertRuleDao().insertAllIgnore(AppDatabase.defaultRules())
            return db.alertRuleDao().getAll().map { it.toDomain() }
        }
        return existing.map { it.toDomain() }
    }

    suspend fun getAlertRule(uuid: String): AlertRule? =
        db.alertRuleDao().getByUuid(uuid)?.toDomain()

    suspend fun upsertAlertRule(rule: AlertRule) {
        db.alertRuleDao().upsert(AlertRuleEntity.fromDomain(rule))
    }

    suspend fun deleteAlertRule(uuid: String) {
        db.alertRuleDao().deleteByUuid(uuid)
        val active = db.activeAlertDao().getActive()
        if (active?.ruleUuid == uuid) {
            db.activeAlertDao().clear()
        }
    }

    suspend fun getActiveAlert(): ActiveAlertState? =
        db.activeAlertDao().getActive()?.toDomain()

    suspend fun saveActiveAlert(state: ActiveAlertState) {
        db.activeAlertDao().save(ActiveAlertEntity.fromDomain(state))
    }

    suspend fun clearActiveAlert() {
        db.activeAlertDao().clear()
    }

    suspend fun insertReading(
        mgdl: Double,
        timestampMs: Long,
        source: String,
        rawText: String?,
        applyDeduplication: Boolean,
        overrideTrend: TrendArrow? = null,
        syncedNightscout: Boolean = false,
    ): InsertOutcome = insertMutex.withLock {
        val settings = settingsStore.current()
        val latest = db.bgReadingDao().getLatest(1).firstOrNull()?.toDomain()
        val roundedMgdl = mgdl.roundToInt().toDouble()

        if (applyDeduplication && latest != null) {
            val sameValue = latest.mgdl.roundToInt() == roundedMgdl.roundToInt()
            val dedupPeriodMs = if (sameValue) 250_000L else 10_000L
            if (timestampMs - latest.timestampMs < dedupPeriodMs) {
                return@withLock InsertOutcome.Deduplicated(latest.timestampMs)
            }
        }

        if (applyDeduplication && settings.jamProtection) {
            val (lastVal, lastRepeat) = settingsStore.getCompanionLastState()
            val repeatCount = if (lastVal == roundedMgdl.roundToInt().toLong()) lastRepeat + 1L else 0L
            settingsStore.setCompanionLastState(roundedMgdl.roundToInt().toLong(), repeatCount)
            if (repeatCount > 6L) {
                return@withLock InsertOutcome.Jammed(roundedMgdl, repeatCount)
            }
        }

        val slopePerMin = if (latest != null && timestampMs > latest.timestampMs) {
            val dtMin = (timestampMs - latest.timestampMs) / 60_000.0
            if (dtMin > 0.0 && dtMin <= 30.0) (mgdl - latest.mgdl) / dtMin else 0.0
        } else {
            0.0
        }

        val trend = overrideTrend
            ?: if (latest != null && timestampMs > latest.timestampMs && (timestampMs - latest.timestampMs) <= 1_800_000L) {
                TrendArrow.fromSlopePerMin(slopePerMin)
            } else {
                TrendArrow.NOT_COMPUTABLE
            }

        val entity = BgReadingEntity(
            timestampMs = timestampMs,
            mgdl = mgdl,
            slopePerMin = slopePerMin,
            trendName = trend.wireName,
            source = source,
            rawText = rawText,
            syncedNightscout = syncedNightscout,
        )
        val rowId = db.bgReadingDao().insert(entity)
        if (rowId == -1L) {
            return@withLock InsertOutcome.Deduplicated(timestampMs)
        }
        InsertOutcome.Inserted(entity.copy(id = rowId).toDomain())
    }

    suspend fun syncNightscout(): Result<String> {
        val settings = settingsStore.current()
        if (settings.nightscoutUrl.isBlank()) {
            return Result.failure(IllegalStateException("Keine Nightscout-URL hinterlegt"))
        }

        var pulled = 0
        var uploadedReadings = 0
        var uploadedTreatments = 0

        if (settings.nightscoutFollowEnabled) {
            val res = NightscoutClient.fetchLatestEntries(settings.nightscoutUrl, settings.nightscoutApiSecret, count = 48)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
            val entries = res.getOrThrow().sortedBy { it.timestampMs }
            for (e in entries) {
                val out = insertReading(
                    mgdl = e.sgvMgdl,
                    timestampMs = e.timestampMs,
                    source = "Nightscout",
                    rawText = null,
                    applyDeduplication = false,
                    overrideTrend = NightscoutClient.directionToTrend(e.direction),
                    syncedNightscout = true,
                )
                if (out is InsertOutcome.Inserted) pulled++
            }
        }

        if (settings.nightscoutUploadEnabled) {
            val unsyncedReadings = db.bgReadingDao().getUnsynced(100)
            if (unsyncedReadings.isNotEmpty()) {
                val res = NightscoutClient.uploadReadings(
                    settings.nightscoutUrl,
                    settings.nightscoutApiSecret,
                    unsyncedReadings.map { it.toDomain() },
                )
                if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
                db.bgReadingDao().markSynced(unsyncedReadings.map { it.id })
                uploadedReadings = unsyncedReadings.size
            }

            val unsyncedTreatments = db.treatmentDao().getUnsynced(50)
            if (unsyncedTreatments.isNotEmpty()) {
                val res = NightscoutClient.uploadTreatments(
                    settings.nightscoutUrl,
                    settings.nightscoutApiSecret,
                    unsyncedTreatments.map { it.toDomain() },
                )
                if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
                db.treatmentDao().markSynced(unsyncedTreatments.map { it.id })
                uploadedTreatments = unsyncedTreatments.size
            }
        }

        return Result.success("Sync OK (↓ $pulled Werte, ↑ $uploadedReadings Werte / $uploadedTreatments Einträge)")
    }

    suspend fun exportBackupJson(): String {
        val readings = db.bgReadingDao().getLatest(2000).map { it.toDomain() }
        val treatments = db.treatmentDao().getLatest(500).map { it.toDomain() }
        val rules = getAlertRules()
        return BackupManager.encodeToJson(BackupPayload(readings, treatments, rules))
    }

    suspend fun importBackupJson(jsonText: String): String {
        val payload = BackupManager.decodeFromJson(jsonText)
        var importedReadings = 0
        for (r in payload.readings.sortedBy { it.timestampMs }) {
            val res = insertReading(
                mgdl = r.mgdl,
                timestampMs = r.timestampMs,
                source = r.source,
                rawText = r.rawText,
                applyDeduplication = false,
                overrideTrend = r.trend,
            )
            if (res is InsertOutcome.Inserted) importedReadings++
        }
        for (t in payload.treatments) {
            db.treatmentDao().upsert(TreatmentEntity.fromDomain(t.copy(id = 0L)))
        }
        for (rule in payload.alertRules) {
            db.alertRuleDao().upsert(AlertRuleEntity.fromDomain(rule))
        }
        return "Importiert: $importedReadings Werte, ${payload.treatments.size} Einträge, ${payload.alertRules.size} Alarme"
    }

    suspend fun recordCollectorEvent(
        packageName: String,
        status: String,
        rawTexts: String,
        parsedMgdl: Double?,
        message: String,
    ) {
        db.collectorEventDao().insert(
            CollectorEventEntity(
                timestampMs = System.currentTimeMillis(),
                packageName = packageName,
                status = status,
                rawTexts = rawTexts,
                parsedMgdl = parsedMgdl,
                message = message,
            )
        )
        db.collectorEventDao().trimOld()
    }

    suspend fun seedDemoCurve() {
        val now = System.currentTimeMillis()
        val totalPoints = 144 // 12 hours every 5 min
        for (i in totalPoints downTo 0) {
            val ts = now - i * 300_000L
            val phase = (totalPoints - i) / 14.0
            val base = 118.0 + 38.0 * sin(phase) + 18.0 * cos(phase * 0.55)
            val clamped = base.coerceIn(58.0, 225.0).roundToInt().toDouble()
            insertReading(
                mgdl = clamped,
                timestampMs = ts,
                source = "Demo-Verlauf",
                rawText = null,
                applyDeduplication = false,
            )
        }
        // Seed 2 sample treatments for demonstration
        addOrUpdateTreatment(
            TreatmentEntry(
                timestampMs = now - 3 * 3_600_000L,
                insulinUnits = 4.5,
                carbsGrams = 48.0,
                note = "Abendessen (Demo)",
                eventType = "Meal Bolus",
            )
        )
        addOrUpdateTreatment(
            TreatmentEntry(
                timestampMs = now - 45 * 60_000L,
                insulinUnits = 1.5,
                carbsGrams = 0.0,
                note = "Korrektur (Demo)",
                eventType = "Correction Bolus",
            )
        )
    }

    suspend fun clearAllReadings() {
        db.bgReadingDao().deleteAll()
        db.treatmentDao().deleteAll()
        db.collectorEventDao().deleteAll()
        settingsStore.resetCompanionJamState()
    }

    suspend fun getWatchedPackages(): Set<String> {
        val custom = settingsStore.current().customPackages
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return CamApsParser.defaultCamApsPackages + custom
    }
}
