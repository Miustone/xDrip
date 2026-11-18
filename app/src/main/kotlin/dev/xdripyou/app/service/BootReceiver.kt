package dev.xdripyou.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.xdripyou.app.XdripYouApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as XdripYouApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.settingsStore.current().foregroundServiceEnabled &&
                    SystemPermissionsHelper.hasPostNotifications(context)
                ) {
                    GlucoseForegroundService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
