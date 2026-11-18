# Changelog - xDrip 2 You

Alle nennenswerten Änderungen in diesem lokalen xDrip-Fork (Branch `kotlin-jetpack-you`, aufbauend auf `NightscoutFoundation/xDrip`) werden in dieser Datei dokumentiert.

Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.0.0/), und das Projekt nutzt das Release-Schema **`YY.MM.AppVersion`** (`versionCode = YY * 10000 + MM * 100 + AppVersion`).

---

## [26.11.3] - 2026-11-18

### Hinzugefügt & Verbessert
- **Aufgeräumter Material 3 You Options-Hub (`SettingsScreen.kt` & `XdripYouRoot.kt`):**
  - Ersetzt die lange Einstellungsliste durch einen übersichtlichen **Optionen-Hub** mit Live-Suchleiste und 5 klaren Untermenüs (*Datenquellen & Sensor*, *Alarme & Klingeltöne*, *Anzeige & Material 3 You*, *Cloud, Broadcast & Export*, *Diagnose, Labor & Info*).
- **Erweiterte xDrip-Funktionen:**
  - **Eigener Alarm-Klingelton:** Auswahl über den Android `RingtoneManager` inkl. Zurücksetzen auf den xDrip-Standardton (`default_alert.mp3`).
  - **Anruf-Stummschaltung & Stale-Alarm-Sperre (`AlertEngine.kt`):** Unterdrückt Alarme während laufender Telefonate (`noAlarmsDuringCalls`) sowie optional bei veralteten Daten (`disableAlertsStaleData`).
  - **Sensor-Session-Tracking & konfigurierbare „Veraltet“-Schwelle:** Sensor-Startzeit mit Anzeige von Tagen/Stunden in der Übersicht (`OverviewScreen.kt`).
  - **Kompakte Status-Benachrichtigung & Nachttisch-Modus:** `compactOngoingNotification` und `FLAG_KEEP_SCREEN_ON` (`keepScreenOn`).
  - **CSV-Export (`BackupManager.kt`):** Exportiert Blutzuckerwerte direkt als `.csv` zusätzlich zum JSON-Backup.
  - **In-App Changelog-Dialog** und Versionsschema `YY.MM.AppVersion` (`versionName = "26.11.3"`, `versionCode = 261103`).

---

## [26.11.2] - 2026-11-18

### Hinzugefügt & Umbenannt
- **Umbenennung zu `xDrip 2 You`:** App-Name, Launcher, TopBar, laufende Status-Benachrichtigung und Widget.
- **Therapie-Tagebuch (Treatments) & Chart-Marker:** Erfassung von Insulin (IE), Kohlenhydraten (g), Fingerstich-BZ und Notizen (`TreatmentEditorSheet.kt`, Room `MIGRATION_1_2`) inklusive Markierung direkt im Verlauf-Chart.
- **30-Min.-Vorhersage & erweiterte Alarme:** Gedämpfte Trend-Prognose (`GlucosePredictor.kt`), **Niedrig-Vorhersage-Alarm** und **Anhaltend-Hoch-Alarm** (`AlertEvaluator.kt`, `AlertEngine.kt`).
- **Nightscout Integration (`NightscoutClient.kt`):** REST-v1-Follower-Abruf und Upload für Messwerte & Behandlungen mit SHA-1 `api-secret`.
- **Lokaler xDrip-Broadcast (`XdripBroadcastSender.kt`):** Sendet `com.eveningoutpost.dexdrip.BgEstimate` für AAPS, Watchfaces & Tasker.
- **Material You Homescreen-Widget (`GlucoseWidgetProvider.kt`)** und **JSON-Backup Export/Import (`BackupManager.kt`)**.

---

## [26.11.1] - 2026-11-18

### Hinzugefügt
- **Komplette Neuimplementierung in 100 % Kotlin + Jetpack Compose + Material 3 You** auf Branch `kotlin-jetpack-you` (unveränderter Upstream bleibt auf `master`).
- **CamAPS FX Companion App Collector (`CamApsNotificationListenerService.kt` & `CamApsParser.kt`):** Übernimmt laufende Benachrichtigungen von `com.camdiab.fx_alert.*` (`mg/dL` & `mmol/L`) inkl. Deduplizierung, Jam-Schutz und Live-Protokoll.
- **Übersicht, Chart, Verlauf & Alarm-Engine:** Interaktiver Canvas-Chart (`3h / 6h / 12h / 24h`), TIR/GMI/CV%-Statistiken, Schwellwertalarme mit Lautlos-Umgehung, Schlummern/Stopp-Benachrichtigungen, Smart Alerting/Snoozing und Foreground Service.
