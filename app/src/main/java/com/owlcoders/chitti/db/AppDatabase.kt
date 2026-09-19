package com.owlcoders.chitti.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.owlcoders.chitti.db.dao.*
import com.owlcoders.chitti.db.entities.*

@Database(
    entities = [
        CapturedEvent::class,
        Task::class,
        Deadline::class,
        Reminder::class,
        Commitment::class,
        CalendarEvent::class,
        NotificationEntity::class,
        Memory::class,
        Person::class,
        AutomationHistory::class,
        ChatHistoryEntity::class,
        UserProfile::class,
        PersonalDocument::class
    ],
    // 5: documents table removed with the file finder feature
    // 6: personal_documents (encrypted ID document vault)
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // Legacy DAO (kept for backward compatibility)
    abstract fun eventDao(): CapturedEventDao

    // New DAOs per plan.md §8
    abstract fun taskDao(): TaskDao
    abstract fun memoryDao(): MemoryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun personDao(): PersonDao
    abstract fun automationHistoryDao(): AutomationHistoryDao
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun reminderDao(): ReminderDao
    abstract fun personalDocumentDao(): PersonalDocumentDao

    companion object {
        const val NAME = "chitti_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds the document vault table without touching anyone's existing data. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personal_documents` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`pageCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `fieldsSealed` BLOB)"
                )
            }
        }

        /** Closes the open database, so a restore can replace its file. */
        fun closeForRestore() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                )
                .addMigrations(MIGRATION_5_6)
                // Only for schema jumps with no migration path (development builds).
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
