# xDrip You (`kotlin-jetpack-you`)

Modernisierte, aufgeräumte Neuimplementierung von xDrip in **100 % Kotlin + Jetpack Compose + Material 3 You**, aufbauend auf diesem lokalen Fork von `NightscoutFoundation/xDrip` (unveränderter Upstream-Stand liegt auf Branch `master`).

## Umfang der Version 1
- **CamAPS FX Companion App Collector** (`CamApsNotificationListenerService`):
  - Übernimmt kontinuierlich laufende Blutzucker-Benachrichtigungen von `com.camdiab.fx_alert.*` (`RemoteViews` + `Notification.extras`).
  - Automatische Einheitserkennung (`mg/dL` & `mmol/L` inkl. Komma-Format), Deduplizierung & Jam-Schutz (angelehnt an xDrip `UiBasedCollector`).
  - Live-Diagnoseprotokoll und Simulator im Tab **Quelle**.
- **Material 3 You Übersicht & Verlauf**:
  - Android 12+ **Dynamic Color** (Wallpaper-Farben) + Hell-/Dunkel-Modus.
  - Interaktiver Compose-Canvas-Glukoseverlauf (`3h / 6h / 12h / 24h`) mit Zielbereichsband und Touch-Scrubbing.
  - TIR, Durchschnitt, GMI und Variabilität (CV %).
- **Alarme & Hintergrunddienst**:
  - `GlucoseForegroundService` mit laufender Status-Benachrichtigung.
  - Hoch-/Niedrig-Schwellwertalarme, Zeitfenster, Lautlos-Umgehung (`USAGE_ALARM`), verpasste Messwerte, schneller Anstieg/Abfall, Smart Alerting / Smart Snoozing sowie Benachrichtigungs-Aktionen (**Schlummern** / **Stopp**).
