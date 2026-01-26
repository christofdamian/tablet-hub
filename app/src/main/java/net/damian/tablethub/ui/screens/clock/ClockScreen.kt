package net.damian.tablethub.ui.screens.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.damian.tablethub.ui.components.ClockDisplay
import net.damian.tablethub.ui.components.NextAlarmDisplay

@Composable
fun ClockScreen(
    modifier: Modifier = Modifier,
    nextAlarmText: String? = null // Will be connected to alarm system later
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ClockDisplay()

            NextAlarmDisplay(
                nextAlarmText = nextAlarmText,
                modifier = Modifier.padding(top = 32.dp)
            )
        }
    }
}
