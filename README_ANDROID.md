# SenPai Scanner — Android v3.0

Cloudflare IP scanner for Android.

## Features (v3.0)

- **Presets** — Quick / Normal / Deep
- **Stop after N healthy** — saves time and battery
- **Skip failed IPs** — remembers bad IPs between scans
- **Foreground scan** — notification with progress
- **Settings** — custom SNI, probe path, IPv6, schedule, vibration
- **Colo filter** — filter results by datacenter
- **Export** — plain, CSV, Clash, sing-box, V2Ray URI
- **Tap row** — copy `ip:port`
- **Compare scans** — stable IPs vs previous run
- **Widget** — last top IPs on home screen
- **Scheduled scan** — WorkManager periodic (Settings)
- **ETA** — remaining time and IP/s during scan
- **CF ranges** — loaded from `assets/cf_ranges.json`

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Architecture

```
MainActivity → MainViewModel → ScanRepository.engine
                    ↓
            ScanForegroundService (optional)
                    ↓
         Prober + IpSource + DataStore settings
```
