package dev.xdripyou.app.data

import dev.xdripyou.app.data.db.ActiveAlertEntity
import dev.xdripyou.app.data.db.AlertRuleEntity
import dev.xdripyou.app.data.db.AppDatabase
import dev.xdripyou.app.data.db.BgReadingEntity
import dev.xdripyou.app.data.db.CollectorEventEntity
import dev.xdripyou.app.domain.ActiveAlertState
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.CamApsParser
import dev.xdripyou.app.domain.CollectorEvent
import dev.xdripyou.app.domain.GlucoseReading
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

    fun observeAlertRules(): Flow<List<AlertRule>> =
        db.alertRuleDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveAlert(): Flow<ActiveAlertState?> =
        db.activeAlertDao().observeActive().map { it?.toDomain() }

    fun observeCollectorEvents(limit: Int = 50): Flow<List<CollectorEvent>> =
        db.collectorEventDao().observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getLatestReadings(limit: Int): List<GlucoseReading> =
        db.bgReadingDao().getLatest(limit).map { it.toDomain() }

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
    ): InsertOutcome = insertMutex.withLock {
        val settings = settingsStore.current()
        val latest = db.bgReadingDao().getLatest(1).firstOrNull()?.toDomain()
        val roundedMgdl = mgdl.roundToInt().toDouble()

        if (applyDeduplication && latest != null) {
            val sameValue = latest.mgdl.roundToInt() == roundedMgdl.roundToInt()
            // Matches xDrip UiBasedCollector: 10s if changed, 250s (samplePeriod - samplePeriod/6) if unchanged
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

        val trend = if (latest != null && timestampMs > latest.timestampMs && (timestampMs - latest.timestampMs) <= 1_800_000L) {
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
        )
        val rowId = db.bgReadingDao().insert(entity)
        if (rowId == -1L) {
            return@withLock InsertOutcome.Deduplicated(timestampMs)
        }
        InsertOutcome.Inserted(entity.copy(id = rowId).toDomain())
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
    }

    suspend fun clearAllReadings() {
        db.bgReadingDao().deleteAll()
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
