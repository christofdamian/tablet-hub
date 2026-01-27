# CLAUDE.md

## Project Guidelines

- Always commit major changes
- Do not include yourself (Co-Authored-By) in commit messages
- Always write a test when fixing a bug

## Debugging

Use adb from Android SDK (not in PATH):
```bash
~/Android/Sdk/platform-tools/adb devices
~/Android/Sdk/platform-tools/adb logcat -d -t 100 | grep tablethub
~/Android/Sdk/platform-tools/adb logcat --pid=$(~/Android/Sdk/platform-tools/adb shell pidof net.damian.tablethub)
```

## Project Overview

TabletHub is an Android tablet app designed as an always-on dashboard to replace a Nest Hub. It serves as a bedside/kitchen dashboard with alarm clock, Home Assistant controls via MQTT, and Plex music playback.

## Tech Stack

- Language: Kotlin
- UI: Jetpack Compose with Material 3
- Min SDK: 29 (Android 10), Target SDK: 34
- MQTT: Eclipse Paho
- Media: AndroidX Media3 / ExoPlayer
- Database: Room
- DI: Hilt
- Networking: Retrofit + Moshi

## Core Features

1. **Clock & Alarms** - Large clock display, alarm management, pre-alarm HA triggers
2. **Quick Actions** - Configurable button grid for HA service calls
3. **Music Player** - Plex integration with library browsing and playback
4. **Photo Slideshow** - Future: Google Photos integration

## Home Assistant Integration

The app registers as an HA device via MQTT discovery, exposing:
- `sensor.tablethub_next_alarm`
- `binary_sensor.tablethub_alarm_ringing`
- `switch.tablethub_alarm_*`
- `media_player.tablethub`
- `switch.tablethub_screen`
- `light.tablethub_brightness`
- `sensor.tablethub_battery`

## Project Structure

```
app/src/main/java/com/tablethub/
├── ui/          # Compose screens and components
├── data/        # Room database, DAOs, repositories
├── service/     # MQTT, alarm, media, display services
├── plex/        # Plex API and authentication
├── di/          # Hilt modules
└── util/        # Utilities
```

See `tablet-hub-spec.md` for full specification.
