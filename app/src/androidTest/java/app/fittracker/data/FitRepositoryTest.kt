package app.fittracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fittracker.domain.BodyArea
import app.fittracker.domain.Effort
import app.fittracker.domain.StartLevel
import app.fittracker.domain.LogSource
import app.fittracker.domain.MoveUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val avoidFloor = MutableStateFlow(false)
    private val day get() = clock.toLocalDate()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitDatabase::class.java)
            .addCallback(FitDatabase.seedCallback)
            .build()
        repo = FitRepository(db.dao(), { clock }, avoidFloor)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedsAllBuiltInMovesWithGoals() = runTest {
        val view = repo.day(day)
        assertEquals(MoveCatalog.ladders.size, view.others.size)
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

    private suspend fun builtIn(key: String) = repo.observeMoves(day).first().ladders
        .flatMap { it.rungs }.single { it.first.builtIn?.key == key }.first

    @Test
    fun showsOneVersionPerLadder() = runTest {
        assertEquals(MoveCatalog.ladders.size, repo.day(day).others.size)
        assertTrue(repo.day(day).others.none { it.exercise.builtIn?.key == "wall_pushups" })
    }

    @Test
    fun twoTooEasyDaysMakeTheMoveHarderNextMorningAndKeepSetsPerDay() = runTest {
        val pushups = builtIn("pushups")
        repo.log(pushups.id, 10, LogSource.APP, Effort.EASY)
        clock = clock.plusDays(1)
        repo.log(pushups.id, 10, LogSource.APP, Effort.EASY)
        clock = clock.plusDays(1)
        repo.ensureFocus(day)
        assertEquals(12, repo.exercise(pushups.id)?.amountPerSet)
        assertEquals(48, repo.goal(pushups.id, day))
        assertEquals(40, repo.goal(pushups.id, day.minusDays(1)))
        // The same two answers must not push it up again the day after.
        clock = clock.plusDays(1)
        repo.ensureFocus(day)
        assertEquals(12, repo.exercise(pushups.id)?.amountPerSet)
    }

    @Test
    fun aTooHardDayMakesTheMoveEasierNextMorning() = runTest {
        val squats = builtIn("squats")
        repo.log(squats.id, 15, LogSource.APP, Effort.HARD)
        clock = clock.plusDays(1)
        repo.ensureFocus(day)
        assertEquals(13, repo.exercise(squats.id)?.amountPerSet)
    }

    @Test
    fun notificationLogsNeverChangeDifficulty() = runTest {
        val pushups = builtIn("pushups")
        repeat(3) {
            repo.logAll(listOf(pushups.id to 10), LogSource.NOTIFICATION)
            clock = clock.plusDays(1)
            repo.ensureFocus(day)
        }
        assertEquals(10, repo.exercise(pushups.id)?.amountPerSet)
    }

    @Test
    fun easierVersionSwitchesNowAndReplacesTheFocusMove() = runTest {
        repo.ensureFocus(day)
        val push = repo.day(day).focus.map { it.exercise }.firstOrNull { it.builtIn?.ladder == "push" }
            ?: builtIn("pushups")
        val easier = repo.easierVersion(push.id)!!
        assertEquals("knee_pushups", easier.builtIn?.key)
        val switched = repo.switchToEasier(push.id, day)!!
        assertEquals(easier.id, switched.id)
        val focusIds = repo.day(day).focus.map { it.exercise.id }
        assertFalse(push.id in focusIds)
        assertEquals(repo.observeMoves(day).first().ladders.single { it.key == "push" }.currentId, easier.id)
    }

    @Test
    fun avoidingTheFloorKeepsFloorMovesOutOfFocus() = runTest {
        avoidFloor.value = true
        repo.ensureFocus(day)
        val focus = repo.day(day).focus
        assertEquals(4, focus.size)
        assertTrue(focus.none { it.exercise.builtIn?.floor == true })
    }

    private suspend fun currentKeys() = repo.observeMoves(day).first().ladders.associate { it.key to it.current?.first?.builtIn?.key }

    @Test
    fun gentleStartUsesTheEasiestVersions() = runTest {
        repo.applyStartLevel(StartLevel.GENTLE)
        val current = currentKeys()
        assertEquals("wall_pushups", current["push"])
        assertEquals("seated_leg_extensions", current["squat"])
        assertEquals("squat_reaches", current["burpees"])
        // No pike push-up version is gentle enough, so that ladder stays off.
        assertNull(current["pike_pushups"])
        repo.applyStartLevel(StartLevel.STRONG)
        assertEquals("pushups", currentKeys()["push"])
        assertEquals("pike_pushups", currentKeys()["pike_pushups"])
    }

    @Test
    fun pickingStrongLaterTurnsTheHardVersionBackOn() = runTest {
        // Before 0.4.0 a gentler start turned chair dips off, because they had no easier version.
        val dips = builtIn("chair_dips")
        repo.updateExercise(dips.id, dips.amountPerSet, enabled = false, goal = repo.goal(dips.id, day), customName = null)
        repo.applyStartLevel(StartLevel.STRONG)
        assertEquals("chair_dips", currentKeys()["chair_dips"])
    }

    @Test
    fun updatePlacesGrownAndNewLaddersForTheSavedLevel() = runTest {
        // An updated Gentle phone: chair dips are off, and their ladder still points at them.
        val dips = builtIn("chair_dips")
        repo.updateExercise(dips.id, dips.amountPerSet, enabled = false, goal = repo.goal(dips.id, day), customName = null)
        repo.placeNewVersions(StartLevel.GENTLE, fromVersion = 0)
        val current = currentKeys()
        assertEquals("seated_dips", current["chair_dips"])
        assertEquals("hip_hinges", current["hinge"])
        // Ladders the user is training stay where they are.
        assertEquals("pushups", current["push"])
        assertEquals("burpees", current["burpees"])
    }

    @Test
    fun updateKeepsAGentleUsersPikeLadderOff() = runTest {
        repo.applyStartLevel(StartLevel.GENTLE)
        // What 0.3.x left behind: pike push-ups off and the new incline version freshly seeded on.
        val incline = builtIn("incline_pike_pushups")
        repo.updateExercise(incline.id, incline.amountPerSet, enabled = true, goal = repo.goal(incline.id, day), customName = null)
        repo.placeNewVersions(StartLevel.GENTLE, fromVersion = 0)
        assertNull(currentKeys()["pike_pushups"])
    }

    @Test
    fun updatePlacementFollowsTheLevelAndRunsOnlyForNewerMoves() = runTest {
        repo.placeNewVersions(StartLevel.STRONG, fromVersion = 0)
        assertEquals("single_leg_deadlifts", currentKeys()["hinge"])
        repo.chooseVersion(builtIn("hip_hinges").id)
        repo.placeNewVersions(StartLevel.STRONG, fromVersion = MoveCatalog.VERSION)
        assertEquals("hip_hinges", currentKeys()["hinge"])
    }

    @Test
    fun typingASetSizeTurnsAutomaticAdjustmentOff() = runTest {
        val plank = builtIn("plank")
        repo.updateExercise(plank.id, amountPerSet = 60, enabled = true, goal = 180, customName = null)
        assertFalse(repo.ladderOf(plank.id)!!.autoAdjust)
        repo.log(plank.id, 60, LogSource.APP, Effort.HARD)
        clock = clock.plusDays(1)
        repo.ensureFocus(day)
        assertEquals(60, repo.exercise(plank.id)?.amountPerSet)
    }
}
