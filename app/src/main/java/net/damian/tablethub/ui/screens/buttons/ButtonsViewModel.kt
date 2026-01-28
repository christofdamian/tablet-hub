package net.damian.tablethub.ui.screens.buttons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.damian.tablethub.data.local.entity.ButtonEntity
import net.damian.tablethub.data.repository.ButtonRepository
import net.damian.tablethub.service.mqtt.HaServiceCaller
import javax.inject.Inject

@HiltViewModel
class ButtonsViewModel @Inject constructor(
    private val buttonRepository: ButtonRepository,
    private val haServiceCaller: HaServiceCaller
) : ViewModel() {

    companion object {
        const val GRID_COLUMNS = 3
        const val GRID_ROWS = 2
        const val TOTAL_BUTTONS = GRID_COLUMNS * GRID_ROWS
    }

    // Map of position -> button config
    private val buttonsFlow = buttonRepository.getAllButtons()
        .map { list -> list.associateBy { it.position } }

    val buttons: StateFlow<Map<Int, ButtonEntity>> = buttonsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _editingButton = MutableStateFlow<ButtonEntity?>(null)
    val editingButton: StateFlow<ButtonEntity?> = _editingButton.asStateFlow()

    private val _showEditor = MutableStateFlow(false)
    val showEditor: StateFlow<Boolean> = _showEditor.asStateFlow()

    fun onButtonClick(position: Int) {
        val button = buttons.value[position]
        if (button != null && button.isConfigured) {
            executeButton(button)
        } else {
            // Open editor for unconfigured button
            editButton(position)
        }
    }

    fun onButtonLongPress(position: Int) {
        editButton(position)
    }

    private fun editButton(position: Int) {
        val existingButton = buttons.value[position]
        _editingButton.value = existingButton ?: ButtonEntity(position = position)
        _showEditor.value = true
    }

    fun dismissEditor() {
        _showEditor.value = false
        _editingButton.value = null
    }

    fun saveButton(button: ButtonEntity) {
        viewModelScope.launch {
            buttonRepository.saveButton(button.copy(isConfigured = true))
            dismissEditor()
        }
    }

    fun deleteButton(position: Int) {
        viewModelScope.launch {
            buttonRepository.deleteButton(position)
            dismissEditor()
        }
    }

    private fun executeButton(button: ButtonEntity) {
        viewModelScope.launch {
            haServiceCaller.sendButtonPress(button)
        }
    }
}
