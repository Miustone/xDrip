package dev.xdripyou.app

import android.app.Application
import dev.xdripyou.app.data.GlucoseRepository
import dev.xdripyou.app.data.SettingsStore
import dev.xdripyou.app.data.db.AppDatabase
import dev.xdripyou.app.service.AlertEngine
import dev.xdripyou.app.service.NotificationChannels
import dev.xdripyou.app.service.SpeechHelper

class XdripYouApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val repository: GlucoseRepository by lazy { GlucoseRepository(database, settingsStore) }
    val speechHelper: SpeechHelper by lazy { SpeechHelper(this) }
    val alertEngine: AlertEngine by lazy { AlertEngine(this, repository, speechHelper) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAll(this)
    }
}
