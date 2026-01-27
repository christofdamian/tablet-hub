package net.damian.tablethub.ui.screens.slideshow.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A semi-transparent clock overlay for the slideshow.
 * Displays the current time with a subtle background.
 *
 * @param modifier Modifier for positioning the overlay
 * @param use24HourFormat Whether to use 24-hour time format
 * @param showSeconds Whether to show seconds
 */
@Composable
fun ClockOverlay(
    modifier: Modifier = Modifier,
    use24HourFormat: Boolean = true,
    showSeconds: Boolean = false
) {
    var currentTime by remember { mutableStateOf(getCurrentTime(use24HourFormat, showSeconds)) }

    // Update time every second
    LaunchedEffect(use24HourFormat, showSeconds) {
        while (true) {
            currentTime = getCurrentTime(use24HourFormat, showSeconds)
            delay(1000L)
        }
    }

    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = currentTime,
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
            color = Color.White,
            letterSpacing = 2.sp
        )
    }
}

private fun getCurrentTime(use24HourFormat: Boolean, showSeconds: Boolean): String {
    val pattern = buildString {
        append(if (use24HourFormat) "HH:mm" else "h:mm")
        if (showSeconds) append(":ss")
        if (!use24HourFormat) append(" a")
    }
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date())
}
