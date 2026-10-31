package app.fittracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun epochDayToDate(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)
    @TypeConverter fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()
    @TypeConverter fun millisToInstant(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)
}

@Database(
    entities = [ExerciseEntity::class, GoalChangeEntity::class, LogEntryEntity::class, DailyFocusEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FitDatabase : RoomDatabase() {
    abstract fun dao(): FitDao

    companion object {
        // Exposed so tests can seed an in-memory database the same way.
        val seedCallback: Callback get() = SeedCallback

        fun create(context: Context): FitDatabase =
            Room.databaseBuilder(context, FitDatabase::class.java, "fit.db")
                .addCallback(seedCallback)
                .build()
    }

    /**
     * Seeds the built-in moves when the database file is first created. Doing it in onCreate
     * (same transaction as table creation) means no screen or alarm can ever see an empty catalog.
     */
    internal object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            MoveCatalog.all.forEachIndexed { index, move ->
                db.execSQL(
                    "INSERT INTO exercise (builtinKey, customName, unit, bodyArea, amountPerSet, enabled, sortOrder) " +
                        "VALUES (?, NULL, ?, ?, ?, 1, ?)",
                    arrayOf<Any>(move.key, move.unit.name, move.area.name, move.amountPerSet, index),
                )
                // Effective from day 0 so the starting goal applies to every past and future day until changed.
                db.execSQL(
                    "INSERT INTO goal_change (exerciseId, dailyGoal, effectiveFrom) " +
                        "SELECT id, ?, 0 FROM exercise WHERE builtinKey = ?",
                    arrayOf<Any>(move.dailyGoal, move.key),
                )
            }
        }
    }
}
