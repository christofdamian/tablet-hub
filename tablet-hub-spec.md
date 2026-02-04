# TabletHub - Android Tablet Home Assistant Dashboard App

## Project Overview

An Android tablet app to replace a Nest Hub, functioning as an always-on bedside/kitchen dashboard with alarm clock, Home Assistant controls, and music playback. The app registers itself as a Home Assistant device via MQTT discovery.

## Target Environment

- Android tablet (Android 10+), always plugged in
- Home Assistant instance with MQTT broker (Mosquitto)
- Plex Media Server on local NAS
- Dedicated dashboard use (kiosk-style)

## Core Screens

The app uses horizontal swipe navigation (ViewPager2/Compose Pager) between screens. A subtle page indicator shows current position.

### Screen 1: Clock & Alarms

**Main View:**
- Large digital clock (primary focus, 40%+ of screen)
- Tap clock to toggle 12h/24h format
- Current date below clock
- Next alarm time displayed (e.g., "⏰ 07:00 Mon")
- Weather widget area (future: HA sensor integration)

**Alarm Management (bottom sheet or tap to expand):**
- List of configured alarms
- Each alarm shows: time, days active (MTWTFSS dots), enabled toggle
- Tap alarm to edit, swipe to delete
- FAB to add new alarm

**Alarm Editor:**
- Time picker (scroll wheels or clock face)
- Day selector (toggle buttons for each day)
- Label/name field
- Pre-alarm offset (minutes before alarm to trigger HA actions)
- Alarm sound source: 
  - Default sounds
  - Plex playlist (picker)
  - Specific Plex album/artist
- Snooze duration setting
- HA actions to trigger (select from configured actions)

**Alarm Firing UX:**
- Full-screen takeover with time display
- Album art if music playing
- Large snooze button
- Dismiss button (require swipe or hold to prevent accidental dismiss)
- Gradual volume increase option

### Screen 2: Quick Actions (Buttons)

**Layout:**
- Grid of customisable buttons (default 3x4, configurable)
- Each button: icon + label
- Visual state indication for entities (e.g., light buttons glow when on)

**Button Configuration (long-press to configure):**
- Icon picker (Material Design Icons subset)
- Label text
- Action type:
  - HA service call (domain, service, entity_id, optional data)
  - Toggle entity
  - URL/deep link
  - Internal action (next screen, settings, etc.)
- Optional: HA entity to track for state display

**Preset Button Types:**
- Light toggle (with brightness slider on long-press?)
- Scene activation
- Script trigger
- Media play/pause
- Custom service call

### Screen 3: Music Player

**Now Playing View:**
- Large album art (dominant element)
- Track title, artist, album
- Progress bar (seekable)
- Playback controls: previous, play/pause, next
- Shuffle and repeat toggles
- Volume slider

**Library Browser (swipe up or tab):**
- Plex library navigation
- Sections: Playlists, Artists, Albums, Recently Added
- Search functionality
- Tap to play, long-press for options (play next, add to queue)

**Playback:**
- Audio plays directly on tablet (ExoPlayer/Media3)
- Plex API for library/metadata
- Exposes media_player entity to HA for external control
- Support for Plex playlists as alarm sounds

### Screen 4: Photo Slideshow (Future)

**Planned Features:**
- Google Photos album integration
- Configurable rotation interval
- Ken Burns effect (subtle pan/zoom)
- Clock overlay option
- Pause on touch
- Activate automatically when idle?

## Home Assistant Integration

### Connection Method: MQTT Discovery

The app connects to the HA MQTT broker and publishes discovery messages. This auto-creates entities in HA without manual configuration.

**MQTT Topics Structure:**
```
homeassistant/sensor/tablethub_{device_id}/next_alarm/config
homeassistant/binary_sensor/tablethub_{device_id}/alarm_active/config
homeassistant/switch/tablethub_{device_id}/alarm_{n}/config
homeassistant/media_player/tablethub_{device_id}/config
homeassistant/switch/tablethub_{device_id}/screen/config
homeassistant/light/tablethub_{device_id}/brightness/config
homeassistant/sensor/tablethub_{device_id}/battery/config
homeassistant/button/tablethub_{device_id}/button_{n}/config

tablethub/{device_id}/state          # JSON state updates
tablethub/{device_id}/command        # Incoming commands
tablethub/{device_id}/media/state    # Media player state
tablethub/{device_id}/media/command  # Media player commands
```

### Exposed Entities

| Entity ID | Type | Description | Attributes |
|-----------|------|-------------|------------|
| `sensor.tablethub_next_alarm` | sensor | ISO timestamp of next alarm | alarm_label, alarm_id |
| `binary_sensor.tablethub_alarm_ringing` | binary_sensor | on when alarm is actively firing | alarm_id |
| `switch.tablethub_alarm_1` (etc.) | switch | Enable/disable individual alarms | time, days, label |
| `media_player.tablethub` | media_player | Full media player | standard media_player attrs |
| `switch.tablethub_screen` | switch | Screen on/off control | |
| `light.tablethub_brightness` | light | Screen brightness (0-255) | |
| `sensor.tablethub_battery` | sensor | Battery percentage | charging (bool) |
| `event.tablethub_button_press` | event | Fired when button pressed | button_id, button_label |

### Incoming Commands (from HA)

The app subscribes to command topics and responds to:

- `set_screen`: on/off
- `set_brightness`: 0-255
- `trigger_alarm`: immediately fire alarm
- `dismiss_alarm`: dismiss current alarm
- `media_play`, `media_pause`, `media_stop`, `media_next`, `media_previous`
- `media_seek`: position in seconds
- `media_volume`: 0.0-1.0
- `play_media`: media_content_id, media_content_type

### Pre-Alarm Automation Hook

When alarm is N minutes away (configurable per-alarm), publish event:
```json
{
  "event_type": "tablethub_pre_alarm",
  "alarm_id": "alarm_1",
  "alarm_time": "07:00:00",
  "minutes_until": 15
}
```

HA automation example:
```yaml
automation:
  - alias: "Bedroom wake-up routine"
    trigger:
      - platform: mqtt
        topic: "tablethub/bedroom/event"
    condition:
      - condition: template
        value_template: "{{ trigger.payload_json.event_type == 'tablethub_pre_alarm' }}"
    action:
      - service: light.turn_on
        target:
          entity_id: light.bedroom
        data:
          brightness_pct: 10
          transition: 300
```

### Alarm Snooze Event

When alarm is snoozed, publish event:
```json
{
  "event_type": "tablethub_alarm_snoozed",
  "alarm_id": "1",
  "snooze_minutes": 9
}
```

### Alarm Dismiss Event

When alarm is dismissed, publish event:
```json
{
  "event_type": "tablethub_alarm_dismissed",
  "alarm_id": "1"
}
```

## Display & Dimming

### Auto-Dimming

- Time-based dimming schedule (e.g., 22:00-07:00 = night mode)
- Night mode: very dim screen (brightness 5-10%), red-shifted clock only
- Optional: light sensor-based auto-brightness (if tablet has sensor)
- Motion/tap to temporarily boost brightness

### Screen Management

- Keep screen on always (KEEP_SCREEN_ON flag)
- Dim to minimum (not off) during sleep hours
- Optional screen-off via HA command (for when away)
- Wake on alarm
- Optional: wake on MQTT command

### Night Mode Display

- Clock only (large, dim, red/orange colour for less sleep disruption)
- No other UI elements
- Tap to temporarily show full UI
- Swipe still works to access other screens

## Plex Integration

### Authentication

- Plex account sign-in (OAuth flow) or
- Direct Plex server connection with token
- Store credentials securely (EncryptedSharedPreferences)

### API Usage

- PlexAPI for library browsing, metadata, artwork
- Direct media URLs for playback
- Transcode if needed (configurable quality)

### Playback

- ExoPlayer/Media3 for audio playback
- Background playback service (foreground notification)
- MediaSession for system integration (lock screen controls, Bluetooth metadata)
- Scrobble to Plex (mark as played)

## Settings Screen

Accessible via gesture (swipe down from top?) or hidden button.

### Categories:

**Connection:**
- Home Assistant URL (for fallback/future WebSocket)
- MQTT broker host, port, user, password
- Plex server URL and authentication
- Device ID/name for HA registration

**Display:**
- Clock format (12h/24h)
- Night mode schedule (start/end times)
- Night mode brightness level
- Daytime brightness (auto/manual)
- Keep screen on toggle
- Screen timeout (if not always-on)

**Buttons:**
- Grid size (3x3, 3x4, 4x4)
- Button configuration UI
- Import/export button config (JSON)

**Alarms:**
- Default snooze duration
- Default pre-alarm offset
- Alarm volume settings
- Gradual volume increase duration

**Advanced:**
- MQTT topic prefix
- Enable/disable specific entities
- Debug logging toggle
- Export logs

## Technical Architecture

### Tech Stack

```
Language: Kotlin
UI: Jetpack Compose
Min SDK: 29 (Android 10)
Target SDK: 34 (Android 14)

Dependencies:
├── androidx.compose.* (UI)
├── androidx.compose.material3 (Material 3 theming)
├── com.google.accompanist:accompanist-pager (swipe navigation)
├── androidx.media3:media3-exoplayer (audio playback)
├── androidx.media3:media3-session (MediaSession)
├── org.eclipse.paho:org.eclipse.paho.client.mqttv3 (MQTT)
├── info.mqtt:paho-mqtt-android (Android MQTT service)
├── com.squareup.retrofit2:retrofit (Plex API)
├── com.squareup.moshi:moshi (JSON parsing)
├── io.coil-kt:coil-compose (image loading)
├── androidx.room:room-* (local database)
├── androidx.datastore:datastore-preferences (settings)
├── androidx.hilt:hilt-* (dependency injection)
├── androidx.work:work-runtime-ktx (background work)
└── com.google.android.material:material (icons, components)
```

### Project Structure

```
app/
├── src/main/
│   ├── java/com/tablethub/
│   │   ├── TabletHubApplication.kt
│   │   ├── MainActivity.kt
│   │   │
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   │   ├── Theme.kt
│   │   │   │   ├── Color.kt
│   │   │   │   └── Typography.kt
│   │   │   ├── navigation/
│   │   │   │   └── AppNavigation.kt (Pager setup)
│   │   │   ├── screens/
│   │   │   │   ├── clock/
│   │   │   │   │   ├── ClockScreen.kt
│   │   │   │   │   ├── ClockViewModel.kt
│   │   │   │   │   ├── AlarmListSheet.kt
│   │   │   │   │   └── AlarmEditorDialog.kt
│   │   │   │   ├── buttons/
│   │   │   │   │   ├── ButtonsScreen.kt
│   │   │   │   │   ├── ButtonsViewModel.kt
│   │   │   │   │   └── ButtonConfigDialog.kt
│   │   │   │   ├── player/
│   │   │   │   │   ├── PlayerScreen.kt
│   │   │   │   │   ├── PlayerViewModel.kt
│   │   │   │   │   ├── NowPlayingView.kt
│   │   │   │   │   └── LibraryBrowser.kt
│   │   │   │   └── settings/
│   │   │   │       ├── SettingsScreen.kt
│   │   │   │       └── SettingsViewModel.kt
│   │   │   └── components/
│   │   │       ├── ClockDisplay.kt
│   │   │       ├── AlarmItem.kt
│   │   │       ├── ActionButton.kt
│   │   │       ├── MediaControls.kt
│   │   │       └── PageIndicator.kt
│   │   │
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── dao/
│   │   │   │   │   ├── AlarmDao.kt
│   │   │   │   │   └── ButtonDao.kt
│   │   │   │   └── entity/
│   │   │   │       ├── AlarmEntity.kt
│   │   │   │       └── ButtonEntity.kt
│   │   │   ├── preferences/
│   │   │   │   └── SettingsDataStore.kt
│   │   │   └── repository/
│   │   │       ├── AlarmRepository.kt
│   │   │       ├── ButtonRepository.kt
│   │   │       └── SettingsRepository.kt
│   │   │
│   │   ├── service/
│   │   │   ├── mqtt/
│   │   │   │   ├── MqttService.kt
│   │   │   │   ├── MqttMessageHandler.kt
│   │   │   │   ├── HaDiscovery.kt (entity registration)
│   │   │   │   └── HaStatePublisher.kt
│   │   │   ├── alarm/
│   │   │   │   ├── AlarmScheduler.kt
│   │   │   │   ├── AlarmReceiver.kt (BroadcastReceiver)
│   │   │   │   └── AlarmService.kt (foreground service)
│   │   │   ├── media/
│   │   │   │   ├── PlaybackService.kt (Media3)
│   │   │   │   └── MediaSessionCallback.kt
│   │   │   └── display/
│   │   │       └── ScreenManager.kt (brightness, wake)
│   │   │
│   │   ├── plex/
│   │   │   ├── PlexApi.kt (Retrofit interface)
│   │   │   ├── PlexRepository.kt
│   │   │   ├── PlexAuthManager.kt
│   │   │   └── model/
│   │   │       ├── PlexLibrary.kt
│   │   │       ├── PlexPlaylist.kt
│   │   │       ├── PlexTrack.kt
│   │   │       └── PlexAlbum.kt
│   │   │
│   │   ├── di/
│   │   │   ├── AppModule.kt
│   │   │   ├── DatabaseModule.kt
│   │   │   ├── NetworkModule.kt
│   │   │   └── ServiceModule.kt
│   │   │
│   │   └── util/
│   │       ├── TimeUtils.kt
│   │       ├── AlarmUtils.kt
│   │       └── Extensions.kt
│   │
│   ├── res/
│   │   ├── drawable/ (icons, placeholders)
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   ├── colors.xml
│   │   │   └── themes.xml
│   │   └── xml/
│   │       └── backup_rules.xml
│   │
│   └── AndroidManifest.xml
│
├── build.gradle.kts (app)
└── proguard-rules.pro
```

### Key Implementation Notes

#### Alarm Scheduling (Critical)

```kotlin
// Use AlarmManager with SCHEDULE_EXACT_ALARM permission
// Request permission on Android 12+:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (!alarmManager.canScheduleExactAlarms()) {
        // Direct user to settings
        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
}

// Schedule with setAlarmClock for highest priority (shows in status bar)
alarmManager.setAlarmClock(
    AlarmManager.AlarmClockInfo(triggerTimeMillis, pendingIntent),
    pendingIntent
)

// Also schedule pre-alarm trigger separately
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP,
    preAlarmTimeMillis,
    preAlarmPendingIntent
)
```

#### MQTT Discovery Message Example

```kotlin
// Publish to: homeassistant/sensor/tablethub_bedroom/next_alarm/config
val discoveryPayload = """
{
    "name": "Next Alarm",
    "unique_id": "tablethub_bedroom_next_alarm",
    "state_topic": "tablethub/bedroom/state",
    "value_template": "{{ value_json.next_alarm }}",
    "device": {
        "identifiers": ["tablethub_bedroom"],
        "name": "Bedroom Tablet",
        "model": "TabletHub",
        "manufacturer": "DIY"
    },
    "icon": "mdi:alarm"
}
"""
```

#### Foreground Service for Playback

```kotlin
// PlaybackService.kt
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
        
        // Start foreground with notification
        val notification = buildMediaNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
}
```

#### Screen Brightness Control

```kotlin
// ScreenManager.kt
fun setBrightness(level: Int) { // 0-255
    val layoutParams = window.attributes
    layoutParams.screenBrightness = level / 255f
    window.attributes = layoutParams
}

fun keepScreenOn(enabled: Boolean) {
    if (enabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```

### Permissions Required

```xml
<manifest>
    <!-- Alarms -->
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    
    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <!-- Media -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <!-- System -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
</manifest>
```

## Development Phases

### Phase 1: Foundation (MVP)
1. Project setup with all dependencies
2. Basic pager navigation between 3 placeholder screens
3. Clock display with time/date
4. Simple alarm creation and storage (Room)
5. Basic alarm firing (sound plays, full-screen dismiss UI)

### Phase 2: Home Assistant Integration
1. MQTT connection service
2. Discovery message publishing
3. State publishing (next alarm, screen, battery)
4. Command receiving (screen control, alarm trigger/dismiss)
5. Button screen with HA service calls

### Phase 3: Music Player
1. Plex authentication
2. Library browsing UI
3. Playback with ExoPlayer
4. Media player entity for HA
5. Plex playlist as alarm source

### Phase 4: Polish
1. Night mode / auto-dimming
2. Alarm pre-trigger events
3. Settings UI
4. Button state reflection from HA
5. Error handling and reconnection logic

### Phase 5: Extras
1. Google Photos slideshow screen
2. Weather widget (HA sensor)
3. Motion detection wake (if camera available)
4. Tasker/Intent integration

## Testing Notes

- Test alarm reliability after device restart
- Test alarm firing from Doze mode
- Test MQTT reconnection after network loss
- Test media playback continues when screen dims
- Test with actual HA instance and automations

## Future Considerations

- Multiple Plex server support
- Chromecast/DLNA output option
- Voice control integration (local STT?)
- Calendar widget
- Intercom functionality (WebRTC to HA?)
- Widget for home screen (if not always full-screen)
