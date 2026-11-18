# xDrip 2 You (`kotlin-jetpack-you`)

Modernisierte, aufgeräumte Neuimplementierung von xDrip in **100 % Kotlin + Jetpack Compose + Material 3 You**, aufbauend auf diesem lokalen Fork von `NightscoutFoundation/xDrip` (unveränderter Upstream-Stand liegt auf Branch `master`).

## Enthaltene Features
- **CamAPS FX Companion App Collector** (`CamApsNotificationListenerService`):
  - Übernimmt laufende Blutzucker-Benachrichtigungen von `com.camdiab.fx_alert.*` (`RemoteViews` + `Notification.extras`).
  - Automatische Einheitserkennung (`mg/dL` & `mmol/L` inkl. Komma-Format), Deduplizierung (`10s` / `250s`), Jam-Schutz & Live-Diagnoseprotokoll.
- **Material 3 You Übersicht, Vorhersage & Verlauf**:
  - Android 12+ **Dynamic Color** (Wallpaper-Farben) + Hell-/Dunkel-/System-Modus.
  - Interaktiver Compose-Canvas-Glukoseverlauf (`3h / 6h / 12h / 24h`) mit Zielbereichsband, Touch-Scrubbing, **30-Min.-Vorhersage-Kurve** und **Therapie-Markern** (Insulin IE & KH g).
  - TIR, Durchschnitt, GMI und Variabilität (CV %).
- **Therapie-Tagebuch (Treatments)**:
  - Schnelle Erfassung von Mahlzeiten/Bolus (IE), Kohlenhydraten (g), Fingerstich-BZ und Notizen direkt über `+ Eintrag`.
- **Umfassendes Alarm-System**:
  - Hoch-/Niedrig-Schwellwertalarme, Zeitfenster, Lautlos-Umgehung (`USAGE_ALARM`), Benachrichtigungs-Aktionen (**Schlummern** / **Stopp**), globale/gezielte Stummschaltung.
  - Verpasste Messwerte, schneller Anstieg/Abfall, **Niedrig-Vorhersage-Alarm**, **Anhaltend-Hoch-Alarm**, Smart Alerting & Smart Snoozing.
- **Ökosystem, Widget & Backup**:
  - **Nightscout Integration**: Follower-Abruf (REST v1) und Upload von Messwerten & Behandlungen mit SHA-1 `api-secret`.
  - **Lokaler xDrip-Broadcast** (`com.eveningoutpost.dexdrip.BgEstimate`) für AAPS, Watchfaces & Tasker.
  - **Material You Homescreen-Widget** (`xDrip 2 You · Blutzucker`).
  - **JSON Export & Import** für Messwerte, Behandlungen und Alarme.
