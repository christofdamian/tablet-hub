package net.damian.tablethub.ui.screens.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.damian.tablethub.ui.theme.NightBackground
import net.damian.tablethub.ui.theme.NightRed
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Minimal night mode clock display.
 * Shows only the time in red on a pure black background.
 * Tap anywhere to exit night mode.
 */
@Composable
fun NightClockDisplay(
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            // Update every second
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    // Use currentTimeMillis to trigger recomposition
    val time = remember(currentTimeMillis) { LocalTime.now() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NightBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = time.format(timeFormatter),
            fontSize = 140.sp,
            fontWeight = FontWeight.Light,
            color = NightRed,
            letterSpacing = (-4).sp
        )
    }
}
