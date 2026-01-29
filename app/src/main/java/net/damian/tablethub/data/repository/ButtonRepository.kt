package net.damian.tablethub.data.repository

import kotlinx.coroutines.flow.Flow
import net.damian.tablethub.data.local.dao.ButtonDao
import net.damian.tablethub.data.local.entity.ButtonEntity
import net.damian.tablethub.service.mqtt.HaDiscovery
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtonRepository @Inject constructor(
    private val buttonDao: ButtonDao,
    private val haDiscovery: HaDiscovery
) {
    fun getAllButtons(): Flow<List<ButtonEntity>> = buttonDao.getAllButtons()

    suspend fun getButtonAt(position: Int): ButtonEntity? = buttonDao.getButtonAt(position)

    suspend fun saveButton(button: ButtonEntity) {
        // Get the old button to check if identifier changed
        val oldButton = buttonDao.getButtonAt(button.position)
        val oldIdentifier = oldButton?.identifier?.ifBlank {
            ButtonEntity.defaultIdentifier(button.position)
        }

        buttonDao.insertButton(button)

        val newIdentifier = button.identifier.ifBlank {
            ButtonEntity.defaultIdentifier(button.position)
        }

        // Remove old trigger if identifier changed
        if (oldIdentifier != null && oldIdentifier != newIdentifier) {
            haDiscovery.removeShortcutButtonTrigger(oldIdentifier)
        }

        // Publish discovery for the new/updated button
        if (button.isConfigured) {
            haDiscovery.publishShortcutButtonTrigger(newIdentifier, button.label)
        }
    }

    suspend fun deleteButton(position: Int) {
        val button = buttonDao.getButtonAt(position)
        if (button != null) {
            val identifier = button.identifier.ifBlank {
                ButtonEntity.defaultIdentifier(position)
            }
            haDiscovery.removeShortcutButtonTrigger(identifier)
        }
        buttonDao.deleteButton(position)
    }

    suspend fun deleteAllButtons() {
        // Remove all button triggers before deleting
        buttonDao.getAllButtonsSync().forEach { button ->
            val identifier = button.identifier.ifBlank {
                ButtonEntity.defaultIdentifier(button.position)
            }
            haDiscovery.removeShortcutButtonTrigger(identifier)
        }
        buttonDao.deleteAllButtons()
    }

    /**
     * Republish discovery for all configured buttons.
     * Called on MQTT reconnection.
     */
    suspend fun publishAllButtonDiscovery() {
        buttonDao.getAllButtonsSync()
            .filter { it.isConfigured }
            .forEach { button ->
                val identifier = button.identifier.ifBlank {
                    ButtonEntity.defaultIdentifier(button.position)
                }
                haDiscovery.publishShortcutButtonTrigger(identifier, button.label)
            }
    }
}
