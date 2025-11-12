package com.ailive.memory.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ailive.memory.database.converters.Converters
import com.ailive.memory.database.dao.ConversationDao
import com.ailive.memory.database.dao.LongTermFactDao
import com.ailive.memory.database.dao.UserProfileDao
import com.ailive.memory.database.entities.ConversationEntity
import com.ailive.memory.database.entities.ConversationTurnEntity
import com.ailive.memory.database.entities.LongTermFactEntity
import com.ailive.memory.database.entities.UserProfileEntity

/**
 * AILive Memory Database
 *
 * Central database for all persistent memory:
 * - Conversations (working memory)
 * - Conversation Turns (individual messages)
 * - Long-term Facts (important knowledge)
 * - User Profile (personal information)
 *
 * Version 1: Initial schema
 */
@Database(
    entities = [
        ConversationEntity::class,
        ConversationTurnEntity::class,
        LongTermFactEntity::class,
        UserProfileEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MemoryDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun longTermFactDao(): LongTermFactDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        private const val DATABASE_NAME = "ailive_memory_db"

        @Volatile
        private var INSTANCE: MemoryDatabase? = null

        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()  // For development
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * For testing purposes
         */
        fun getInMemoryDatabase(context: Context): MemoryDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                MemoryDatabase::class.java
            ).build()
        }
    }
}
