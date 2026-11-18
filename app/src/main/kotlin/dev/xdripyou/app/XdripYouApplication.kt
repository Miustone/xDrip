package dev.xdripyou.app

import android.app.Application
import dev.xdripyou.app.data.GlucoseRepository
import dev.xdripyou.app.data.SettingsStore
import dev.xdripyou.app.data.db.AppDatabase
import dev.xdripyou.app.service.AlertEngine
import dev.xdripyou.app.service.NotificationChannels

class XdripYouApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val repository: GlucoseRepository by lazy { GlucoseRepository(database, settingsStore) }
    val alertEngine: AlertEngine by lazy { AlertEngine(this, repository) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAll(this)
    }
}
