package net.damian.tablethub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "buttons")
data class ButtonEntity(
    @PrimaryKey
    val position: Int, // Grid position (0-5 for 3x2 grid)
    val label: String = "",
    val icon: String = "lightbulb", // Material icon name
    val identifier: String = "", // Identifier sent to HA (e.g., "button_1")
    val isConfigured: Boolean = false
) {
    companion object {
        fun defaultIdentifier(position: Int) = "button_${position + 1}"
    }
}
