# TabletHub - Voice Commands Implementation Plan

Always-listening voice commands using wake word detection and Android SpeechRecognizer.

---

## Overview

Add hands-free voice control to TabletHub with a wake word ("Hey Tablet" or similar) followed by commands like "snooze", "dismiss alarm", or button names.

**Architecture:**
```
[Microphone] → [Wake Word Engine] → [SpeechRecognizer] → [Command Parser] → [Action Executor]
                  (always on)         (on-demand)          (text match)      (existing services)
```

**Key Design Decisions:**
- Two-stage recognition: lightweight wake word detection + full speech recognition
- Wake word runs continuously with low CPU/battery impact
- SpeechRecognizer only activates after wake word detected
- Reuse existing action entry points (AlarmService, HaServiceCaller, etc.)

---

## 1. Wake Word Library Selection

### Option A: Porcupine by Picovoice (Recommended)

**Pros:**
- Offline, on-device processing
- Low CPU/memory footprint (~2MB model)
- Custom wake word support
- Free tier: 3 custom wake words, unlimited activations
- Well-documented Android SDK

**Cons:**
- Requires API key (free tier available)
- Custom wake words need training via console

**Integration:**
```kotlin
dependencies {
    implementation("ai.picovoice:porcupine-android:3.0.0")
}
```

### Option B: Vosk

**Pros:**
- Fully open source, no API key
- Offline recognition
- Can do continuous keyword spotting

**Cons:**
- Larger models (~50MB for English)
- Higher CPU usage than Porcupine
- More complex setup

### Option C: Android Hotword API

**Pros:**
- Native Android, no dependencies

**Cons:**
- Only works with "OK Google" / "Hey Google"
- Requires Google Play Services
- Not customizable

**Recommendation:** Start with Porcupine for simplicity and efficiency. Fall back to Vosk if API key requirement is problematic.

---

## 2. Architecture Design

### New Files

```
app/src/main/java/net/damian/tablethub/
├── service/voice/
│   ├── VoiceCommandService.kt      # Foreground service managing voice pipeline
│   ├── WakeWordDetector.kt         # Porcupine wrapper for wake word detection
│   ├── SpeechRecognitionManager.kt # Android SpeechRecognizer wrapper
│   ├── CommandParser.kt            # Maps recognized text to actions
│   └── VoiceCommandExecutor.kt     # Executes parsed commands via existing services
├── data/preferences/
│   └── VoiceSettings.kt            # Voice-related settings (enabled, wake word, etc.)
└── ui/screens/settings/
    └── VoiceSettingsSection.kt     # Settings UI for voice commands
```

### Service Lifecycle

```
App Launch
    ↓
VoiceCommandService starts (foreground, low-priority notification)
    ↓
WakeWordDetector initializes Porcupine, starts listening
    ↓
[Continuous listening - low CPU]
    ↓
Wake word detected ("Hey Tablet")
    ↓
Audio feedback (beep/vibration)
    ↓
SpeechRecognitionManager starts (5 second timeout)
    ↓
Speech recognized → CommandParser
    ↓
Command matched → VoiceCommandExecutor
    ↓
Action executed → Return to wake word listening
```

### Audio Focus Strategy

```kotlin
// During wake word detection: No audio focus needed (passive listening)

// After wake word detected:
// 1. Request AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
// 2. Lower alarm/music volume temporarily
// 3. Play acknowledgment beep
// 4. Start speech recognition
// 5. On result/timeout: release focus, restore volume
```

---

## 3. Implementation Steps

### Phase 1: Core Infrastructure (~4 hours)

**1.1 Add Dependencies**

```kotlin
// build.gradle.kts (app)
dependencies {
    // Porcupine wake word
    implementation("ai.picovoice:porcupine-android:3.0.0")
}
```

**1.2 Add Permissions**

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<service
    android:name=".service.voice.VoiceCommandService"
    android:foregroundServiceType="microphone"
    android:exported="false" />
```

**1.3 VoiceCommandService.kt**

```kotlin
@AndroidEntryPoint
class VoiceCommandService : Service() {
    @Inject lateinit var wakeWordDetector: WakeWordDetector
    @Inject lateinit var speechManager: SpeechRecognitionManager
    @Inject lateinit var commandExecutor: VoiceCommandExecutor

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        startListening()
    }

    private fun startListening() {
        scope.launch {
            wakeWordDetector.detections.collect {
                onWakeWordDetected()
            }
        }
    }

    private suspend fun onWakeWordDetected() {
        // 1. Play acknowledgment
        playAcknowledgmentSound()

        // 2. Pause wake word detection
        wakeWordDetector.pause()

        // 3. Start speech recognition
        val result = speechManager.recognizeSpeech(timeoutMs = 5000)

        // 4. Parse and execute
        result?.let { text ->
            val command = CommandParser.parse(text)
            command?.let { commandExecutor.execute(it) }
        }

        // 5. Resume wake word detection
        wakeWordDetector.resume()
    }

    companion object {
        const val NOTIFICATION_ID = 3001
    }
}
```

**1.4 WakeWordDetector.kt**

```kotlin
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false

    private val _detections = MutableSharedFlow<Unit>()
    val detections: SharedFlow<Unit> = _detections.asSharedFlow()

    fun start(accessKey: String) {
        porcupine = Porcupine.Builder()
            .setAccessKey(accessKey)
            .setKeyword(Porcupine.BuiltInKeyword.HEY_SIRI) // or custom
            .build(context)

        startAudioCapture()
    }

    private fun startAudioCapture() {
        val bufferSize = porcupine!!.frameLength
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            porcupine!!.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        isListening = true
        audioRecord?.startRecording()

        thread {
            val buffer = ShortArray(bufferSize)
            while (isListening) {
                audioRecord?.read(buffer, 0, buffer.size)
                val keywordIndex = porcupine?.process(buffer) ?: -1
                if (keywordIndex >= 0) {
                    _detections.tryEmit(Unit)
                }
            }
        }
    }

    fun pause() { isListening = false }
    fun resume() { isListening = true; startAudioCapture() }
    fun stop() { /* cleanup */ }
}
```

### Phase 2: Speech Recognition (~2 hours)

**2.1 SpeechRecognitionManager.kt**

```kotlin
@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    suspend fun recognizeSpeech(timeoutMs: Long): String? = suspendCancellableCoroutine { cont ->
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                cont.resume(matches?.firstOrNull())
            }

            override fun onError(error: Int) {
                cont.resume(null)
            }

            // ... other callbacks
        })

        speechRecognizer?.startListening(intent)

        // Timeout handling
        Handler(Looper.getMainLooper()).postDelayed({
            if (cont.isActive) {
                speechRecognizer?.stopListening()
                cont.resume(null)
            }
        }, timeoutMs)
    }
}
```

### Phase 3: Command Parsing (~1 hour)

**3.1 CommandParser.kt**

```kotlin
sealed class VoiceCommand {
    object Snooze : VoiceCommand()
    object DismissAlarm : VoiceCommand()
    object StopMusic : VoiceCommand()
    object NextTrack : VoiceCommand()
    object PreviousTrack : VoiceCommand()
    data class PressButton(val buttonLabel: String) : VoiceCommand()
    data class SetBrightness(val level: Int) : VoiceCommand() // 0-100
    object NightModeOn : VoiceCommand()
    object NightModeOff : VoiceCommand()
}

object CommandParser {
    private val patterns = listOf(
        // Alarm commands
        Regex("(?i)(snooze|snooze it|snooze alarm)") to { _: MatchResult -> VoiceCommand.Snooze },
        Regex("(?i)(dismiss|stop|turn off|shut up|dismiss alarm|stop alarm)") to { _: MatchResult -> VoiceCommand.DismissAlarm },

        // Music commands
        Regex("(?i)(stop music|pause|pause music)") to { _: MatchResult -> VoiceCommand.StopMusic },
        Regex("(?i)(next|skip|next track|next song)") to { _: MatchResult -> VoiceCommand.NextTrack },
        Regex("(?i)(previous|back|previous track|last song)") to { _: MatchResult -> VoiceCommand.PreviousTrack },

        // Display commands
        Regex("(?i)brightness (\\d+)") to { m: MatchResult ->
            VoiceCommand.SetBrightness(m.groupValues[1].toInt().coerceIn(0, 100))
        },
        Regex("(?i)(night mode|night mode on|dim)") to { _: MatchResult -> VoiceCommand.NightModeOn },
        Regex("(?i)(day mode|night mode off|bright)") to { _: MatchResult -> VoiceCommand.NightModeOff },

        // Button commands - will be dynamically expanded with actual button labels
        Regex("(?i)press (.+)") to { m: MatchResult -> VoiceCommand.PressButton(m.groupValues[1]) },
        Regex("(?i)(.+) button") to { m: MatchResult -> VoiceCommand.PressButton(m.groupValues[1]) },
    )

    fun parse(text: String): VoiceCommand? {
        for ((pattern, factory) in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return factory(match)
            }
        }
        return null
    }
}
```

### Phase 4: Command Execution (~1 hour)

**4.1 VoiceCommandExecutor.kt**

```kotlin
@Singleton
class VoiceCommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmScheduler: AlarmScheduler,
    private val snoozeManager: SnoozeManager,
    private val haServiceCaller: HaServiceCaller,
    private val playbackManager: PlaybackManager,
    private val screenManager: ScreenManager,
    private val nightModeManager: NightModeManager,
    private val buttonRepository: ButtonRepository
) {
    suspend fun execute(command: VoiceCommand) {
        when (command) {
            is VoiceCommand.Snooze -> {
                // Send snooze intent to AlarmService
                val intent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_SNOOZE_ALARM
                }
                context.startService(intent)
            }

            is VoiceCommand.DismissAlarm -> {
                val intent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_STOP_ALARM
                }
                context.startService(intent)
            }

            is VoiceCommand.StopMusic -> {
                playbackManager.pause()
            }

            is VoiceCommand.NextTrack -> {
                playbackManager.skipToNext()
            }

            is VoiceCommand.PreviousTrack -> {
                playbackManager.skipToPrevious()
            }

            is VoiceCommand.SetBrightness -> {
                val brightness = (command.level * 255 / 100)
                screenManager.setBrightness(brightness)
            }

            is VoiceCommand.NightModeOn -> {
                nightModeManager.setNightMode(true)
            }

            is VoiceCommand.NightModeOff -> {
                nightModeManager.setNightMode(false)
            }

            is VoiceCommand.PressButton -> {
                // Find button by label (fuzzy match)
                val buttons = buttonRepository.getAllButtons().first()
                val matchedButton = buttons.find {
                    it.label.equals(command.buttonLabel, ignoreCase = true) ||
                    it.label.contains(command.buttonLabel, ignoreCase = true)
                }
                matchedButton?.let { haServiceCaller.sendButtonPress(it) }
            }
        }
    }
}
```

### Phase 5: Settings UI (~1 hour)

**5.1 Add to SettingsDataStore.kt**

```kotlin
// New preferences
val voiceCommandsEnabled: Flow<Boolean>
suspend fun setVoiceCommandsEnabled(enabled: Boolean)

val wakeWord: Flow<String> // "hey_tablet", "hey_siri", etc.
suspend fun setWakeWord(word: String)

val voiceFeedbackEnabled: Flow<Boolean> // Play beep on wake word
suspend fun setVoiceFeedbackEnabled(enabled: Boolean)
```

**5.2 Add VoiceSettingsSection to SettingsScreen.kt**

```kotlin
// Voice Commands section
SettingsSection(title = "Voice Commands") {
    // Enable/disable toggle
    SwitchPreference(
        title = "Enable Voice Commands",
        subtitle = "Say 'Hey Tablet' followed by a command",
        checked = voiceEnabled,
        onCheckedChange = { viewModel.setVoiceEnabled(it) }
    )

    if (voiceEnabled) {
        // Wake word selection
        DropdownPreference(
            title = "Wake Word",
            options = listOf("Hey Tablet", "OK Tablet", "Computer"),
            selected = wakeWord,
            onSelected = { viewModel.setWakeWord(it) }
        )

        // Audio feedback toggle
        SwitchPreference(
            title = "Audio Feedback",
            subtitle = "Play sound when wake word detected",
            checked = audioFeedback,
            onCheckedChange = { viewModel.setAudioFeedback(it) }
        )

        // Test button
        Button(onClick = { viewModel.testVoiceCommand() }) {
            Text("Test Voice Recognition")
        }
    }
}
```

### Phase 6: Integration & Polish (~2 hours)

**6.1 Start service from MainActivity**

```kotlin
// MainActivity.kt onCreate()
if (settingsDataStore.voiceCommandsEnabled.first()) {
    startVoiceCommandService()
}
```

**6.2 Runtime permission handling**

```kotlin
// Request RECORD_AUDIO permission before enabling voice commands
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        viewModel.setVoiceEnabled(true)
    }
}

// In settings toggle
if (!hasRecordPermission) {
    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
}
```

**6.3 Visual feedback during listening**

```kotlin
// Optional: Show a small indicator when listening
// Could be a pulsing microphone icon in the status bar area
```

---

## 4. Supported Commands

| Voice Command | Action | Entry Point |
|---------------|--------|-------------|
| "Snooze" / "Snooze it" | Snooze current alarm | `AlarmService.ACTION_SNOOZE_ALARM` |
| "Dismiss" / "Stop alarm" | Dismiss current alarm | `AlarmService.ACTION_STOP_ALARM` |
| "Stop music" / "Pause" | Pause playback | `PlaybackManager.pause()` |
| "Next" / "Skip" | Next track | `PlaybackManager.skipToNext()` |
| "Previous" / "Back" | Previous track | `PlaybackManager.skipToPrevious()` |
| "Brightness 50" | Set brightness to 50% | `ScreenManager.setBrightness()` |
| "Night mode" / "Dim" | Enable night mode | `NightModeManager.setNightMode(true)` |
| "Day mode" / "Bright" | Disable night mode | `NightModeManager.setNightMode(false)` |
| "Press [button name]" | Execute quick button | `HaServiceCaller.sendButtonPress()` |
| "[button name] button" | Execute quick button | `HaServiceCaller.sendButtonPress()` |

---

## 5. Audio Handling Considerations

### Challenge: Recognizing speech during alarm

When the alarm is ringing, the SpeechRecognizer may struggle to hear the user.

**Solutions:**

1. **Duck alarm audio during listening**
   ```kotlin
   // When wake word detected:
   alarmService.setVolume(0.2f) // Lower to 20%
   // After recognition completes:
   alarmService.setVolume(1.0f) // Restore
   ```

2. **Pause alarm briefly**
   ```kotlin
   // More aggressive: pause alarm sound for 5 seconds during recognition
   alarmService.pauseSound()
   delay(5000)
   alarmService.resumeSound()
   ```

3. **Use noise suppression** (Android API 30+)
   ```kotlin
   intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, true)
   // Built-in noise cancellation in newer Android versions
   ```

### Challenge: Battery consumption

Wake word detection runs continuously, but Porcupine is designed for this:
- ~1-2% CPU usage
- ~10-20mA current draw
- Acceptable for a tablet that's usually plugged in

**Optimization:**
- Disable voice commands when screen is off (optional setting)
- Use Android's battery optimization exclusion for the service

---

## 6. Testing Plan

### Unit Tests

```kotlin
class CommandParserTest {
    @Test
    fun `parse snooze variations`() {
        assertEquals(VoiceCommand.Snooze, CommandParser.parse("snooze"))
        assertEquals(VoiceCommand.Snooze, CommandParser.parse("Snooze it"))
        assertEquals(VoiceCommand.Snooze, CommandParser.parse("SNOOZE ALARM"))
    }

    @Test
    fun `parse button commands`() {
        val cmd = CommandParser.parse("press lights")
        assertTrue(cmd is VoiceCommand.PressButton)
        assertEquals("lights", (cmd as VoiceCommand.PressButton).buttonLabel)
    }

    @Test
    fun `parse brightness with value`() {
        val cmd = CommandParser.parse("brightness 75")
        assertTrue(cmd is VoiceCommand.SetBrightness)
        assertEquals(75, (cmd as VoiceCommand.SetBrightness).level)
    }
}
```

### Integration Tests

1. Wake word detection accuracy (various distances, noise levels)
2. Speech recognition accuracy for each command
3. End-to-end: wake word → command → action execution
4. Audio focus handling during alarm
5. Service lifecycle (start, stop, restart after crash)

### Manual Testing Checklist

- [ ] Wake word detected from 1m, 2m, 3m distance
- [ ] Wake word works with background music playing
- [ ] Snooze command works during alarm
- [ ] Dismiss command works during alarm
- [ ] Button commands match configured button labels
- [ ] Service survives app backgrounding
- [ ] Service restarts after device reboot (if enabled)
- [ ] Permission flow works correctly
- [ ] Settings toggles work

---

## 7. Future Enhancements

### Custom Wake Words
- Train custom wake word "Hey Tablet" via Picovoice console
- Allow user to choose from preset options

### Command Confirmation
- TTS feedback: "Snoozed for 10 minutes"
- Visual feedback: Brief toast or animation

### Dynamic Button Commands
- Automatically update CommandParser patterns when buttons are configured
- Support synonyms: "turn on lights" → "lights" button

### MQTT Voice Commands
- Publish voice events to HA: `tablethub/{id}/voice/command`
- Allow HA automations to respond to voice

### Offline Speech Recognition
- Use Vosk for fully offline speech-to-text
- Larger model but no internet required

### Multi-language Support
- Detect device language for speech recognition
- Translate command patterns

---

## 8. Effort Estimate

| Phase | Description | Effort |
|-------|-------------|--------|
| 1 | Core infrastructure (service, wake word) | ~4 hours |
| 2 | Speech recognition integration | ~2 hours |
| 3 | Command parsing | ~1 hour |
| 4 | Command execution | ~1 hour |
| 5 | Settings UI | ~1 hour |
| 6 | Integration & polish | ~2 hours |
| 7 | Testing & debugging | ~3 hours |
| **Total** | | **~14 hours** |

---

## 9. Dependencies to Add

```kotlin
// build.gradle.kts (app)
dependencies {
    // Porcupine wake word detection
    implementation("ai.picovoice:porcupine-android:3.0.0")

    // Optional: Vosk for offline speech recognition
    // implementation("com.alphacephei:vosk-android:0.3.47")
}
```

---

## 10. Porcupine API Key

Free tier available at https://picovoice.ai/

1. Create account
2. Get AccessKey from console
3. Store in `local.properties` (not committed):
   ```
   PICOVOICE_ACCESS_KEY=your_key_here
   ```
4. Load in build.gradle:
   ```kotlin
   buildConfigField("String", "PICOVOICE_KEY",
       "\"${project.findProperty("PICOVOICE_ACCESS_KEY") ?: ""}\"")
   ```
