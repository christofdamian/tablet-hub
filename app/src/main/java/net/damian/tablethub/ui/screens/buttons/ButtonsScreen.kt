package net.damian.tablethub.ui.screens.buttons

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Blinds
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.damian.tablethub.data.local.entity.ButtonEntity

@Composable
fun ButtonsScreen(
    modifier: Modifier = Modifier,
    viewModel: ButtonsViewModel = hiltViewModel()
) {
    val buttons by viewModel.buttons.collectAsState()
    val editingButton by viewModel.editingButton.collectAsState()
    val showEditor by viewModel.showEditor.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        ButtonGrid(
            buttons = buttons,
            onButtonClick = viewModel::onButtonClick,
            onButtonLongPress = viewModel::onButtonLongPress,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )

        if (showEditor && editingButton != null) {
            ButtonConfigDialog(
                button = editingButton!!,
                onSave = viewModel::saveButton,
                onDelete = { viewModel.deleteButton(editingButton!!.position) },
                onDismiss = viewModel::dismissEditor
            )
        }
    }
}

@Composable
private fun ButtonGrid(
    buttons: Map<Int, ButtonEntity>,
    onButtonClick: (Int) -> Unit,
    onButtonLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(ButtonsViewModel.GRID_COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items((0 until ButtonsViewModel.TOTAL_BUTTONS).toList()) { position ->
            val button = buttons[position]
            ActionButton(
                button = button,
                onClick = { onButtonClick(position) },
                onLongPress = { onButtonLongPress(position) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    button: ButtonEntity?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isConfigured = button?.isConfigured == true

    val containerColor = if (isConfigured) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isConfigured) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isConfigured) {
                    getIconForName(button?.icon ?: "lightbulb")
                } else {
                    Icons.Default.Add
                },
                contentDescription = button?.label,
                modifier = Modifier.size(40.dp),
                tint = contentColor
            )

            if (isConfigured && button?.label?.isNotBlank() == true) {
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (!isConfigured) {
                Text(
                    text = "Long press\nto configure",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun getIconForName(name: String): ImageVector {
    return when (name.lowercase()) {
        "lightbulb" -> Icons.Default.Lightbulb
        "power" -> Icons.Default.Power
        "tv" -> Icons.Default.Tv
        "speaker" -> Icons.Default.Speaker
        "thermostat" -> Icons.Default.Thermostat
        "blinds" -> Icons.Default.Blinds
        "lock" -> Icons.Default.Lock
        "door" -> Icons.Default.DoorFront
        "garage" -> Icons.Default.Garage
        "play_arrow" -> Icons.Default.PlayArrow
        "pause" -> Icons.Default.Pause
        "skip_next" -> Icons.Default.SkipNext
        "skip_previous" -> Icons.Default.SkipPrevious
        "volume_up" -> Icons.Default.VolumeUp
        else -> Icons.Default.Lightbulb
    }
}
