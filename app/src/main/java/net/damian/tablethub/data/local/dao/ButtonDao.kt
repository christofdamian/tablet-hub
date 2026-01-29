package net.damian.tablethub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.damian.tablethub.data.local.entity.ButtonEntity

@Dao
interface ButtonDao {

    @Query("SELECT * FROM buttons ORDER BY position ASC")
    fun getAllButtons(): Flow<List<ButtonEntity>>

    @Query("SELECT * FROM buttons ORDER BY position ASC")
    suspend fun getAllButtonsSync(): List<ButtonEntity>

    @Query("SELECT * FROM buttons WHERE position = :position")
    suspend fun getButtonAt(position: Int): ButtonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertButton(button: ButtonEntity)

    @Update
    suspend fun updateButton(button: ButtonEntity)

    @Query("DELETE FROM buttons WHERE position = :position")
    suspend fun deleteButton(position: Int)

    @Query("DELETE FROM buttons")
    suspend fun deleteAllButtons()
}
