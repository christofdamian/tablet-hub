# TabletHub

An Android tablet app designed as an always-on dashboard to replace a Nest Hub. It serves as a bedside/kitchen dashboard with alarm clock, Home Assistant controls via MQTT, and Plex music playback.

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
4. **Night Mode** - Ambient light sensor-based auto-dimming with red-shifted display
5. **Photo Slideshow** - Future: Google Photos integration

## Home Assistant Integration

The app registers as an HA device via MQTT discovery, exposing:
- `sensor.tablethub_next_alarm`
- `binary_sensor.tablethub_alarm_ringing`
- `switch.tablethub_alarm_*`
- `media_player.mqtt_media_player_tablethub_music`
- `switch.tablethub_screen`
- `switch.tablethub_night_mode`
- `light.tablethub_brightness`
- `sensor.tablethub_battery`

**MQTT Events:**
- `tablethub/{device_id}/event` - Pre-alarm events for HA automations (sunrise lights, etc.)

### Media Player Setup

The media player entity requires the [bkbilly/mqtt_media_player](https://github.com/bkbilly/mqtt_media_player) custom integration:

1. Install via HACS or manually
2. Restart Home Assistant
3. Go to Settings → Devices & Services → Add Integration → MQTT Media Player
4. The TabletHub media player should appear automatically

### Controlling the Media Player

**Basic controls:**
```yaml
# Play/pause
action: media_player.media_play_pause
target:
  entity_id: media_player.mqtt_media_player_tablethub_music

# Next/previous track
action: media_player.media_next_track
action: media_player.media_previous_track
```

**Play a playlist by name:**
```yaml
action: media_player.play_media
data:
  media_content_id: "My Playlist Name"
  media_content_type: playlist
target:
  entity_id: media_player.mqtt_media_player_tablethub_music
```

### Example Automation

Wake up with music when alarm triggers:
```yaml
automation:
  - alias: "TabletHub - Play music on alarm"
    trigger:
      - platform: state
        entity_id: binary_sensor.tablethub_alarm_ringing
        to: "on"
    action:
      - action: media_player.play_media
        data:
          media_content_id: "Morning Playlist"
          media_content_type: playlist
        target:
          entity_id: media_player.mqtt_media_player_tablethub_music
```

## Project Structure

```
app/src/main/java/net/damian/tablethub/
├── ui/          # Compose screens and components
├── data/        # Room database, DAOs, repositories
├── service/     # MQTT, alarm, media, display services
├── plex/        # Plex API and authentication
├── di/          # Hilt modules
└── util/        # Utilities
```

## Documentation

- `tablet-hub-spec.md` - Full specification
- `tablet-hub-post-mvp.md` - Future enhancements
