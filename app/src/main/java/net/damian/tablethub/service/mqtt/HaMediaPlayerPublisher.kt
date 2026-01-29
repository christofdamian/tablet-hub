package net.damian.tablethub.service.mqtt

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
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

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
        val baseTopic = "tablethub/$deviceId"

        val config = JSONObject().apply {
            put("name", "TabletHub Music")
            put("unique_id", "${deviceId}_media_player")
            put("object_id", "${deviceId}_media_player")
            put("state_topic", "$baseTopic/media_player/state")
            put("command_topic", "$baseTopic/media_player/set")
            put("availability_topic", "$baseTopic/availability")
            put("payload_available", "online")
            put("payload_not_available", "offline")

            // Device info
            put("device", JSONObject().apply {
                put("identifiers", JSONArray().put("tablethub_$deviceId"))
                put("name", "TabletHub")
                put("model", "Tablet Dashboard")
                put("manufacturer", "TabletHub")
            })
        }

        mqttManager.publish(
            topic = "homeassistant/media_player/${deviceId}_player/config",
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

        val stateJson = JSONObject().apply {
            // State: playing, paused, idle
            put("state", when {
                state.isPlaying -> "playing"
                track != null -> "paused"
                else -> "idle"
            })

            // Media info
            if (track != null) {
                put("media_title", track.title)
                put("media_artist", track.grandparentTitle ?: track.parentTitle ?: "")
                put("media_album_name", track.parentTitle ?: "")

                track.duration?.let { duration ->
                    put("media_duration", duration / 1000) // Convert to seconds
                }

                put("media_position", position / 1000) // Convert to seconds
                put("media_position_updated_at", System.currentTimeMillis() / 1000.0)

                // Album art URL
                plexRepository.getArtworkUrl(track.effectiveThumb)?.let { artUrl ->
                    put("media_image_url", artUrl)
                }
            }

            // Queue info
            if (state.queue.isNotEmpty()) {
                put("queue_position", state.currentIndex)
                put("queue_size", state.queue.size)
            }
        }

        mqttManager.publish(
            topic = "tablethub/$deviceId/media_player/state",
            payload = stateJson.toString(),
            retained = true
        )

        Log.d(TAG, "Published media player state: ${if (state.isPlaying) "playing" else "paused"} - ${track?.title ?: "no track"}")
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
                "toggle" -> playbackManager.togglePlayPause()
                "media_play_pause" -> playbackManager.togglePlayPause()
            }
        }
    }
}
