# xDrip 2 You (`main` / `kotlin-jetpack-you`)

Modernisierte, aufgeräumte Neuimplementierung von xDrip in **100 % Kotlin + Jetpack Compose + Material 3 You**, aufbauend auf diesem Fork von `NightscoutFoundation/xDrip` (unveränderter Upstream-Stand liegt auf Branch `master`, aktive Entwicklung läuft auf `main` bzw. `kotlin-jetpack-you`).

Aktuelle Version: **`26.11.15-rc1`** (`YY.MM.AppVersion`, Details in [CHANGELOG.md](CHANGELOG.md)).

## Enthaltene Features
- **Ersteinrichtungs-Assistent (First-Run Setup Wizard)**:
  - 4-Schritte-Onboarding beim ersten Start für Einheiten (`mg/dL` / `mmol/L`), Material-3-You-Design, Datenquellenwahl (CamAPS FX, weitere Companion Apps, Nightscout-Follower oder Demo) und geführte Einrichtung aller Android-Berechtigungen (Notification Listener, Benachrichtigungen, Akku-Optimierung, DND-Umgehung, exakte Alarme).
- **CamAPS FX & Companion App Collector** (`CamApsNotificationListenerService`):
  - Übernimmt laufende Blutzucker-Benachrichtigungen von `com.camdiab.fx_alert.*` (`RemoteViews` + `Notification.extras`) sowie optional von weiteren CGM-/Pumpen-Companion-Apps (Dexcom G6/G7/ONE/Stelo, Medtronic Guardian/Simplera/MiniMed, Eversense, Aidex, Ottai, Sinocare, Diabeloop).
  - Automatische Einheitserkennung (`mg/dL` & `mmol/L` inkl. Komma-Format), Deduplizierung (`10s` / `250s`), Jam-Schutz & Live-Diagnoseprotokoll.
- **Material 3 You Übersicht, Nachttisch-Modus & Chart**:
  - Android 12+ **Dynamic Color** (Wallpaper-Farben), Hell-/Dunkel-/System-Modus, optionaler Nachttisch-Modus (`keepScreenOn`) sowie Vollbild-OLED-Nachttischuhr (`BedsideScreen`).
  - Kompakte Übersicht ohne vertikales Scrollen (Querformat ohne Navigationsleiste) mit **Fingerstich-Markern**, **30-Min.-Vorhersage-Kurve**, **Therapie-Markern**, Punktdarstellung, Rasterlinien, Durchschnittslinie und anpassbarer Y-Achsen-Skala.
  - Konfigurierbare 24h-Statistikzeile (TIR, Durchschnitt, GMI und Variabilität CV %) sowie Sensor-Session-Tracking inkl. Ablauf-Vorwarnung.
- **Therapie-Tagebuch, IOB/COB & Mehr-Tage-AGP**:
  - Schnelle Erfassung und Bearbeitung von Mahlzeiten/Bolus (IE), Kohlenhydraten (g), Fingerstich-BZ und Notizen.
  - Berechnung von **Insulin on Board (IOB)** und **Carbs on Board (COB)** nach xDrips Trapez-/Resorptionsmodell mit konfigurierbarer DIA, Peak-Profil und KH-Aufnahmerate.
  - Mehr-Tage-Statistiken (**24 Std. bis 90 Tage**) inkl. 5-Zonen-TIR, SD/CV/GMI, Datenabdeckung und **AGP-Perzentil-Chart** (10–90 %, 25–75 %, Median).
- **Umfassendes Alarm- & Sprach-System**:
  - Hoch-/Niedrig-Schwellwertalarme, Zeitfenster, eigener Android-Klingelton (`RingtoneManager`), Lautlos-Umgehung (`USAGE_ALARM`), **Schlummern / Stopp**-Aktionen, Eskalation (3-Min.-Verzögerung / Medium-Cap), Stumm-Start-Modus, Anruf-Stummschaltung, Stale-Alarm-Sperre, Bildschirm-Wecken und Kamera-Blitzlicht.
  - Verpasste Messwerte, schneller Anstieg/Abfall, **Niedrig-Vorhersage-Alarm**, **Anhaltend-Hoch-Alarm**, Smart Alerting & Smart Snoozing.
  - **Text-to-Speech (`SpeechHelper`)** für neue Blutzuckerwerte, Trends und Alarme inkl. anpassbarem Sprechtempo.
- **Aufgeräumter Options-Hub, Ökosystem & Backup**:
  - Suchbarer **5-Kategorien-Options-Hub** (*Datenquellen & Sensor*, *Alarme & Klingeltöne*, *Anzeige & Material 3 You*, *Cloud, Broadcast & Export*, *Diagnose, Labor & Info*).
  - **Nightscout, Dexcom Share, LibreLinkUp & Peer-Sync**: Nightscout REST v1 (Messwerte, Treatments & DeviceStatus Upload/Pull), Dexcom Share (US & EU), LibreLinkUp (EU/DE/US/Global) und direkter xDrip Peer-Sync (Port 17580).
  - **Health Connect, InfluxDB & Tidepool**: Bidirektionaler Android Health Connect Sync (Blutzucker & KH), InfluxDB v1/v2 Line-Protocol-Uploader sowie Tidepool-Uploader.
  - **Lokaler xDrip-Broadcast, Broadcast-Snooze & Tasker-Bridge**: Sendet/empfängt `BgEstimate`, `Snooze` sowie Tasker-/Locale-Automationsbefehle.
  - **Lokaler xDrip Web-Service (Port 17580)**: Stellt `/sgv.json`, `/status.json`, `/treatments.json`, `/pebble` und `/tasker/<befehl>` lokal bereit.
  - **Material You Homescreen-Widget** (`xDrip 2 You · Blutzucker`), **Schwebendes Overlay**, **Wear ExternalStatus-Broadcast**, **JSON-Backup (Export/Import)**, **CSV-Export**, **AAPS-Profil-Import** und **Erinnerungen**.
- **Direkt-Hardware, Kalibrierung & weitere Follower**:
  - **BLE & NFC**: Direkter BLE-Scanner & GATT-Kollektor für Bluetooth-Blutzuckermessgeräte (`0x1808`), Libre-Bridges (Bubble/MiaoMiao) und Dexcom G6/ONE sowie **Freestyle Libre NFC FRAM-Scan (`NfcV`)**.
  - **Kalibrierungs-Engine & Rauschfilter**: Gewichtete lineare Regression (`CalibrationEngine`, Slope/Intercept/MARD %), 5-Punkt-Glättung, **CareLink Connect** und generischer **WebFollow JSON-Client**.
