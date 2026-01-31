package net.damian.tablethub.service.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages text-to-speech using Android's native TTS engine.
 * Receives text via MQTT and speaks it, with optional audio ducking.
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TtsManager"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val pendingUtterances = mutableSetOf<String>()
    private var pendingMessage: Pair<String, String?>? = null  // message, language

    fun initialize() {
        if (tts != null) return

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(utteranceListener)
                Log.d(TAG, "TTS initialized successfully")

                // Speak any pending message that was queued before initialization
                pendingMessage?.let { (message, language) ->
                    pendingMessage = null
                    speak(message, language)
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(message: String, language: String? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, queuing message and initializing")
            pendingMessage = message to language
            initialize()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        pendingUtterances.add(utteranceId)

        // Set language if specified
        language?.let { lang ->
            val locale = Locale.forLanguageTag(lang)
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language $lang not supported, using default")
                tts?.language = Locale.getDefault()
            }
        }

        // Request audio focus with ducking
        requestAudioFocus()

        Log.d(TAG, "Speaking: $message")
        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun requestAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .build()

        audioFocusRequest?.let {
            audioManager.requestAudioFocus(it)
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d(TAG, "TTS started: $utteranceId")
        }

        override fun onDone(utteranceId: String?) {
            Log.d(TAG, "TTS done: $utteranceId")
            utteranceId?.let { pendingUtterances.remove(it) }
            if (pendingUtterances.isEmpty()) {
                abandonAudioFocus()
            }
        }

        @Deprecated("Deprecated in API 21")
        override fun onError(utteranceId: String?) {
            Log.e(TAG, "TTS error: $utteranceId")
            utteranceId?.let { pendingUtterances.remove(it) }
            if (pendingUtterances.isEmpty()) {
                abandonAudioFocus()
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e(TAG, "TTS error: $utteranceId, code: $errorCode")
            utteranceId?.let { pendingUtterances.remove(it) }
            if (pendingUtterances.isEmpty()) {
                abandonAudioFocus()
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        abandonAudioFocus()
        Log.d(TAG, "TTS shutdown")
    }
}
