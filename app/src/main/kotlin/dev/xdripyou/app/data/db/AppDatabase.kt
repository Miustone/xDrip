package dev.xdripyou.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BgReadingEntity::class,
        TreatmentEntity::class,
        AlertRuleEntity::class,
        ActiveAlertEntity::class,
        CollectorEventEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bgReadingDao(): BgReadingDao
    abstract fun treatmentDao(): TreatmentDao
    abstract fun alertRuleDao(): AlertRuleDao
    abstract fun activeAlertDao(): ActiveAlertDao
    abstract fun collectorEventDao(): CollectorEventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bg_readings ADD COLUMN syncedNightscout INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS treatments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        insulinUnits REAL NOT NULL,
                        carbsGrams REAL NOT NULL,
                        bloodGlucoseMgdl REAL,
                        note TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        syncedNightscout INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_treatments_timestampMs ON treatments(timestampMs)")
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            lateinit var db: AppDatabase
            db = Room.databaseBuilder(context, AppDatabase::class.java, "xdrip_you.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(sqliteDb: SupportSQLiteDatabase) {
                        super.onCreate(sqliteDb)
                        CoroutineScope(Dispatchers.IO).launch {
                            db.alertRuleDao().insertAllIgnore(defaultRules())
                        }
                    }
                })
                .build()
            return db
        }

        fun defaultRules(): List<AlertRuleEntity> = listOf(
            AlertRuleEntity(
                uuid = "c5f1999c-4ec5-449e-adad-3980b172b920",
                name = "Dringend Niedrig",
                isHigh = false,
                thresholdMgdl = 55.0,
                enabled = true,
                allDay = true,
                startMinutes = 0,
                endMinutes = 0,
                minutesBetween = 5,
                defaultSnoozeMinutes = 20,
                overrideSilent = true,
                vibrate = true,
                soundUri = null,
            ),
            AlertRuleEntity(
                uuid = "low-70-default",
                name = "Niedrig",
                isHigh = false,
                thresholdMgdl = 70.0,
                enabled = true,
                allDay = true,
                startMinutes = 0,
                endMinutes = 0,
                minutesBetween = 10,
                defaultSnoozeMinutes = 30,
                overrideSilent = false,
                vibrate = true,
                soundUri = null,
            ),
            AlertRuleEntity(
                uuid = "high-180-default",
                name = "Hoch",
                isHigh = true,
                thresholdMgdl = 180.0,
                enabled = true,
                allDay = true,
                startMinutes = 0,
                endMinutes = 0,
                minutesBetween = 20,
                defaultSnoozeMinutes = 60,
                overrideSilent = false,
                vibrate = true,
                soundUri = null,
            ),
            AlertRuleEntity(
                uuid = "urgent-high-250-default",
                name = "Sehr Hoch",
                isHigh = true,
                thresholdMgdl = 250.0,
                enabled = false,
                allDay = true,
                startMinutes = 0,
                endMinutes = 0,
                minutesBetween = 15,
                defaultSnoozeMinutes = 45,
                overrideSilent = false,
                vibrate = true,
                soundUri = null,
            ),
        )
    }
}
