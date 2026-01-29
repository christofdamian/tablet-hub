package net.damian.tablethub.service.mqtt

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.damian.tablethub.data.preferences.SettingsDataStore
import net.damian.tablethub.plex.PlexRepository
import net.damian.tablethub.service.music.PlaybackManager
import net.damian.tablethub.service.music.PlaybackState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes media player state to Home Assistant via MQTT.
 * Compatible with bkbilly/mqtt_media_player custom integration.
 * https://github.com/bkbilly/mqtt_media_player
 */
@Singleton
class HaMediaPlayerPublisher @Inject constructor(
    private val mqttManager: MqttManager,
    private val playbackManager: PlaybackManager,
    private val plexRepository: PlexRepository,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "HaMediaPlayerPublisher"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isPublishing = false
    private var deviceId: String = "tablethub"
    private var lastArtworkUrl: String? = null
    private var cachedArtworkBase64: String? = null
    private val httpClient = OkHttpClient()

    private val baseTopic: String get() = "tablethub/$deviceId/media"

    fun startPublishing() {
        if (isPublishing) return
        isPublishing = true

        Log.d(TAG, "Starting media player state publishing")

        scope.launch {
            // Load device ID
            deviceId = settingsDataStore.deviceId.first()

            // Publish discovery config
            publishDiscoveryConfig()

            // Subscribe to playback state changes
            playbackManager.playbackState
                .onEach { state -> publishState(state) }
                .launchIn(scope)
        }
    }

    private fun publishDiscoveryConfig() {
        val config = JSONObject().apply {
            put("name", "TabletHub Music")
            put("unique_id", "${deviceId}_media_player")
            put("object_id", "${deviceId}_media_player")

            // Availability
            put("availability", JSONObject().apply {
                put("topic", "tablethub/$deviceId/availability")
                put("payload_available", "online")
                put("payload_not_available", "offline")
            })

            // State topics (what we publish)
            put("state_state_topic", "$baseTopic/state")
            put("state_title_topic", "$baseTopic/title")
            put("state_artist_topic", "$baseTopic/artist")
            put("state_album_topic", "$baseTopic/album")
            put("state_duration_topic", "$baseTopic/duration")
            put("state_position_topic", "$baseTopic/position")
            put("state_albumart_topic", "$baseTopic/albumart")

            // Command topics and payloads (what we listen to)
            put("command_play_topic", "$baseTopic/cmd/play")
            put("command_play_payload", "play")
            put("command_pause_topic", "$baseTopic/cmd/pause")
            put("command_pause_payload", "pause")
            put("command_playpause_topic", "$baseTopic/cmd/playpause")
            put("command_playpause_payload", "playpause")
            put("command_next_topic", "$baseTopic/cmd/next")
            put("command_next_payload", "next")
            put("command_previous_topic", "$baseTopic/cmd/previous")
            put("command_previous_payload", "previous")

            // Device info
            put("device", JSONObject().apply {
                put("identifiers", JSONArray().put("tablethub_$deviceId"))
                put("name", "TabletHub")
                put("model", "Tablet Dashboard")
                put("manufacturer", "TabletHub")
            })
        }

        val topic = "homeassistant/media_player/${deviceId}_music/config"
        Log.d(TAG, "Publishing media player discovery to: $topic")

        // Publish availability FIRST - required for HA to show the entity as available
        mqttManager.publish(
            topic = "tablethub/$deviceId/availability",
            payload = "online",
            retained = true
        )

        // Then publish the discovery config
        mqttManager.publish(
            topic = topic,
            payload = config.toString(),
            retained = true
        )

        Log.d(TAG, "Published media player discovery config")
    }

    private suspend fun publishState(state: PlaybackState) {
        val track = state.currentTrack

        // Get position on main thread since MediaController requires it
        val position = withContext(Dispatchers.Main) {
            playbackManager.getCurrentPosition()
        }

        // Publish state
        val playbackState = when {
            state.isPlaying -> "playing"
            track != null -> "paused"
            else -> "idle"
        }
        mqttManager.publish("$baseTopic/state", playbackState, retained = true)

        // Publish track info
        if (track != null) {
            mqttManager.publish("$baseTopic/title", track.title, retained = true)
            mqttManager.publish("$baseTopic/artist", track.grandparentTitle ?: track.parentTitle ?: "", retained = true)
            mqttManager.publish("$baseTopic/album", track.parentTitle ?: "", retained = true)
            mqttManager.publish("$baseTopic/duration", ((track.duration ?: 0) / 1000).toString(), retained = true)
            mqttManager.publish("$baseTopic/position", (position / 1000).toString(), retained = true)

            // Fetch and publish album art as base64
            val artworkUrl = plexRepository.getArtworkUrl(track.effectiveThumb)
            if (artworkUrl != null && artworkUrl != lastArtworkUrl) {
                lastArtworkUrl = artworkUrl
                fetchAndPublishArtwork(artworkUrl)
            } else if (artworkUrl == lastArtworkUrl && cachedArtworkBase64 != null) {
                // Use cached artwork
                mqttManager.publish("$baseTopic/albumart", cachedArtworkBase64!!, retained = true)
            }
        } else {
            // Clear track info when nothing is playing
            mqttManager.publish("$baseTopic/title", "", retained = true)
            mqttManager.publish("$baseTopic/artist", "", retained = true)
            mqttManager.publish("$baseTopic/album", "", retained = true)
            mqttManager.publish("$baseTopic/duration", "0", retained = true)
            mqttManager.publish("$baseTopic/position", "0", retained = true)
            mqttManager.publish("$baseTopic/albumart", "", retained = true)
            lastArtworkUrl = null
            cachedArtworkBase64 = null
        }

        Log.d(TAG, "Published media player state: $playbackState - ${track?.title ?: "no track"}")
    }

    private fun fetchAndPublishArtwork(url: String) {
        scope.launch {
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        cachedArtworkBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        mqttManager.publish("$baseTopic/albumart", cachedArtworkBase64!!, retained = true)
                        Log.d(TAG, "Published album art (${bytes.size} bytes)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch album art", e)
            }
        }
    }

    fun handleCommand(command: String, payload: String?) {
        Log.d(TAG, "Received media player command: $command, payload: $payload")

        // MediaController methods must be called on main thread
        mainScope.launch {
            when (command.lowercase()) {
                "play" -> playbackManager.play()
                "pause" -> playbackManager.pause()
                "stop" -> playbackManager.stop()
                "next", "next_track" -> playbackManager.skipToNext()
                "previous", "previous_track" -> playbackManager.skipToPrevious()
                "toggle", "playpause" -> playbackManager.togglePlayPause()
            }
        }
    }
}
