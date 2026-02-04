package net.damian.tablethub.service.music

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import net.damian.tablethub.MainActivity
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "MusicPlaybackService"
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null

    @Inject
    lateinit var playbackManager: PlaybackManager

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exoPlayer ->
                // Initialize equalizer with bass boost
                setupEqualizer(exoPlayer.audioSessionId)
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        playbackManager.onPlaybackStateChanged(playbackState)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playbackManager.onIsPlayingChanged(isPlaying)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        playbackManager.onMediaItemTransition(mediaItem)
                    }
                })

                val sessionIntent = Intent(this, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    sessionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                mediaSession = MediaSession.Builder(this, exoPlayer)
                    .setSessionActivity(pendingIntent)
                    .build()

                playbackManager.initialize(exoPlayer)
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun setupEqualizer(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true

                // Get the level range (typically -1500 to 1500 millibels)
                val minLevel = bandLevelRange[0]
                val maxLevel = bandLevelRange[1]

                // Boost bass frequencies (typically bands 0 and 1)
                // Band 0: ~60 Hz (sub-bass)
                // Band 1: ~230 Hz (bass)
                val bassBoost = (maxLevel * 0.7).toInt().toShort() // 70% of max boost

                for (band in 0 until numberOfBands) {
                    val centerFreq = getCenterFreq(band.toShort()) / 1000 // Convert to Hz
                    when {
                        centerFreq < 250 -> {
                            // Sub-bass and bass - full boost
                            setBandLevel(band.toShort(), bassBoost)
                            Log.d(TAG, "Band $band (${centerFreq}Hz): boosted to $bassBoost")
                        }
                        centerFreq < 500 -> {
                            // Low-mids - slight boost for warmth
                            val warmth = (maxLevel * 0.3).toInt().toShort()
                            setBandLevel(band.toShort(), warmth)
                            Log.d(TAG, "Band $band (${centerFreq}Hz): warmth at $warmth")
                        }
                        else -> {
                            // Keep mids and highs flat
                            setBandLevel(band.toShort(), 0)
                            Log.d(TAG, "Band $band (${centerFreq}Hz): flat")
                        }
                    }
                }
            }
            Log.d(TAG, "Equalizer initialized with bass boost")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize equalizer", e)
        }
    }

    override fun onDestroy() {
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player?.release()
        player = null
        super.onDestroy()
    }
}
