package net.damian.tablethub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import net.damian.tablethub.data.local.dao.AlarmDao
import net.damian.tablethub.data.local.dao.ButtonDao
import net.damian.tablethub.data.local.entity.AlarmEntity
import net.damian.tablethub.data.local.entity.ButtonEntity

@Database(
    entities = [AlarmEntity::class, ButtonEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun buttonDao(): ButtonDao
}
