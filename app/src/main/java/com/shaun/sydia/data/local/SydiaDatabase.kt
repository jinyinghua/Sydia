package com.shaun.sydia.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MemoryEntity::class, ChatHistoryEntity::class], version = 1, exportSchema = false)
abstract class SydiaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SydiaDatabase? = null

        fun getDatabase(context: Context): SydiaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SydiaDatabase::class.java,
                    "sydia_brain.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
