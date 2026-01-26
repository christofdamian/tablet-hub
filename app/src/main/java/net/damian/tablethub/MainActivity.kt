package net.damian.tablethub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import net.damian.tablethub.service.display.ScreenManager
import net.damian.tablethub.service.mqtt.MqttService
import net.damian.tablethub.ui.navigation.AppNavigation
import net.damian.tablethub.ui.theme.TabletHubTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set up screen manager
        ScreenManager.setActivity(this)

        // Start MQTT service
        MqttService.start(this)

        setContent {
            TabletHubTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        ScreenManager.clearActivity()
        super.onDestroy()
    }
}
