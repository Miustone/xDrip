# Changelog - xDrip 2 You

Alle nennenswerten Änderungen in diesem lokalen xDrip-Fork (Branch `kotlin-jetpack-you`, aufbauend auf `NightscoutFoundation/xDrip`) werden in dieser Datei dokumentiert.

Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.0.0/), und das Projekt nutzt das Release-Schema **`YY.MM.AppVersion`** (`versionCode = YY * 10000 + MM * 100 + AppVersion`).

---

## [26.11.9] - 2026-11-18

### Hinzugefügt & Portiert (Sync-Ökosystem komplettiert)
- **Android Health Connect (`HealthConnectSyncManager.kt`, `MainActivity.kt`, `AndroidManifest.xml`):**
  - Vollständige bidirektionale Integration über `androidx.health.connect:connect-client`: schreibt Blutzucker (`BloodGlucoseRecord`) und Kohlenhydrate (`NutritionRecord`), liest externe Glukosewerte und bindet Android-14+-Berechtigungen direkt ein.
- **InfluxDB Uploader v1 & v2 (`InfluxDbClient.kt`):**
  - Portierung von xDrips `InfluxDBUploader` (Line Protocol für `glucose` & `treatment`, unterstützt InfluxDB v1 `/write?db=` sowie v2 `/api/v2/write?org=&bucket=` mit Token).
- **Tidepool Uploader (`TidepoolClient.kt`):**
  - Portierung von xDrips `TidepoolUploader` (Login, Dataset-Session und Upload von `cbg`, `bolus`, `food` und `smbg` zu `api.tidepool.org` bzw. `int-api.tidepool.org`).
- **xDrip Peer-Sync / Master-Follower (`XdripPeerSyncClient.kt`):**
  - Direkter Geräte-zu-Geräte-Abruf (`/sgv.json` + `/treatments.json` auf Port `17580` inkl. optionalem `api-secret`) ohne Cloud-Server.
- **Versionsstand:** `versionName = "26.11.9"`, `versionCode = 261109`.

---

## [26.11.8] - 2026-11-18

### Hinzugefügt & Portiert
- **LibreLinkUp Follower (`LibreLinkUpClient.kt`, `GlucoseRepository.kt`, `GlucoseForegroundService.kt`):**
  - Direkter Cloud-Follower für FreeStyle LibreLinkUp (Regionen `EU`, `DE`, `US`, `Global` inkl. automatischem Region-Redirect) mit 5-Min.-Hintergrundabruf und Test-Button unter *Optionen → Datenquellen & Sensor*.
- **Tasker & Automation Bridge (`TaskerCommandHandler.kt`, `LocalWebServer.kt`, `XdripBroadcastReceiver.kt`):**
  - Portierung von xDrips `FireReceiver` & `WebServiceTasker` (`com.twofortyfouram.locale.intent.action.FIRE_SETTING`, `dev.xdripyou.app.TASKER_COMMAND` sowie `GET /tasker/<befehl>` auf Port `17580`).
  - Unterstützt `snooze`, `snooze_low`, `snooze_high`, `silence`, `unsilence`, `volume`, `speak`, `carbs`, `insulin`, `bg` und `sync`.
- **Hoch-Vorhersage-Alarm (`GlucosePredictor.minutesUntilAbove`, `AlertEngine.kt`, `AlertsScreen.kt`):**
  - Vervollständigt die prädiktive Alarmierung zusätzlich zur Niedrig-Vorhersage (`highPredictionEnabled` & konfigurierbarer Horizont).
- **24h-Therapie-Bilanz (`HistoryScreen.kt`):**
  - Zeigt im Verlauf direkt die tägliche Gesamt-Insulinmenge (IE), Gesamtkohlenhydrate (g) und Eintragszahl an.
- **Versionsstand:** `versionName = "26.11.8"`, `versionCode = 261108`.

---

## [26.11.7] - 2026-11-18

### Hinzugefügt & Verbessert
- **Schlankere Navigationsleiste (`XdripYouRoot.kt`):**
  - Kompakte Material-3-You-Navigationsleiste (`56.dp` statt `80.dp` Höhe) für mehr vertikalen Platz auf der Übersicht.
- **OLED Vollbild-Nachttisch-Modus (`BedsideScreen.kt`, `XdripYouRoot.kt`):**
  - Über das Mond-Icon in der Top-Leiste der Übersicht aufrufbar: kontrastreiche schwarze Vollbild-Uhr mit großem Blutzuckerwert, Trend, Delta, IOB/COB und aktivem `keepScreenOn`.
- **24-Stunden-Basalprofil (`BasalProfile.kt`, `SettingsScreen.kt`, `OverviewScreen.kt`):**
  - Portierung von xDrips `BasalProfile` (24 stündliche Basalraten mit Tagesgesamtbasal-Anzeige, interaktivem Balken-Editor und optionalem `Basal`-Chip auf der Übersicht).
- **Nightscout DeviceStatus & Pumpendaten (`NightscoutClient.kt`, `GlucoseRepository.kt`):**
  - Synchronisiert Phone-Akku + IOB/COB zu `/api/v1/devicestatus` und übernimmt optional externe Loop-/Pumpendaten (IOB, COB, Reservoir) im Follower-Betrieb.
- **Fingerstich-Marker im Chart (`GlucoseChart.kt`):**
  - Zeichnet `Fingerstich`-Einträge als Kalibrierungs-/Kontrollringe direkt auf dem jeweiligen BZ-Niveau im Verlauf-Chart.
- **Versionsstand:** `versionName = "26.11.7"`, `versionCode = 261107`.

---

## [26.11.6] - 2026-11-18

### Hinzugefügt & Optimiert
- **Kompakte Übersicht ohne Scrollen & Querformat ohne Navigationsleiste (`OverviewScreen.kt`, `XdripYouRoot.kt`, `GlucoseChart.kt`):**
  - Hochformat zeigt Hero-Glukose, flexiblen Chart und 4er-Statistikleiste gemeinsam auf einem Bildschirm ohne vertikales Scrollen.
  - Querformat blendet Bottom-NavigationBar (und TopAppBar auf der Übersicht) aus und ordnet Hero-Wert + Statistiken links neben dem maximal großen Verlauf-Chart an.
- **Dexcom Share Follower (`DexcomShareClient.kt`, `GlucoseRepository.kt`, `GlucoseForegroundService.kt`):**
  - Portierung von xDrips `ShareFollowDownload`: Direkter Abruf von Dexcom Share (US `share2.dexcom.com` & EU/International `shareous1.dexcom.com`) inkl. 5-Min.-Hintergrundsync und manuellem Abruf unter *Optionen → Datenquellen & Sensor*.
- **IOB-bewusster Bolus-Rechner (`BolusCalculator.kt`, `TreatmentEditorSheet.kt`):**
  - Konfigurierbarer KH-Faktor (g/IE) und Korrekturfaktor (ISF) unter *Optionen → Anzeige, IOB/COB & Material 3 You*; automatischer Vorschlag direkt im `+ Eintrag`-Dialog inkl. IOB-Abzug und `Fingerstich`-Eintragstyp.
- **Versionsstand:** `versionName = "26.11.6"`, `versionCode = 261106`.

---

## [26.11.5] - 2026-11-18

### Hinzugefügt & Portiert
- **IOB & COB Engine (`IobCobCalculator.kt`, `OverviewScreen.kt`, `AlertEngine.kt`):**
  - Portierung von xDrips trapezförmiger Insulin-Wirkungskurve (`LinearTrapezoidInsulin` / `Treatments`) sowie der verzögerten KH-Resorption.
  - Konfigurierbare Insulin-Wirkdauer (DIA, 3–7 Std.), Peak-Profil (Ultra-Schnell / Schnell / Klassisch) und KH-Aufnahmerate (g/Std.) unter *Optionen -> Anzeige, IOB/COB & Material 3 You*.
  - Anzeige von aktivem **IOB (IE)** und **COB (g)** als Material-3-Chips auf der Übersichts-Karte sowie in der laufenden Status-Benachrichtigung.
- **Mehr-Tage-Statistik & AGP-Perzentil-Chart (`AgpStats.kt`, `PercentileChart.kt`, `HistoryScreen.kt`):**
  - Portierung von xDrips `PercentileView` / `StatsActivity`: Zeitraum-Auswahl für **24 Std., 7 Tage, 14 Tage, 30 Tage und 90 Tage**.
  - Zeigt 5-Zonen-TIR-Balken, GMI, Ø, SD, CV %, Sensor-Datenabdeckung (%) sowie das **Ambulatory Glucose Profile (AGP)** mit 10–90% Band, 25–75% IQR-Band und Median-Kurve über 24 Stunden.
- **Therapie-Bearbeitung & Nightscout-Treatments-Abruf (`TreatmentEditorSheet.kt`, `NightscoutClient.kt`, `GlucoseRepository.kt`):**
  - Bestehende Therapie-Einträge im Verlauf lassen sich per Antippen direkt bearbeiten.
  - Nightscout-Follower ruft jetzt neben Glukosewerten auch Therapie-Einträge (`/api/v1/treatments.json`) dedupliziert ab.
- **Lokaler xDrip Web-Service (`LocalWebServer.kt`):**
  - Eingebetteter HTTP-Server auf Port **`17580`** (kompatibel mit xDrip `XdripWebService`) für lokale Consumer (AAPS, Watchfaces, Nightwatch, Tasker).
  - Unterstützt `/sgv.json`, `/status.json`, `/treatments.json` und `/pebble` inkl. optionalem WLAN-Zugriff (`0.0.0.0`) und SHA-1 `api-secret`-Schutz.
- **xDrip Local Broadcast Empfang & Broadcast Snooze (`XdripBroadcastReceiver.kt`):**
  - Optionaler Empfang externer `com.eveningoutpost.dexdrip.BgEstimate`-Broadcasts (z. B. von Juggluco/AAPS) mit Loop-Schutz.
  - Senden & Empfangen von `com.eveningoutpost.dexdrip.Snooze` zur Alarm-Synchronisation zwischen Apps.
- **Versionsstand:** `versionName = "26.11.5"`, `versionCode = 261105`.

---

## [26.11.4] - 2026-11-18

### Hinzugefügt & Integriert
- **Ersteinrichtungs-Assistent (First-Run Setup Wizard, `OnboardingWizard.kt`):**
  - Geführter 4-Schritte-Assistent beim ersten App-Start:
    1. *Willkommen & Einheiten*: Auswahl von `mg/dL` oder `mmol/L` und Material-3-You-Farbschema (System, Hell, Dunkel + Android 12+ Dynamic Color).
    2. *Datenquelle wählen*: CamAPS FX Companion App, erweiterter Companion-App-Modus (Dexcom, Medtronic, Eversense, Aidex, Ottai, Sinocare, Diabeloop), Nightscout-Follower oder Demo/Manuell.
    3. *Berechtigungen einrichten*: Interaktive Status-Karten & Direkt-Buttons für **Notification Listener**, **Benachrichtigungen (`POST_NOTIFICATIONS`)**, **Akku-Optimierung ausnehmen**, **Nicht-stören-Umgehung (`DND`)** sowie **Exakte Alarme (`SCHEDULE_EXACT_ALARM`)** inkl. automatischem Refresh bei App-Rückkehr.
    4. *Sensor, Alarme & Abschluss*: Sensor-Startzeit setzen, optionale Demo-Werte laden und direkt ins Dashboard starten. Erneut aufrufbar über *Einstellungen -> Diagnose, Labor & Info*.
- **Umfassende funktionale xDrip-Einstellungen integriert:**
  - **Datenquellen & Sensor (`CamApsParser`, `GlucoseRepository`):** Erweiterte Companion-App-Erkennung (`extendedCompanionPackages`), Sensor-Laufzeit in Tagen (`sensorExpectedDays`) und Sensor-Ablauf-Vorwarnung (`sensorExpiryAlertEnabled`).
  - **Alarme, Eskalation & Sprachausgabe (`AlertEngine`, `SpeechHelper`):**
    - `TextToSpeech`-Sprachausgabe für neue Blutzuckerwerte (`speakReadingsEnabled`), Trendrichtung (`speakTrendEnabled`), aktive Alarme (`speakAlertsEnabled`), Sprechtempo (`speechSpeed`) und In-App-Sprachtest.
    - Lautstärke-Eskalation (`ascendingToMedium`, `delayAscending3Min`), Stumm-Start-Modus (`startAlertsSnoozed`), Action-Buttons in Alarm-Benachrichtigungen (`showButtonsInAlerts`), Bildschirm wecken (`wakePhoneDuringAlerts`) und Kamera-Blitzlicht bei Alarm (`flashTorchOnAlert`).
  - **Anzeige & Chart (`GlucoseChart`, `OverviewScreen`):** Punktdarstellung ohne Verbindungslinie (`chartPointsOnly`), Rasterlinien (`showChartGrid`), Durchschnittslinie (`showChartAverageLine`), Y-Achsen-Obergrenze (`chartMaxMgdl`) und 24h-Statistikzeile mit TIR, Durchschnitt, GMI & CV% (`showExtraStatusLine`).
  - **Cloud, Broadcast & Aufbewahrung (`XdripBroadcastSender`, `NightscoutClient`, `GlucoseForegroundService`):** Gezieltes Broadcast-Zielpaket (`broadcastTargetPackage`), optionaler Nightscout-Behandlungs-Upload (`nightscoutUploadTreatments`), Upload nur im WLAN (`nightscoutWifiOnly`) sowie automatische Datenbereinigung nach konfigurierbarer Aufbewahrungsdauer (`retentionDays`).
  - **Versionsstand:** `versionName = "26.11.4"`, `versionCode = 261104`.

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
