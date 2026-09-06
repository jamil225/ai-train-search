package com.trainsearch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, ConversationStateEntity::class],
    version = 2,
    exportSchema = false // v2: summary → tripState; migration handles existing data
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table without the summary column
                db.execSQL("""
                    CREATE TABLE conversation_state_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        tripState TEXT,
                        lastActiveEpochMs INTEGER NOT NULL,
                        pendingClarificationQuestion TEXT
                    )
                """)
                // Copy data, dropping summary
                db.execSQL("""
                    INSERT INTO conversation_state_new (id, lastActiveEpochMs, pendingClarificationQuestion, tripState)
                    SELECT id, lastActiveEpochMs, pendingClarificationQuestion, NULL FROM conversation_state
                """)
                // Swap tables
                db.execSQL("DROP TABLE conversation_state")
                db.execSQL("ALTER TABLE conversation_state_new RENAME TO conversation_state")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "train_search.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
