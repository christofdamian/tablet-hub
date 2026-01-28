package net.damian.tablethub.ui.screens.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.damian.tablethub.data.local.entity.ButtonEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonConfigDialog(
    button: ButtonEntity,
    onSave: (ButtonEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(button.label) }
    var icon by remember { mutableStateOf(button.icon) }
    var identifier by remember {
        mutableStateOf(button.identifier.ifBlank { ButtonEntity.defaultIdentifier(button.position) })
    }
    var iconExpanded by remember { mutableStateOf(false) }

    val commonIcons = listOf(
        "lightbulb", "power", "tv", "speaker", "thermostat",
        "blinds", "fan", "lock", "door", "garage",
        "play_arrow", "pause", "skip_next", "skip_previous", "volume_up"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Button") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = iconExpanded,
                    onExpandedChange = { iconExpanded = it }
                ) {
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text("Icon") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = iconExpanded) },
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = iconExpanded,
                        onDismissRequest = { iconExpanded = false }
                    ) {
                        commonIcons.forEach { iconName ->
                            DropdownMenuItem(
                                text = { Text(iconName) },
                                onClick = {
                                    icon = iconName
                                    iconExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("Identifier") },
                    placeholder = { Text(ButtonEntity.defaultIdentifier(button.position)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (button.isConfigured) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                }
                Button(
                    onClick = {
                        onSave(
                            button.copy(
                                label = label,
                                icon = icon,
                                identifier = identifier
                            )
                        )
                    }
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
