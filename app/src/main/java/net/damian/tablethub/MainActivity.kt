package net.damian.tablethub

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import net.damian.tablethub.service.display.ColorTemperatureManager
import net.damian.tablethub.service.display.LightSensorService
import net.damian.tablethub.service.display.NightModeManager
import net.damian.tablethub.service.display.ScreenManager
import net.damian.tablethub.service.mqtt.MqttService
import net.damian.tablethub.ui.navigation.AppNavigation
import net.damian.tablethub.ui.screens.clock.NightClockDisplay
import net.damian.tablethub.ui.screens.settings.SettingsScreen
import net.damian.tablethub.ui.theme.TabletHubTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var nightModeManager: NightModeManager

    @Inject
    lateinit var lightSensorService: LightSensorService

    @Inject
    lateinit var colorTemperatureManager: ColorTemperatureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set up screen manager
        ScreenManager.setActivity(this)

        // Start MQTT service
        MqttService.start(this)

        // Light sensor is managed by LightSensorService based on auto-sensing state

        setContent {
            val nightModeState by nightModeManager.nightModeState.collectAsState()
            var showSettings by remember { mutableStateOf(false) }

            TabletHubTheme(nightMode = nightModeState.isActive) {
                if (showSettings) {
                    // Show settings screen
                    SettingsScreen(
                        onDismiss = { showSettings = false }
                    )
                } else if (nightModeState.isActive) {
                    // Show minimal night clock display
                    NightClockDisplay(
                        onTap = { nightModeManager.toggleNightMode() },
                        dimOverlay = nightModeState.dimOverlay
                    )
                } else {
                    // Show normal UI
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        AppNavigation(
                            modifier = Modifier.padding(innerPadding),
                            isNightModeActive = nightModeState.isActive,
                            onNightModeToggle = { nightModeManager.toggleNightMode() },
                            onSettingsClick = { showSettings = true }
                        )
                    }
                }
            }
        }

        // Initialize color temperature overlay after setContent
        colorTemperatureManager.setActivity(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Reset wake timer on any touch to extend the wake period
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            nightModeManager.onUserInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        lightSensorService.onActivityResumed()
    }

    override fun onPause() {
        super.onPause()
        lightSensorService.onActivityPaused()
    }

    override fun onDestroy() {
        lightSensorService.onActivityPaused()
        colorTemperatureManager.clearActivity()
        ScreenManager.clearActivity()
        super.onDestroy()
    }
}
