package net.damian.tablethub.data.repository

import kotlinx.coroutines.flow.Flow
import net.damian.tablethub.data.local.dao.ButtonDao
import net.damian.tablethub.data.local.entity.ButtonEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtonRepository @Inject constructor(
    private val buttonDao: ButtonDao
) {
    fun getAllButtons(): Flow<List<ButtonEntity>> = buttonDao.getAllButtons()

    suspend fun getButtonAt(position: Int): ButtonEntity? = buttonDao.getButtonAt(position)

    suspend fun saveButton(button: ButtonEntity) = buttonDao.insertButton(button)

    suspend fun deleteButton(position: Int) = buttonDao.deleteButton(position)

    suspend fun deleteAllButtons() = buttonDao.deleteAllButtons()
}
