package dev.xdripyou.app.data

import dev.xdripyou.app.data.db.ActiveAlertEntity
import dev.xdripyou.app.data.db.AlertRuleEntity
import dev.xdripyou.app.data.db.AppDatabase
import dev.xdripyou.app.data.db.BgReadingEntity
import dev.xdripyou.app.data.db.CalibrationEntity
import dev.xdripyou.app.data.db.CollectorEventEntity
import dev.xdripyou.app.data.db.ReminderEntity
import dev.xdripyou.app.data.db.TreatmentEntity
import dev.xdripyou.app.domain.ActiveAlertState
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.CalibrationEngine
import dev.xdripyou.app.domain.CalibrationRecord
import dev.xdripyou.app.domain.CamApsParser
import dev.xdripyou.app.domain.CollectorEvent
import dev.xdripyou.app.domain.ExternalPumpStatus
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.IobCobCalculator
import dev.xdripyou.app.domain.IobCobSnapshot
import dev.xdripyou.app.domain.ReminderItem
import dev.xdripyou.app.domain.SlopeIntercept
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.domain.TrendArrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _externalPumpStatus = MutableStateFlow<ExternalPumpStatus?>(null)
    val externalPumpStatusFlow: Flow<ExternalPumpStatus?> = _externalPumpStatus.asStateFlow()

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

    suspend fun getReadingsSince(sinceMs: Long, limit: Int = 30_000): List<GlucoseReading> =
        db.bgReadingDao().getSince(sinceMs, limit).map { it.toDomain() }

    suspend fun getTreatmentsSince(sinceMs: Long, limit: Int = 500): List<TreatmentEntry> =
        db.treatmentDao().getSince(sinceMs, limit).map { it.toDomain() }

    suspend fun currentIobCob(atMs: Long = System.currentTimeMillis()): IobCobSnapshot {
        val settings = settingsStore.current()
        val recent = db.treatmentDao().getSince(atMs - 12 * 3_600_000L, 200).map { it.toDomain() }
        return IobCobCalculator.snapshot(recent, atMs, settings)
    }

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

    suspend fun syncNightscout(uploaderBatteryPercent: Int? = null): Result<String> {
        val settings = settingsStore.current()
        if (settings.nightscoutUrl.isBlank()) {
            return Result.failure(IllegalStateException("Keine Nightscout-URL hinterlegt"))
        }

        var pulled = 0
        var pulledTreatments = 0
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

            val trRes = NightscoutClient.fetchLatestTreatments(settings.nightscoutUrl, settings.nightscoutApiSecret, count = 36)
            if (trRes.isSuccess) {
                val existing = db.treatmentDao().getLatest(200).map { it.toDomain() }
                for (remote in trRes.getOrThrow()) {
                    val duplicate = existing.any { local ->
                        kotlin.math.abs(local.timestampMs - remote.timestampMs) <= 60_000L &&
                            kotlin.math.abs(local.insulinUnits - remote.insulinUnits) < 0.01 &&
                            kotlin.math.abs(local.carbsGrams - remote.carbsGrams) < 0.1 &&
                            local.note == remote.note
                    }
                    if (!duplicate) {
                        db.treatmentDao().upsert(TreatmentEntity.fromDomain(remote))
                        pulledTreatments++
                    }
                }
            }

            if (settings.nightscoutDeviceStatusEnabled) {
                NightscoutClient.fetchLatestDeviceStatus(settings.nightscoutUrl, settings.nightscoutApiSecret)
                    .getOrNull()?.let { _externalPumpStatus.value = it }
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

            if (settings.nightscoutUploadTreatments) {
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

            if (settings.nightscoutDeviceStatusEnabled) {
                val iobCob = currentIobCob()
                NightscoutClient.uploadDeviceStatus(
                    baseUrl = settings.nightscoutUrl,
                    apiSecret = settings.nightscoutApiSecret,
                    uploaderBatteryPercent = uploaderBatteryPercent,
                    iobUnits = if (iobCob.hasActiveIob) iobCob.iobUnits else null,
                    cobGrams = if (iobCob.hasActiveCob) iobCob.cobGrams else null,
                )
            }
        }

        return Result.success("Sync OK (↓ $pulled Werte / $pulledTreatments Eintr., ↑ $uploadedReadings Werte / $uploadedTreatments Eintr.)")
    }

    suspend fun syncDexcomShare(): Result<String> {
        val settings = settingsStore.current()
        val res = DexcomShareClient.fetchLatestReadings(
            username = settings.dexcomShareUsername,
            password = settings.dexcomSharePassword,
            usAccount = settings.dexcomShareUsAccount,
            count = 36,
        )
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        var pulled = 0
        for (item in res.getOrThrow().sortedBy { it.timestampMs }) {
            val out = insertReading(
                mgdl = item.mgdl,
                timestampMs = item.timestampMs,
                source = "Dexcom Share",
                rawText = null,
                applyDeduplication = false,
                overrideTrend = item.trend,
            )
            if (out is InsertOutcome.Inserted) pulled++
        }
        return Result.success("Dexcom Share OK (↓ $pulled neue Werte)")
    }

    suspend fun syncLibreLinkUp(): Result<String> {
        val settings = settingsStore.current()
        val res = LibreLinkUpClient.fetchLatestReadings(
            email = settings.libreLinkEmail,
            password = settings.libreLinkPassword,
            region = LibreLinkRegion.fromWire(settings.libreLinkRegion),
        )
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        var pulled = 0
        for (item in res.getOrThrow()) {
            val out = insertReading(
                mgdl = item.mgdl,
                timestampMs = item.timestampMs,
                source = "LibreLinkUp",
                rawText = null,
                applyDeduplication = false,
                overrideTrend = item.trend,
            )
            if (out is InsertOutcome.Inserted) pulled++
        }
        return Result.success("LibreLinkUp OK (↓ $pulled neue Werte)")
    }

    suspend fun syncHealthConnect(context: android.content.Context): Result<String> {
        val settings = settingsStore.current()
        if (!settings.healthConnectEnabled) return Result.success("Health Connect deaktiviert")
        var sent = 0
        var received = 0

        if (settings.healthConnectSend) {
            val recentReadings = getReadingsSince(System.currentTimeMillis() - 12 * 3_600_000L, 200)
            val sendRes = HealthConnectSyncManager.writeReadings(context, recentReadings)
            if (sendRes.isFailure) return Result.failure(sendRes.exceptionOrNull()!!)
            sent = sendRes.getOrDefault(0)

            val recentTreatments = getTreatmentsSince(System.currentTimeMillis() - 12 * 3_600_000L, 100)
            HealthConnectSyncManager.writeCarbTreatments(context, recentTreatments)
        }

        if (settings.healthConnectReceive) {
            val readRes = HealthConnectSyncManager.readRecentReadings(context, hoursBack = 12L)
            if (readRes.isFailure) return Result.failure(readRes.exceptionOrNull()!!)
            for ((ts, mgdl) in readRes.getOrThrow().sortedBy { it.first }) {
                val out = insertReading(
                    mgdl = mgdl,
                    timestampMs = ts,
                    source = "Health Connect",
                    rawText = null,
                    applyDeduplication = false,
                )
                if (out is InsertOutcome.Inserted) received++
            }
        }
        return Result.success("Health Connect OK (↑ $sent, ↓ $received)")
    }

    suspend fun syncInfluxDb(): Result<String> {
        val settings = settingsStore.current()
        val recentReadings = getReadingsSince(System.currentTimeMillis() - 12 * 3_600_000L, 200)
        val recentTreatments = getTreatmentsSince(System.currentTimeMillis() - 12 * 3_600_000L, 100)
        val res = InfluxDbClient.upload(
            baseUrl = settings.influxUrl,
            databaseOrBucket = settings.influxDatabase,
            org = settings.influxOrg,
            username = settings.influxUsername,
            tokenOrPassword = settings.influxTokenOrPassword,
            readings = recentReadings,
            treatments = recentTreatments,
        )
        return res.map { count -> "InfluxDB OK (↑ $count Werte)" }
    }

    suspend fun syncTidepool(): Result<String> {
        val settings = settingsStore.current()
        val recentReadings = getReadingsSince(System.currentTimeMillis() - 24 * 3_600_000L, 350)
        val recentTreatments = getTreatmentsSince(System.currentTimeMillis() - 24 * 3_600_000L, 120)
        val res = TidepoolClient.upload(
            email = settings.tidepoolEmail,
            password = settings.tidepoolPassword,
            useDevServers = settings.tidepoolDevServers,
            readings = recentReadings,
            treatments = recentTreatments,
        )
        return res.map { count -> "Tidepool OK (↑ $count Datensätze)" }
    }

    suspend fun syncPeerMaster(): Result<String> {
        val settings = settingsStore.current()
        val res = XdripPeerSyncClient.fetchFromMaster(
            masterHostOrUrl = settings.peerSyncHost,
            apiSecret = settings.peerSyncSecret,
            count = 48,
        )
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        val bundle = res.getOrThrow()
        var pulled = 0
        for (e in bundle.entries.sortedBy { it.timestampMs }) {
            val out = insertReading(
                mgdl = e.sgvMgdl,
                timestampMs = e.timestampMs,
                source = "xDrip Peer",
                rawText = null,
                applyDeduplication = false,
                overrideTrend = NightscoutClient.directionToTrend(e.direction),
            )
            if (out is InsertOutcome.Inserted) pulled++
        }
        val existing = db.treatmentDao().getLatest(200).map { it.toDomain() }
        var pulledTr = 0
        for (remote in bundle.treatments) {
            val duplicate = existing.any { local ->
                kotlin.math.abs(local.timestampMs - remote.timestampMs) <= 60_000L &&
                    kotlin.math.abs(local.insulinUnits - remote.insulinUnits) < 0.01 &&
                    kotlin.math.abs(local.carbsGrams - remote.carbsGrams) < 0.1
            }
            if (!duplicate) {
                db.treatmentDao().upsert(TreatmentEntity.fromDomain(remote.copy(syncedNightscout = false)))
                pulledTr++
            }
        }
        return Result.success("Peer-Sync OK (↓ $pulled Werte / $pulledTr Eintr.)")
    }

    suspend fun exportBackupJson(): String {
        val readings = db.bgReadingDao().getLatest(2000).map { it.toDomain() }
        val treatments = db.treatmentDao().getLatest(500).map { it.toDomain() }
        val rules = getAlertRules()
        return BackupManager.encodeToJson(BackupPayload(readings, treatments, rules))
    }

    suspend fun exportReadingsCsv(): String {
        val readings = db.bgReadingDao().getLatest(5000).map { it.toDomain() }
        return BackupManager.encodeReadingsToCsv(readings)
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

    suspend fun pruneExpiredReadings() {
        val days = settingsStore.current().retentionDays.coerceIn(7, 3650)
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        db.bgReadingDao().deleteOlderThan(cutoff)
    }

    suspend fun getWatchedPackages(): Set<String> {
        val settings = settingsStore.current()
        val base = if (settings.includeAllCompanionApps) {
            CamApsParser.extendedCompanionPackages
        } else {
            CamApsParser.defaultCamApsPackages
        }
        val custom = settings.customPackages
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return base + custom
    }

    // --- Calibrations (WLS Linear Regression) ---

    fun observeCalibrations(limit: Int = 50): Flow<List<CalibrationRecord>> =
        db.calibrationDao().observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getRecentCalibrations(limit: Int = 30): List<CalibrationRecord> =
        db.calibrationDao().getRecent(limit).map { it.toDomain() }

    suspend fun currentSlopeIntercept(): SlopeIntercept {
        val cals = getRecentCalibrations(20)
        val algo = dev.xdripyou.app.domain.CalibrationAlgorithm.fromWire(settingsStore.current().calibrationAlgorithm)
        return CalibrationEngine.calculateByAlgorithm(cals, algo)
    }

    suspend fun addCalibration(
        bgMgdl: Double,
        rawValue: Double? = null,
        timestampMs: Long = System.currentTimeMillis(),
        sensorId: String = "",
        note: String = "Blutzucker-Kalibrierung",
    ): SlopeIntercept {
        val latestReading = getLatestReadings(1).firstOrNull()
        val effectiveRaw = rawValue ?: latestReading?.mgdl ?: bgMgdl
        val prior = getRecentCalibrations(20)
        val previewList = prior + CalibrationRecord(
            timestampMs = timestampMs,
            bgMgdl = bgMgdl,
            rawValue = effectiveRaw,
            slope = 1.0,
            intercept = 0.0,
            sensorId = sensorId,
            note = note,
        )
        val wls = CalibrationEngine.calculateWls(previewList)
        db.calibrationDao().upsert(
            CalibrationEntity.fromDomain(
                CalibrationRecord(
                    timestampMs = timestampMs,
                    bgMgdl = bgMgdl,
                    rawValue = effectiveRaw,
                    slope = wls.slope,
                    intercept = wls.intercept,
                    sensorId = sensorId,
                    note = note,
                )
            )
        )
        // Also log fingerstick calibration as a BG Check treatment marker
        addOrUpdateTreatment(
            TreatmentEntry(
                timestampMs = timestampMs,
                bloodGlucoseMgdl = bgMgdl,
                note = "Kalibrierung",
                eventType = "BG Check",
            )
        )
        return wls
    }

    suspend fun deleteCalibration(id: Long) = db.calibrationDao().deleteById(id)

    suspend fun clearCalibrations() = db.calibrationDao().deleteAll()

    // --- Reminders ---

    fun observeReminders(): Flow<List<ReminderItem>> =
        db.reminderDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getReminders(): List<ReminderItem> =
        db.reminderDao().getAll().map { it.toDomain() }

    suspend fun upsertReminder(item: ReminderItem): Long =
        db.reminderDao().upsert(ReminderEntity.fromDomain(item))

    suspend fun deleteReminder(id: Long) =
        db.reminderDao().deleteById(id)

    // --- CareLink & WebFollow Sync ---

    suspend fun syncCareLink(): Result<String> {
        val settings = settingsStore.current()
        if (!settings.careLinkEnabled) return Result.failure(IllegalStateException("CareLink deaktiviert"))
        val region = CareLinkRegion.fromWire(settings.careLinkRegion)
        val res = CareLinkFollowClient.fetchLatest(region, settings.careLinkUsername, settings.careLinkPassword)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        var inserted = 0
        val readings = res.getOrThrow()
        for (r in readings) {
            val out = insertReading(
                mgdl = r.mgdl,
                timestampMs = r.timestampMs,
                source = "CareLink (${region.wireValue.uppercase()})",
                rawText = null,
                applyDeduplication = false,
                overrideTrend = r.trend,
            )
            if (out is InsertOutcome.Inserted) inserted++
        }
        val last = readings.lastOrNull()
        if (last?.activeInsulin != null || last?.pumpBatteryPercent != null) {
            _externalPumpStatus.value = ExternalPumpStatus(
                timestampMs = last.timestampMs,
                iobUnits = last.activeInsulin,
                pumpBatteryPercent = last.pumpBatteryPercent,
            )
        }
        return Result.success("CareLink OK · $inserted neu (${readings.size} gelesen)")
    }

    suspend fun syncWebFollow(): Result<String> {
        val settings = settingsStore.current()
        if (!settings.webFollowEnabled) return Result.failure(IllegalStateException("WebFollow deaktiviert"))
        val res = WebFollowClient.fetchLatest(settings.webFollowUrl, settings.webFollowJsonPath)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        var inserted = 0
        for (r in res.getOrThrow()) {
            val out = insertReading(
                mgdl = r.mgdl,
                timestampMs = r.timestampMs,
                source = "WebFollow",
                rawText = null,
                applyDeduplication = false,
                overrideTrend = r.trend,
            )
            if (out is InsertOutcome.Inserted) inserted++
        }
        return Result.success("WebFollow OK · $inserted neu")
    }
}
