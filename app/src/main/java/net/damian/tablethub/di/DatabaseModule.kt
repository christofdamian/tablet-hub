package net.damian.tablethub.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.damian.tablethub.data.local.AppDatabase
import net.damian.tablethub.data.local.dao.AlarmDao
import net.damian.tablethub.data.local.dao.ButtonDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tablethub.db"
        )
            .fallbackToDestructiveMigration() // TODO: Add proper migrations for production
            .build()
    }

    @Provides
    fun provideAlarmDao(database: AppDatabase): AlarmDao {
        return database.alarmDao()
    }

    @Provides
    fun provideButtonDao(database: AppDatabase): ButtonDao {
        return database.buttonDao()
    }
}
