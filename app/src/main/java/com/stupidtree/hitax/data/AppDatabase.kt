package com.stupidtree.hitax.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stupidtree.stupiduser.data.model.UserProfile
import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.data.model.timetable.TermSubject
import com.stupidtree.hitax.data.model.timetable.Timetable
import com.stupidtree.hitax.data.source.dao.EventItemDao
import com.stupidtree.hitax.data.source.dao.SubjectDao
import com.stupidtree.hitax.data.source.dao.TimetableDao
import com.stupidtree.stupiduser.data.source.dao.UserProfileDao

@Database(
    entities = [EventItem::class, TermSubject::class, Timetable::class],
    version = 2
)
@androidx.room.TypeConverters(TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventItemDao(): EventItemDao
    abstract fun subjectDao(): SubjectDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @JvmStatic
        fun getDatabase(context: Context): AppDatabase {
            if (INSTANCE == null) {
                synchronized(AppDatabase::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java, "hita"
                        ).addMigrations(MIGRATION_1_2).build()
                    }
                }
            }
            return INSTANCE!!
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subject ADD COLUMN selectCategory TEXT")
                db.execSQL("ALTER TABLE subject ADD COLUMN nature TEXT")
            }
        }
    }
}
