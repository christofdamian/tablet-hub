package net.damian.tablethub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import net.damian.tablethub.data.local.dao.AlarmDao
import net.damian.tablethub.data.local.entity.AlarmEntity

@Database(
    entities = [AlarmEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
