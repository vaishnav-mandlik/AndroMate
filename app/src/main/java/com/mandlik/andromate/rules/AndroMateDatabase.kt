package com.mandlik.andromate.rules

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * AndroMateDatabase — Local database for storing rules
 *
 * Like SQLite under the hood, but Room gives you a clean
 * Kotlin API on top. Think of it as:
 *   AsyncStorage (React Native) → stores key-value pairs
 *   Room (Android) → stores structured data in tables
 *
 * Singleton pattern: only one instance exists in the whole app.
 * Same as creating a context provider in React Native.
 */
@Database(entities = [Rule::class], version = 1, exportSchema = false)
abstract class AndroMateDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var INSTANCE: AndroMateDatabase? = null

        /**
         * Get the database instance. Creates it if it doesn't exist.
         *
         * Usage:
         *   val db = AndroMateDatabase.getInstance(context)
         *   val rules = db.ruleDao().getAllRules()
         */
        fun getInstance(context: Context): AndroMateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AndroMateDatabase::class.java,
                    "andromate_database"  // Database file name
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}