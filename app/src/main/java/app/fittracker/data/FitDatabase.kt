package app.fittracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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
    entities = [ExerciseEntity::class, GoalChangeEntity::class, LogEntryEntity::class, DailyFocusEntity::class, LadderStateEntity::class],
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * 0.3.0: easier and harder versions of moves. Adds the "how did that feel" answer and the
         * ladder table; the new built-in moves themselves arrive through [SeedCallback.onOpen].
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE log_entry ADD COLUMN effort TEXT")
                db.execSQL(LADDER_STATE_SQL)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ladder_state_exerciseId` ON `ladder_state` (`exerciseId`)")
            }
        }

        // Copied from app/schemas/.../2.json so the migrated table matches what Room validates.
        private const val LADDER_STATE_SQL =
            "CREATE TABLE IF NOT EXISTS `ladder_state` (`ladderKey` TEXT NOT NULL, `exerciseId` INTEGER NOT NULL, " +
                "`autoAdjust` INTEGER NOT NULL, `changedOn` INTEGER NOT NULL, PRIMARY KEY(`ladderKey`), " +
                "FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    }

    /**
     * Seeds the built-in moves when the database file is first created. Doing it in onCreate
     * (same transaction as table creation) means no screen or alarm can ever see an empty catalog.
     */
    internal object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = syncCatalog(db)

        // Also on every open: onCreate only runs on a fresh install, so updated phones would
        // otherwise never get moves added to the catalog later. Every statement is a no-op once done.
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                syncCatalog(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        private fun syncCatalog(db: SupportSQLiteDatabase) {
            MoveCatalog.all.forEach { move ->
                // New moves go after everything else, so existing moves keep their order.
                db.execSQL(
                    "INSERT INTO exercise (builtinKey, customName, unit, bodyArea, amountPerSet, enabled, sortOrder) " +
                        "SELECT ?, NULL, ?, ?, ?, 1, (SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM exercise) " +
                        "WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE builtinKey = ?)",
                    arrayOf<Any>(move.key, move.unit.name, move.area.name, move.amountPerSet, move.key),
                )
                // Effective from day 0 so the starting goal applies to every past and future day until changed.
                db.execSQL(
                    "INSERT INTO goal_change (exerciseId, dailyGoal, effectiveFrom) " +
                        "SELECT e.id, ?, 0 FROM exercise e WHERE e.builtinKey = ? " +
                        "AND NOT EXISTS (SELECT 1 FROM goal_change g WHERE g.exerciseId = e.id)",
                    arrayOf<Any>(move.dailyGoal, move.key),
                )
            }
            // Everyone starts on the original moves (or a newer ladder's easiest one); a new user's
            // first-run answer, or an updater's one-time placement, moves them from there.
            MoveCatalog.ladders.keys.forEach { ladder ->
                db.execSQL(
                    "INSERT OR IGNORE INTO ladder_state (ladderKey, exerciseId, autoAdjust, changedOn) " +
                        "SELECT ?, id, 1, 0 FROM exercise WHERE builtinKey = ?",
                    arrayOf<Any>(ladder, MoveCatalog.seedRung(ladder).key),
                )
            }
        }
    }
}
