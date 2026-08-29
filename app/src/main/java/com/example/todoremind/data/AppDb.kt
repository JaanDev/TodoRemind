package com.example.todoremind.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Todo::class, DailyReminder::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun dailyDao(): DailyDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun get(ctx: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext, AppDb::class.java, "todoremind.db"
                ).build().also { INSTANCE = it }
            }
    }
}
