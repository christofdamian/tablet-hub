package net.damian.tablethub.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.damian.tablethub.data.preferences.SettingsDataStore
import net.damian.tablethub.data.preferences.WeatherConfig
import net.damian.tablethub.service.mqtt.EntityStateTracker
import javax.inject.Inject

data class WeatherState(
    val temperature: String? = null,
    val condition: String? = null,
    val isEnabled: Boolean = true
)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val entityStateTracker: EntityStateTracker,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _weatherState = MutableStateFlow(WeatherState())
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private var currentConfig: WeatherConfig? = null

    init {
        // Collect weather config and track entities
        settingsDataStore.weatherConfig
            .onEach { config ->
                // Untrack old entities if config changed
                currentConfig?.let { oldConfig ->
                    if (oldConfig.temperatureEntity != config.temperatureEntity) {
                        entityStateTracker.untrackEntity(oldConfig.temperatureEntity)
                    }
                    if (oldConfig.conditionEntity != config.conditionEntity) {
                        entityStateTracker.untrackEntity(oldConfig.conditionEntity)
                    }
                }

                currentConfig = config

                if (config.enabled) {
                    entityStateTracker.trackEntity(config.temperatureEntity)
                    entityStateTracker.trackEntity(config.conditionEntity)
                }

                _weatherState.value = _weatherState.value.copy(isEnabled = config.enabled)
            }
            .launchIn(viewModelScope)

        // Combine config and entity states to produce weather state
        combine(
            settingsDataStore.weatherConfig,
            entityStateTracker.entityStates
        ) { config, entityStates ->
            if (!config.enabled) {
                WeatherState(isEnabled = false)
            } else {
                val temperature = entityStates[config.temperatureEntity]?.state
                val condition = entityStates[config.conditionEntity]?.state
                WeatherState(
                    temperature = temperature,
                    condition = condition,
                    isEnabled = true
                )
            }
        }
            .onEach { state -> _weatherState.value = state }
            .launchIn(viewModelScope)
    }
}
