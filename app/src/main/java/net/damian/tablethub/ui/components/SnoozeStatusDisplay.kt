package net.damian.tablethub.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SnoozeStatusDisplay(
    snoozeEndTime: LocalDateTime?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (snoozeEndTime != null) {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val formattedTime = snoozeEndTime.format(timeFormatter)

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Snooze,
                contentDescription = "Snoozed",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Snoozed until $formattedTime",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel snooze",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
