package net.damian.tablethub.service.mqtt

import android.content.Context
import android.media.AudioManager
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
            put("state_volume_topic", "$baseTopic/volume")

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
            put("command_playmedia_topic", "$baseTopic/cmd/playmedia")
            put("command_volume_topic", "$baseTopic/cmd/volume")

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

        // Publish volume (0.0 to 1.0)
        val volumeLevel = getVolumeLevel()
        mqttManager.publish("$baseTopic/volume", volumeLevel.toString(), retained = true)

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

        // Handle playmedia command separately as it requires async operations
        if (command.lowercase() == "playmedia") {
            handlePlayMedia(payload ?: command)
            return
        }

        // Handle volume command
        if (command.lowercase() == "volume" && payload != null) {
            handleVolumeCommand(payload)
            return
        }

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

    private fun handleVolumeCommand(payload: String) {
        try {
            val volume = payload.toFloat().coerceIn(0f, 1f)
            setVolumeLevel(volume)
            // Publish updated volume
            mqttManager.publish("$baseTopic/volume", volume.toString(), retained = true)
            Log.d(TAG, "Volume set to: $volume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse volume: $payload", e)
        }
    }

    private fun getVolumeLevel(): Float {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f
    }

    private fun setVolumeLevel(level: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (level * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    }

    private fun handlePlayMedia(payload: String) {
        scope.launch {
            try {
                val json = JSONObject(payload)
                // Support both bkbilly format (media_type/media_id) and standard HA format (media_content_type/media_content_id)
                val contentType = json.optString("media_type", "").ifEmpty {
                    json.optString("media_content_type", "")
                }
                val contentId = json.optString("media_id", "").ifEmpty {
                    json.optString("media_content_id", "")
                }

                Log.d(TAG, "Play media request: type=$contentType, id=$contentId")

                when (contentType.lowercase()) {
                    "playlist" -> playPlaylist(contentId)
                    else -> Log.w(TAG, "Unsupported media content type: $contentType")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse play_media command: $payload", e)
            }
        }
    }

    private suspend fun playPlaylist(playlistName: String) {
        Log.d(TAG, "Looking up playlist: $playlistName")

        // Get all playlists
        val playlistsResult = plexRepository.getPlaylists()
        if (playlistsResult.isFailure) {
            Log.e(TAG, "Failed to get playlists: ${playlistsResult.exceptionOrNull()?.message}")
            return
        }

        val playlists = playlistsResult.getOrNull() ?: return

        // Find playlist by name (case-insensitive)
        val playlist = playlists.find { it.title.equals(playlistName, ignoreCase = true) }
        if (playlist == null) {
            Log.w(TAG, "Playlist not found: $playlistName")
            return
        }

        Log.d(TAG, "Found playlist: ${playlist.title} (${playlist.ratingKey})")

        // Get playlist tracks (limit to 100 to avoid timeout on large playlists)
        val maxTracks = 100
        val tracksResult = plexRepository.getPlaylistTracks(playlist.ratingKey, limit = maxTracks)
        if (tracksResult.isFailure) {
            Log.e(TAG, "Failed to get playlist tracks: ${tracksResult.exceptionOrNull()?.message}")
            return
        }

        val tracks = tracksResult.getOrNull()
        if (tracks.isNullOrEmpty()) {
            Log.w(TAG, "Playlist is empty: $playlistName")
            return
        }

        // Filter tracks that have valid media info
        val validTracks = tracks.filter { track ->
            val hasMedia = track.media?.firstOrNull()?.parts?.firstOrNull()?.key != null
            if (!hasMedia) {
                Log.w(TAG, "Track '${track.title}' has no media info, skipping")
            }
            hasMedia
        }

        if (validTracks.isEmpty()) {
            Log.w(TAG, "No valid tracks in playlist: $playlistName")
            return
        }

        // Shuffle tracks for variety (same behavior as alarm playlists)
        val shuffledTracks = validTracks.shuffled()

        Log.d(TAG, "Starting shuffled playback of ${shuffledTracks.size} tracks from playlist: $playlistName")

        // Start playback on main thread
        withContext(Dispatchers.Main) {
            playbackManager.playQueue(shuffledTracks, 0)
        }
    }
}
