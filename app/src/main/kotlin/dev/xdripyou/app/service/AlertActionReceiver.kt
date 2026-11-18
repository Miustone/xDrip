package dev.xdripyou.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.xdripyou.app.XdripYouApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as XdripYouApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent?.action) {
                    ACTION_SNOOZE -> {
                        val mins = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 20)
                        app.alertEngine.snoozeActiveAlert(mins)
                    }
                    ACTION_STOP -> {
                        app.alertEngine.stopAlert(clearActive = true)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "dev.xdripyou.app.ACTION_SNOOZE"
        const val ACTION_STOP = "dev.xdripyou.app.ACTION_STOP"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }
}
