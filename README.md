# xDrip 2 You (`main` / `kotlin-jetpack-you`)

Modernisierte, aufgeräumte Neuimplementierung von xDrip in **100 % Kotlin + Jetpack Compose + Material 3 You**, aufbauend auf diesem Fork von `NightscoutFoundation/xDrip` (unveränderter Upstream-Stand liegt auf Branch `master`, aktive Entwicklung läuft auf `main` bzw. `kotlin-jetpack-you`).

Aktuelle Version: **`26.11.4`** (`YY.MM.AppVersion`, Details in [CHANGELOG.md](CHANGELOG.md)).

## Enthaltene Features
- **Ersteinrichtungs-Assistent (First-Run Setup Wizard)**:
  - 4-Schritte-Onboarding beim ersten Start für Einheiten (`mg/dL` / `mmol/L`), Material-3-You-Design, Datenquellenwahl (CamAPS FX, weitere Companion Apps, Nightscout-Follower oder Demo) und geführte Einrichtung aller Android-Berechtigungen (Notification Listener, Benachrichtigungen, Akku-Optimierung, DND-Umgehung, exakte Alarme).
- **CamAPS FX & Companion App Collector** (`CamApsNotificationListenerService`):
  - Übernimmt laufende Blutzucker-Benachrichtigungen von `com.camdiab.fx_alert.*` (`RemoteViews` + `Notification.extras`) sowie optional von weiteren CGM-/Pumpen-Companion-Apps (Dexcom G6/G7/ONE/Stelo, Medtronic Guardian/Simplera/MiniMed, Eversense, Aidex, Ottai, Sinocare, Diabeloop).
  - Automatische Einheitserkennung (`mg/dL` & `mmol/L` inkl. Komma-Format), Deduplizierung (`10s` / `250s`), Jam-Schutz & Live-Diagnoseprotokoll.
- **Material 3 You Übersicht, Vorhersage & Chart**:
  - Android 12+ **Dynamic Color** (Wallpaper-Farben), Hell-/Dunkel-/System-Modus und optionaler Nachttisch-Modus (`keepScreenOn`).
  - Interaktiver Compose-Canvas-Glukoseverlauf (`3h / 6h / 12h / 24h`) mit Zielbereichsband, Touch-Scrubbing, **30-Min.-Vorhersage-Kurve**, **Therapie-Markern**, Punktdarstellung, Rasterlinien, Durchschnittslinie und anpassbarer Y-Achsen-Skala.
  - Konfigurierbare 24h-Statistikzeile (TIR, Durchschnitt, GMI und Variabilität CV %) sowie Sensor-Session-Tracking inkl. Ablauf-Vorwarnung.
- **Therapie-Tagebuch (Treatments)**:
  - Schnelle Erfassung von Mahlzeiten/Bolus (IE), Kohlenhydraten (g), Fingerstich-BZ und Notizen direkt über `+ Eintrag`.
- **Umfassendes Alarm- & Sprach-System**:
  - Hoch-/Niedrig-Schwellwertalarme, Zeitfenster, eigener Android-Klingelton (`RingtoneManager`), Lautlos-Umgehung (`USAGE_ALARM`), **Schlummern / Stopp**-Aktionen, Eskalation (3-Min.-Verzögerung / Medium-Cap), Stumm-Start-Modus, Anruf-Stummschaltung, Stale-Alarm-Sperre, Bildschirm-Wecken und Kamera-Blitzlicht.
  - Verpasste Messwerte, schneller Anstieg/Abfall, **Niedrig-Vorhersage-Alarm**, **Anhaltend-Hoch-Alarm**, Smart Alerting & Smart Snoozing.
  - **Text-to-Speech (`SpeechHelper`)** für neue Blutzuckerwerte, Trends und Alarme inkl. anpassbarem Sprechtempo.
- **Aufgeräumter Options-Hub, Ökosystem & Backup**:
  - Suchbarer **5-Kategorien-Options-Hub** (*Datenquellen & Sensor*, *Alarme & Klingeltöne*, *Anzeige & Material 3 You*, *Cloud, Broadcast & Export*, *Diagnose, Labor & Info*).
  - **Nightscout Integration**: Follower-Abruf (REST v1) und Upload (Messwerte + optionale Treatments, optional nur im WLAN) mit SHA-1 `api-secret`.
  - **Lokaler xDrip-Broadcast** (`com.eveningoutpost.dexdrip.BgEstimate`) mit optionalem Ziel-Paketnamen für AAPS, Watchfaces & Tasker.
  - **Material You Homescreen-Widget** (`xDrip 2 You · Blutzucker`), **JSON-Backup (Export/Import)**, **CSV-Export** und automatische Datenbereinigung nach Aufbewahrungsdauer.
