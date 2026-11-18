package dev.xdripyou.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BgReadingDao {
    @Query("SELECT * FROM bg_readings WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC LIMIT :limit")
    fun observeSince(sinceMs: Long, limit: Int = 600): Flow<List<BgReadingEntity>>

    @Query("SELECT * FROM bg_readings ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<BgReadingEntity>

    @Query("SELECT * FROM bg_readings WHERE syncedNightscout = 0 ORDER BY timestampMs ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 100): List<BgReadingEntity>

    @Query("UPDATE bg_readings SET syncedNightscout = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BgReadingEntity): Long

    @Query("DELETE FROM bg_readings WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM bg_readings")
    suspend fun deleteAll()
}

@Dao
interface TreatmentDao {
    @Query("SELECT * FROM treatments WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC LIMIT :limit")
    fun observeSince(sinceMs: Long, limit: Int = 300): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getLatest(limit: Int = 300): List<TreatmentEntity>

    @Query("SELECT * FROM treatments WHERE syncedNightscout = 0 ORDER BY timestampMs ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 50): List<TreatmentEntity>

    @Query("UPDATE treatments SET syncedNightscout = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TreatmentEntity): Long

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM treatments")
    suspend fun deleteAll()
}

@Dao
interface AlertRuleDao {
    @Query("SELECT * FROM alert_rules ORDER BY isHigh ASC, thresholdMgdl ASC")
    fun observeAll(): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alert_rules ORDER BY isHigh ASC, thresholdMgdl ASC")
    suspend fun getAll(): List<AlertRuleEntity>

    @Query("SELECT * FROM alert_rules WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): AlertRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AlertRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<AlertRuleEntity>)

    @Query("DELETE FROM alert_rules WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)
}

@Dao
interface ActiveAlertDao {
    @Query("SELECT * FROM active_alert WHERE id = 1")
    fun observeActive(): Flow<ActiveAlertEntity?>

    @Query("SELECT * FROM active_alert WHERE id = 1")
    suspend fun getActive(): ActiveAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ActiveAlertEntity)

    @Query("DELETE FROM active_alert")
    suspend fun clear()
}

@Dao
interface CollectorEventDao {
    @Query("SELECT * FROM collector_events ORDER BY timestampMs DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<CollectorEventEntity>>

    @Insert
    suspend fun insert(entity: CollectorEventEntity)

    @Query("DELETE FROM collector_events WHERE id NOT IN (SELECT id FROM collector_events ORDER BY id DESC LIMIT 100)")
    suspend fun trimOld()

    @Query("DELETE FROM collector_events")
    suspend fun deleteAll()
}
