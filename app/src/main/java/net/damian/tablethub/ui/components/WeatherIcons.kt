package net.damian.tablethub.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps Home Assistant weather conditions to Material Icons.
 */
object WeatherIcons {
    fun getIconForCondition(condition: String?): ImageVector {
        return when (condition?.lowercase()) {
            "sunny", "clear", "clear-night" -> Icons.Filled.WbSunny
            "cloudy" -> Icons.Filled.Cloud
            "partlycloudy", "partly-cloudy", "partly_cloudy" -> Icons.Filled.WbCloudy
            "rainy", "pouring", "rain", "showers" -> Icons.Filled.WaterDrop
            "lightning", "lightning-rainy", "thunderstorm" -> Icons.Filled.Thunderstorm
            "windy", "windy-variant" -> Icons.Filled.Air
            "snowy", "snowy-rainy", "snow" -> Icons.Filled.Cloud
            "fog", "hail" -> Icons.Filled.Cloud
            else -> Icons.Filled.WbCloudy
        }
    }
}
