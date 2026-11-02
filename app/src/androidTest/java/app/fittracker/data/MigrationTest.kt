package app.fittracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrading a phone from 0.2.x must keep every logged set, goal and custom move. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(dbName)
    }

    /**
     * Tables exactly as version 1 created them, from the exported schema. Room's MigrationTestHelper
     * would do the same, but it needs a newer kotlinx-serialization than the app ships.
     */
    private fun openVersion1(): SQLiteDatabase {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("app.fittracker.data.FitDatabase/1.json").bufferedReader().use { it.readText() }
        val database = JSONObject(json).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = database.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = 1
        return db
    }

    private fun openCurrent() = Room.databaseBuilder(context, FitDatabase::class.java, dbName)
        .addCallback(FitDatabase.seedCallback)
        .addMigrations(FitDatabase.MIGRATION_1_2)
        .build()

    private val day = LocalDate.of(2026, 10, 30)

    /** Builds a version 1 database the way 0.2.0 seeded it, with some training history on top. */
    private fun createVersion1() {
        openVersion1().use { db ->
            MoveCatalog.all.take(13).forEachIndexed { index, move ->
                db.execSQL(
                    "INSERT INTO exercise (builtinKey, customName, unit, bodyArea, amountPerSet, enabled, sortOrder) VALUES (?, NULL, ?, ?, ?, 1, ?)",
                    arrayOf<Any>(move.key, move.unit.name, move.area.name, move.amountPerSet, index),
                )
                db.execSQL(
                    "INSERT INTO goal_change (exerciseId, dailyGoal, effectiveFrom) SELECT id, ?, 0 FROM exercise WHERE builtinKey = ?",
                    arrayOf<Any>(move.dailyGoal, move.key),
                )
            }
            // A user who changed push-ups, turned burpees off and added their own move.
            db.execSQL("UPDATE exercise SET amountPerSet = 12 WHERE builtinKey = 'pushups'")
            db.execSQL("UPDATE exercise SET enabled = 0 WHERE builtinKey = 'burpees'")
            db.execSQL("INSERT INTO goal_change (exerciseId, dailyGoal, effectiveFrom) SELECT id, 48, ? FROM exercise WHERE builtinKey = 'pushups'", arrayOf<Any>(day.minusDays(5).toEpochDay()))
            db.execSQL("INSERT INTO exercise (builtinKey, customName, unit, bodyArea, amountPerSet, enabled, sortOrder) VALUES (NULL, 'Kettlebell swings', 'REPS', 'LEGS', 20, 1, 13)")
            db.execSQL("INSERT INTO goal_change (exerciseId, dailyGoal, effectiveFrom) SELECT id, 60, 0 FROM exercise WHERE customName = 'Kettlebell swings'")
            listOf("pushups", "squats").forEach { key ->
                db.execSQL(
                    "INSERT INTO log_entry (exerciseId, amount, timestamp, localDate, source) SELECT id, 10, 0, ?, 'APP' FROM exercise WHERE builtinKey = ?",
                    arrayOf<Any>(day.toEpochDay(), key),
                )
                db.execSQL("INSERT INTO daily_focus (localDate, exerciseId) SELECT ?, id FROM exercise WHERE builtinKey = ?", arrayOf<Any>(day.toEpochDay(), key))
            }
        }
    }

    @Test
    fun migratedSchemaMatchesVersion2() {
        createVersion1()
        // Room compares every migrated table with the version 2 schema on open and throws on a mismatch.
        val db = openCurrent()
        try {
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }
    }

    @Test
    fun updateKeepsHistoryAndAddsNewMovesAfterTheOldOnes() = runTest {
        createVersion1()
        val db = openCurrent()
        try {
            val dao = db.dao()
            val exercises = dao.exercises()
            assertEquals(MoveCatalog.all.size + 1, exercises.size)
            val byKey = exercises.associateBy { it.builtinKey }

            // Old rows untouched.
            assertEquals(12, byKey.getValue("pushups").amountPerSet)
            assertEquals(false, byKey.getValue("burpees").enabled)
            assertTrue(exercises.any { it.customName == "Kettlebell swings" })
            val oldMaxSort = exercises.filter { it.builtinKey in MoveCatalog.all.take(13).map { m -> m.key } || it.builtinKey == null }.maxOf { it.sortOrder }
            assertTrue(exercises.filter { it.builtinKey in MoveCatalog.all.drop(13).map { m -> m.key } }.all { it.sortOrder > oldMaxSort })

            val totals = dao.totals(day, day).associate { it.exerciseId to it.total }
            assertEquals(10, totals[byKey.getValue("pushups").id])
            assertEquals(2, dao.focus(day).size)
            dao.efforts(byKey.getValue("pushups").id, day.minusDays(1), day.plusDays(1)).forEach { assertNull(it.effort) }

            // Every new move has a goal, and every ladder starts on the move the user already had.
            val goals = dao.goalChanges()
            MoveCatalog.all.drop(13).forEach { move -> assertTrue(move.key, goals.any { it.exerciseId == byKey.getValue(move.key).id }) }
            val states = dao.ladderStates().associateBy { it.ladderKey }
            assertEquals(MoveCatalog.ladders.keys, states.keys)
            assertEquals(byKey.getValue("pushups").id, states.getValue("push").exerciseId)
            assertEquals(byKey.getValue("squats").id, states.getValue("squat").exerciseId)

            // One row per ladder plus the custom move: burpees are off, but their newer, easier
            // versions arrive switched on, so that ladder is still in use.
            val repo = FitRepository(dao, { day.plusDays(1).atStartOfDay(java.time.ZoneId.of("Europe/Helsinki")) })
            assertEquals(MoveCatalog.ladders.size + 1, repo.day(day.plusDays(1)).others.size)
        } finally {
            db.close()
        }
        // The catalog sync runs on every open; a second open must not add anything twice.
        val again = openCurrent()
        try {
            assertEquals(MoveCatalog.all.size + 1, again.dao().exercises().size)
            assertEquals(MoveCatalog.all.size + 1, again.dao().goalChanges().map { it.exerciseId }.toSet().size)
        } finally {
            again.close()
        }
    }
}
