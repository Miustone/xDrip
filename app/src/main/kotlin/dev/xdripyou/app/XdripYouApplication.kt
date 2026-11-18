package dev.xdripyou.app

import android.app.Application
import dev.xdripyou.app.data.GlucoseRepository
import dev.xdripyou.app.data.SettingsStore
import dev.xdripyou.app.data.db.AppDatabase
import dev.xdripyou.app.service.AlertEngine
import dev.xdripyou.app.service.LocalWebServer
import dev.xdripyou.app.service.NotificationChannels
import dev.xdripyou.app.service.SpeechHelper
import dev.xdripyou.app.service.TaskerCommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XdripYouApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val repository: GlucoseRepository by lazy { GlucoseRepository(database, settingsStore) }
    val speechHelper: SpeechHelper by lazy { SpeechHelper(this) }
    val alertEngine: AlertEngine by lazy { AlertEngine(this, repository, speechHelper) }
    val localWebServer: LocalWebServer by lazy {
        LocalWebServer(repository) { cmd -> TaskerCommandHandler.execute(this, cmd) }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAll(this)
        appScope.launch {
            localWebServer.syncWithSettings(settingsStore.current())
        }
    }
}
