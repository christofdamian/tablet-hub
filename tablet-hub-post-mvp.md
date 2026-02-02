# TabletHub - Post-MVP Features

Features to add after the core app (clock/alarms, buttons, music player, HA integration) is working.

---

## ~~1. Google Photos Background~~ ❌ WON'T DO

Google Photos API no longer viable. Would need alternative approach (local files).

---

## ~~2. Weather Widget~~ ✅ DONE

Implemented. See README.md for setup instructions.

---

## ~~3. TTS / Voice Announcements~~ ✅ DONE

Implemented with native Android TTS. Supports plain text or JSON with language. Auto-ducks music during announcements. See README.md for usage.

---

## ~~4. HA-Driven Alarm Timing~~ ✅ DONE

Implemented via `sensor.tablethub_alarm_countdown` which publishes minutes until next alarm. See README.md for automation examples.

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

**~~Settings UI:~~** ✅ DONE
- ~~Lux threshold for auto-switching (default: 15)~~
- ~~Hysteresis value (default: 5)~~
- ~~Night brightness level (default: 5/255)~~
- ~~Enable/disable auto mode vs manual only~~

**~~Brightness Restoration:~~** ✅ DONE
- ~~Remember brightness level before entering night mode~~
- ~~Restore previous brightness when exiting night mode~~

**~~Color Temperature:~~** ✅ DONE (bonus feature)
- Added `number.tablethub_color_temp` entity (0-100% warmth)
- Amber overlay for blue light reduction
- Controllable via HA automations

**~~Gradual Transitions:~~** ❌ WON'T DO
- Conflicts with instant NightClockDisplay switch which is preferred

**~~Sensor Calibration:~~** ✅ DONE
- ~~Add calibration option~~ - "Use Current" button in settings

**~~Time-Based Schedule:~~** ❌ WON'T DO
- Can be handled via HA automations instead

## 7. Improve the media browsing for the Music Player 📋 LATER
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

0. ~~**Weather widget**~~ ✅ DONE
1. ~~**TTS support**~~ ✅ DONE
2. ~~**HA-driven alarm timing**~~ ✅ DONE (sensor.tablethub_alarm_countdown)
3. ~~**Night mode settings UI**~~ ✅ DONE
4. **Media player improvements** 📋 LATER – async loading, scroll bar, search
