package dev.xdripyou.app.service

import dev.xdripyou.app.XdripYouApplication
import dev.xdripyou.app.data.SettingsStore
import dev.xdripyou.app.domain.AlertProfile
import dev.xdripyou.app.domain.TreatmentEntry

/**
 * Port of xDrip's Tasker / Automation command interpreter (`FireReceiver` & `WebServiceTasker`).
 *
 * Supported commands:
 * - `snooze [minutes]`
 * - `snooze_low [minutes]` / `snooze_high [minutes]` / `silence [minutes]` / `unsilence`
 * - `volume <ascending|high|medium|vibrate|silent>`
 * - `speak`
 * - `carbs <grams>` / `insulin <units>` / `bg <mgdl>`
 * - `sync`
 */
object TaskerCommandHandler {
    suspend fun execute(app: XdripYouApplication, rawCommand: String): String {
        val parts = rawCommand.trim().replace('/', ' ').split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "Kein Befehl angegeben"

        val cmd = parts[0].uppercase()
        val arg1 = parts.getOrNull(1)
        return when (cmd) {
            "SNOOZE" -> {
                val mins = arg1?.toIntOrNull()?.coerceIn(1, 720) ?: 0
                app.alertEngine.snoozeActiveAlert(mins)
                "Alarm geschlummert"
            }
            "SNOOZE_LOW" -> {
                val mins = arg1?.toIntOrNull()?.coerceIn(0, 720) ?: 35
                val until = if (mins == 0) 0L else System.currentTimeMillis() + mins * 60_000L
                app.repository.settingsStore.update { it[SettingsStore.Keys.LOW_DISABLED_UNTIL] = until }
                "Niedrig-Alarme stumm (${mins}m)"
            }
            "SNOOZE_HIGH" -> {
                val mins = arg1?.toIntOrNull()?.coerceIn(0, 720) ?: 120
                val until = if (mins == 0) 0L else System.currentTimeMillis() + mins * 60_000L
                app.repository.settingsStore.update { it[SettingsStore.Keys.HIGH_DISABLED_UNTIL] = until }
                "Hoch-Alarme stumm (${mins}m)"
            }
            "SILENCE", "SNOOZE_ALL" -> {
                val mins = arg1?.toIntOrNull()?.coerceIn(0, 720) ?: 60
                val until = if (mins == 0) 0L else System.currentTimeMillis() + mins * 60_000L
                app.repository.settingsStore.update { it[SettingsStore.Keys.ALERTS_DISABLED_UNTIL] = until }
                if (mins > 0) app.alertEngine.stopAlert(clearActive = true)
                "Alle Alarme stumm (${mins}m)"
            }
            "UNSILENCE" -> {
                app.repository.settingsStore.update {
                    it[SettingsStore.Keys.ALERTS_DISABLED_UNTIL] = 0L
                    it[SettingsStore.Keys.LOW_DISABLED_UNTIL] = 0L
                    it[SettingsStore.Keys.HIGH_DISABLED_UNTIL] = 0L
                }
                "Stummschaltung aufgehoben"
            }
            "VOLUME" -> {
                val profile = AlertProfile.entries.firstOrNull { it.wireValue.equals(arg1, ignoreCase = true) }
                    ?: return "Unbekanntes Profil: $arg1"
                app.repository.settingsStore.update { it[SettingsStore.Keys.ALERT_PROFILE] = profile.wireValue }
                "Profil: ${profile.labelDe}"
            }
            "SPEAK" -> {
                val latest = app.repository.getLatestReadings(1).firstOrNull()
                    ?: return "Kein Messwert zum Vorlesen"
                val s = app.repository.settingsStore.current()
                app.speechHelper.speakReading(latest, s.useMgdl, s.speakTrendEnabled, s.speechSpeed.toFloat())
                "Messwert vorgelesen"
            }
            "CARBS" -> {
                val grams = arg1?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 300.0 }
                    ?: return "Ungültige KH-Menge"
                app.repository.addOrUpdateTreatment(
                    TreatmentEntry(
                        timestampMs = System.currentTimeMillis(),
                        carbsGrams = grams,
                        eventType = "Carb Correction",
                        note = "Tasker",
                    )
                )
                app.alertEngine.refreshOngoingNotification()
                "KH gespeichert: ${grams.toInt()} g"
            }
            "INSULIN" -> {
                val units = arg1?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 100.0 }
                    ?: return "Ungültige Insulinmenge"
                app.repository.addOrUpdateTreatment(
                    TreatmentEntry(
                        timestampMs = System.currentTimeMillis(),
                        insulinUnits = units,
                        eventType = "Meal Bolus",
                        note = "Tasker",
                    )
                )
                app.alertEngine.refreshOngoingNotification()
                "Insulin gespeichert: $units IE"
            }
            "BG", "CAL" -> {
                val raw = arg1?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 20.0..600.0 }
                    ?: return "Ungültiger BZ-Wert"
                app.repository.addOrUpdateTreatment(
                    TreatmentEntry(
                        timestampMs = System.currentTimeMillis(),
                        bloodGlucoseMgdl = raw,
                        eventType = "BG Check",
                        note = "Tasker",
                    )
                )
                "BZ gespeichert: ${raw.toInt()} mg/dL"
            }
            "SYNC" -> {
                app.repository.syncNightscout()
                "Sync ausgeführt"
            }
            else -> "Unbekannter Befehl: $cmd"
        }
    }
}
