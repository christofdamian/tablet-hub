package net.damian.tablethub.ui.screens.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var actionType by remember { mutableStateOf(button.actionType) }
    var domain by remember { mutableStateOf(button.domain) }
    var service by remember { mutableStateOf(button.service) }
    var entityId by remember { mutableStateOf(button.entityId) }
    var serviceData by remember { mutableStateOf(button.serviceData) }

    var actionTypeExpanded by remember { mutableStateOf(false) }
    var domainExpanded by remember { mutableStateOf(false) }
    var serviceExpanded by remember { mutableStateOf(false) }
    var iconExpanded by remember { mutableStateOf(false) }

    val commonDomains = listOf("light", "switch", "scene", "script", "cover", "fan", "climate", "media_player")
    val commonServices = mapOf(
        "light" to listOf("toggle", "turn_on", "turn_off"),
        "switch" to listOf("toggle", "turn_on", "turn_off"),
        "cover" to listOf("open_cover", "close_cover", "toggle"),
        "fan" to listOf("toggle", "turn_on", "turn_off"),
        "climate" to listOf("set_temperature", "turn_on", "turn_off"),
        "media_player" to listOf("toggle", "media_play_pause", "volume_up", "volume_down")
    )
    val commonIcons = listOf(
        "lightbulb", "power", "tv", "speaker", "thermostat",
        "blinds", "fan", "lock", "door", "garage",
        "play_arrow", "pause", "skip_next", "skip_previous", "volume_up"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Button") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Icon picker
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

                // Action type picker
                ExposedDropdownMenuBox(
                    expanded = actionTypeExpanded,
                    onExpandedChange = { actionTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = actionType.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        onValueChange = { },
                        label = { Text("Action Type") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionTypeExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = actionTypeExpanded,
                        onDismissRequest = { actionTypeExpanded = false }
                    ) {
                        ButtonEntity.ActionType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(type.name.replace("_", " ").lowercase()
                                        .replaceFirstChar { it.uppercase() })
                                },
                                onClick = {
                                    actionType = type
                                    actionTypeExpanded = false
                                    // Set default domain for certain action types
                                    when (type) {
                                        ButtonEntity.ActionType.SCENE -> domain = "scene"
                                        ButtonEntity.ActionType.SCRIPT -> domain = "script"
                                        else -> {}
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Domain and Service only for SERVICE_CALL and TOGGLE_ENTITY
                if (actionType == ButtonEntity.ActionType.SERVICE_CALL ||
                    actionType == ButtonEntity.ActionType.TOGGLE_ENTITY) {

                    // Domain picker
                    ExposedDropdownMenuBox(
                        expanded = domainExpanded,
                        onExpandedChange = { domainExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("Domain") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = domainExpanded) },
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = domainExpanded,
                            onDismissRequest = { domainExpanded = false }
                        ) {
                            commonDomains.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d) },
                                    onClick = {
                                        domain = d
                                        domainExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Service picker (only for SERVICE_CALL)
                    if (actionType == ButtonEntity.ActionType.SERVICE_CALL) {
                        val servicesForDomain = commonServices[domain] ?: emptyList()

                        ExposedDropdownMenuBox(
                            expanded = serviceExpanded,
                            onExpandedChange = { serviceExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = service,
                                onValueChange = { service = it },
                                label = { Text("Service") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serviceExpanded) },
                                singleLine = true
                            )
                            if (servicesForDomain.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = serviceExpanded,
                                    onDismissRequest = { serviceExpanded = false }
                                ) {
                                    servicesForDomain.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s) },
                                            onClick = {
                                                service = s
                                                serviceExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Entity ID (always shown)
                OutlinedTextField(
                    value = entityId,
                    onValueChange = { entityId = it },
                    label = { Text("Entity ID") },
                    placeholder = { Text("e.g., light.living_room") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Service data (advanced)
                OutlinedTextField(
                    value = serviceData,
                    onValueChange = { serviceData = it },
                    label = { Text("Service Data (JSON, optional)") },
                    placeholder = { Text("""{"brightness": 255}""") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                actionType = actionType,
                                domain = domain,
                                service = service,
                                entityId = entityId,
                                serviceData = serviceData
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
