# TabletHub - Post-MVP Features

Features to add after the core app (clock/alarms, buttons, music player, HA integration) is working.

---

## 1. Google Photos Background

Replace the plain clock screen background with a Google Photos slideshow.

**Functionality:**
- Photo fills screen, clock overlays on top with drop shadow for readability
- Ken Burns effect (subtle pan/zoom animation)
- Configurable rotation interval (e.g., 30 seconds, 1 minute)
- Album picker in settings (select one or more albums)
- Pause rotation on touch, resume after timeout
- Respect night mode (dim or disable during sleep hours)

**Technical:**
- Google Photos API with OAuth authentication
- Cache photos locally for offline/fast display
- Preload next image for smooth transitions

**Clock Screen Layout:**
```
┌─────────────────────────────────┐
│  ☀️ 18°               07:00    │
│                                 │
│      [Google Photo fills       │
│         entire background]      │
│                                 │
│               ⏰ 07:30 Mon      │
└─────────────────────────────────┘
```

---

## 2. Weather Widget

Display current weather on the clock screen, pulled from Home Assistant.

**Functionality:**
- Show current temperature and condition icon
- Optional: humidity, high/low, short forecast
- Tap to expand with more detail (or link to weather screen)
- Updates via MQTT from HA sensors

**HA Entities to Subscribe:**
```
sensor.weather_temperature
sensor.weather_condition
sensor.weather_humidity (optional)
weather.home (for forecast)
```

**Display:**
- Top corner of clock screen
- Icon + temperature as primary
- Condition text or forecast as secondary (optional)

---

## 3. TTS / Voice Announcements

Allow Home Assistant to speak through the tablet using text-to-speech.

**Functionality:**
- Tablet accepts TTS audio as a media_player target
- HA generates audio via its TTS integration, tablet plays it
- Volume respects current media_player volume or separate announcement volume
- Optional: duck current music during announcement, resume after

**Usage in HA:**
```yaml
- service: tts.speak
  target:
    entity_id: media_player.tablethub_bedroom
  data:
    message: >
      Good morning. It's {{ states('sensor.weather_temperature') }}
      degrees and {{ states('sensor.weather_condition') }}.
```

**Use Cases:**
- Morning weather announcement as part of alarm routine
- Doorbell/security announcements
- Calendar reminders
- Custom automations

**Technical:**
- Already supported via media_player entity – TTS services send audio URL
- May need to handle `play_media` with `media_content_type: music` vs `announcement`
- Consider brief audio focus handling (pause music → play TTS → resume)

---

## 4. HA-Driven Alarm Timing

Let Home Assistant control pre-alarm routines based on `sensor.tablethub_next_alarm` instead of configuring offsets on the tablet.

**Current (MVP):**
- Tablet has per-alarm pre-offset setting
- Tablet fires `tablethub_pre_alarm` event X minutes before

**Post-MVP Option:**
- Tablet just exposes `sensor.tablethub_next_alarm` (ISO timestamp)
- HA calculates timing and triggers automations independently
- Simplifies tablet app, gives full flexibility to HA

**HA Template Sensor Example:**
```yaml
template:
  - sensor:
      - name: "Bedroom Alarm Minutes Remaining"
        state: >
          {% set alarm = states('sensor.tablethub_next_alarm') | as_datetime %}
          {% if alarm %}
            {{ ((alarm - now()).total_seconds() / 60) | round(0) }}
          {% else %}
            unknown
          {% endif %}
        unit_of_measurement: "min"
```

**HA Trigger Example:**
```yaml
trigger:
  - platform: template
    value_template: >
      {{ states('sensor.bedroom_alarm_minutes_remaining') | int <= 15 }}
```

**Benefit:**
- Change pre-alarm timing (15 min, 30 min, etc.) entirely in HA
- Different routines for different days/conditions
- No tablet app changes needed

---

## 5. Example Full Wake-Up Automation

Combining all post-MVP features:

```yaml
automation:
  # 15 minutes before alarm: sunrise + bird songs
  - alias: "Bedroom pre-alarm routine"
    trigger:
      - platform: template
        value_template: >
          {{ states('sensor.bedroom_alarm_minutes_remaining') | int == 15 }}
    condition:
      - condition: state
        entity_id: input_boolean.alarm_enabled
        state: "on"
    action:
      # Start sunrise fade over 15 minutes
      - service: light.turn_on
        target:
          entity_id: light.bedroom
        data:
          brightness_pct: 1
          color_temp_kelvin: 2200
      - service: light.turn_on
        target:
          entity_id: light.bedroom
        data:
          brightness_pct: 50
          color_temp_kelvin: 3500
          transition: 900

      # Bird songs, quiet with gradual fade in
      - service: media_player.volume_set
        target:
          entity_id: media_player.tablethub_bedroom
        data:
          volume_level: 0.1
      - service: media_player.play_media
        target:
          entity_id: media_player.tablethub_bedroom
        data:
          media_content_id: "plex://playlist/bird-songs"
          media_content_type: playlist
      - service: media_player.volume_set
        target:
          entity_id: media_player.tablethub_bedroom
        data:
          volume_level: 0.3
          transition: 600

  # Alarm fires: full wake-up
  - alias: "Bedroom alarm fires"
    trigger:
      - platform: state
        entity_id: binary_sensor.tablethub_alarm_ringing
        to: "on"
    action:
      # Lights full brightness, cool white
      - service: light.turn_on
        target:
          entity_id: light.bedroom
        data:
          brightness_pct: 100
          color_temp_kelvin: 5000

      # Switch to upbeat playlist
      - service: media_player.play_media
        target:
          entity_id: media_player.tablethub_bedroom
        data:
          media_content_id: "plex://playlist/morning-energy"
          media_content_type: playlist
      - service: media_player.volume_set
        target:
          entity_id: media_player.tablethub_bedroom
        data:
          volume_level: 0.6

      # Weather announcement after 10 seconds
      - delay: "00:00:10"
      - service: tts.speak
        target:
          entity_id: media_player.tablethub_bedroom
        data:
          message: >
            Good morning! It's currently
            {{ states('sensor.weather_temperature') }} degrees
            and {{ states('sensor.weather_condition') }}.
```

---

## 6. Night Mode Enhancements

Improvements to the existing night mode feature.

**Settings UI:**
- Add settings screen to configure:
  - Lux threshold for auto-switching (default: 15)
  - Hysteresis value (default: 5)
  - Night brightness level (default: 5/255)
  - Enable/disable auto mode vs manual only

**Brightness Restoration:**
- Remember brightness level before entering night mode
- Restore previous brightness when exiting night mode
- Currently exits at night brightness (5) requiring manual adjustment

**Gradual Transitions:**
- Fade between normal and night mode instead of instant switch
- Smooth brightness transitions over 1-2 seconds
- Optional color temperature shift animation

**Sensor Calibration:**
- Different devices have different light sensor sensitivities
- Add calibration option or per-device presets
- "Calibrate in dark room" wizard

**Time-Based Schedule:**
- In addition to sensor-based switching
- Configure night mode hours (e.g., 22:00-07:00)
- Combine with sensor: schedule AND dark = night mode

## 7. Improve the media browsing for the Music Player
- load the data async and on-demand when scrolling, just load the first two pages first
- add a scroll bar (if possible showing the current place in the alphabet, like in Google Photos)

### Search Feature

Add search functionality to find artists, albums, and tracks.

**Implementation:**

1. **Repository** (~10 lines)
   - Add `search(sectionId, query, type?)` method to `PlexRepository.kt`
   - Calls existing `PlexServerApi.search()` endpoint
   - Returns `List<PlexMetadata>` (mixed results or filtered by type)

2. **ViewModel** (~20 lines)
   - Add `searchQuery: StateFlow<String>` state
   - Add `searchResults: StateFlow<List<PlexMetadata>>` state
   - Add `search(query: String)` function
   - Consider debouncing (300-500ms) to avoid excessive API calls

3. **UI** (~30 lines)
   - Add search icon/field to the tab bar area in `MusicLibraryScreen.kt`
   - Show search results in a list (replacing tab content when searching)
   - Clear search button to return to normal browsing
   - Results show type icon (artist/album/track) for mixed results

**API Endpoint (already exists):**
```kotlin
// PlexServerApi.kt:54
@GET("library/sections/{sectionId}/search")
suspend fun search(
    @Path("sectionId") sectionId: String,
    @Header("X-Plex-Token") token: String,
    @Query("query") query: String,
    @Query("type") type: Int? = null  // 8=artist, 9=album, 10=track
): Response<PlexMediaContainer>
```

**Optional Enhancements:**
- Filter tabs (Artists only, Albums only, Tracks only)
- Search history (recent searches)
- Voice search integration

---

## Priority Order

0. **Improve the media browsing for the Music Player**
1. **Weather widget** – easiest, just subscribe to HA sensors via MQTT
2. **TTS support** – mostly free via existing media_player, minor audio focus handling
3. **HA-driven alarm timing** – no tablet changes, just HA templates
4. **Night mode settings UI** – configure thresholds and brightness

## Some day
1. **Google Photos background** – most complex (OAuth, caching, overlay rendering)
   Since we can't support Google Photos any more, maybe we have to switch to local files

---

## Notes

- All features integrate with existing MQTT/HA architecture
- No changes to core alarm, button, or music player functionality
- Google Photos requires additional API credentials and OAuth flow
- Weather and TTS can be added incrementally without breaking existing features
