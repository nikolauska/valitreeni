package app.fittracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fittracker.domain.BodyArea
import app.fittracker.domain.LogSource
import app.fittracker.domain.MoveUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FitRepositoryTest {
    private lateinit var db: FitDatabase
    private var clock: ZonedDateTime = LocalDateTime.parse("2026-11-02T09:00").atZone(ZoneId.of("Europe/Helsinki"))
    private lateinit var repo: FitRepository
    private val day get() = clock.toLocalDate()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitDatabase::class.java)
            .addCallback(FitDatabase.seedCallback)
            .build()
        repo = FitRepository(db.dao()) { clock }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedsAllBuiltInMovesWithGoals() = runTest {
        val view = repo.day(day)
        assertEquals(13, view.others.size)
        val squats = view.others.single { it.exercise.builtIn?.key == "squats" }
        assertEquals(15, squats.exercise.amountPerSet)
        assertEquals(60, squats.goal)
    }

    @Test
    fun focusIsPickedOnceAndBalanced() = runTest {
        repo.ensureFocus(day)
        repo.ensureFocus(day)
        val focus = repo.day(day).focus
        assertEquals(4, focus.size)
        assertEquals(4, focus.map { it.exercise.area }.toSet().size)
    }

    @Test
    fun roundLoggingAndUndo() = runTest {
        repo.ensureFocus(day)
        val round = repo.day(day).round
        assertEquals(4, round.items.size)
        repo.logAll(round.items.map { it.exerciseId to it.amount }, LogSource.NOTIFICATION)
        val first = round.items.first()
        assertEquals(first.amount, repo.day(day).focus.single { it.exercise.id == first.exerciseId }.done)
        repo.undoLast(first.exerciseId, day)
        assertEquals(0, repo.day(day).focus.single { it.exercise.id == first.exerciseId }.done)
    }

    @Test
    fun raisingAGoalKeepsOldDaysUnchanged() = runTest {
        val pushups = repo.day(day).others.single { it.exercise.builtIn?.key == "pushups" }.exercise
        repo.log(pushups.id, 40, LogSource.APP)
        clock = clock.plusDays(1)
        repo.updateExercise(pushups.id, amountPerSet = 10, enabled = true, goal = 50, customName = null)
        assertEquals(40, repo.day(day.minusDays(1)).others.single { it.exercise.id == pushups.id }.goal)
        assertEquals(50, repo.day(day).others.single { it.exercise.id == pushups.id }.goal)
    }

    @Test
    fun historyMarksGoalDays() = runTest {
        repo.ensureFocus(day)
        repo.day(day).focus.forEach { repo.log(it.exercise.id, it.goal, LogSource.APP) }
        val history = repo.observeHistory(day.minusDays(7), day).first()
        assertTrue(history.days.getValue(day).goalsMet.all { it })
        assertFalse(history.days.containsKey(day.minusDays(1)))
    }

    @Test
    fun movesFirstSeenInFocusAreNew() = runTest {
        repo.ensureFocus(day)
        val id = repo.day(day).focus.first().exercise.id
        assertTrue(repo.isNewMove(id, day))
        assertFalse(repo.isNewMove(id, LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun turningOffAFocusMoveReplacesItToday() = runTest {
        repo.ensureFocus(day)
        val off = repo.day(day).focus.first().exercise
        repo.updateExercise(off.id, off.amountPerSet, enabled = false, goal = repo.goal(off.id, day), customName = null)
        val focus = repo.day(day).focus
        assertEquals(4, focus.size)
        assertFalse(focus.any { it.exercise.id == off.id })
    }

    @Test
    fun goalOfATurnedOffMoveIsStillKnown() = runTest {
        val pullups = repo.day(day).others.single { it.exercise.builtIn?.key == "pullups" }.exercise
        repo.updateExercise(pullups.id, pullups.amountPerSet, enabled = false, goal = 15, customName = null)
        assertEquals(15, repo.goal(pullups.id, day))
    }

    @Test
    fun deletingAnOwnMoveRemovesItsLogs() = runTest {
        val id = repo.addCustomExercise("Jumping jacks", MoveUnit.REPS, BodyArea.CARDIO, 20, 60)
        repo.log(id, 20, LogSource.APP)
        repo.deleteExercise(id)
        assertNull(repo.exercise(id))
        assertFalse(repo.day(day).others.any { it.exercise.id == id })
    }

    @Test
    fun builtInMovesCannotBeDeleted() = runTest {
        val pushups = repo.day(day).others.single { it.exercise.builtIn?.key == "pushups" }.exercise
        repo.deleteExercise(pushups.id)
        assertEquals(pushups.id, repo.exercise(pushups.id)?.id)
    }
}
