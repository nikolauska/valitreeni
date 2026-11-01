package app.fittracker.domain

import app.fittracker.domain.Effort.EASY
import app.fittracker.domain.Effort.HARD
import app.fittracker.domain.Effort.OK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {
    private fun rung(id: Long, min: Int, start: Int, max: Int, floor: Boolean = false, enabled: Boolean = true, level: Int = 0) =
        Rung(id, MoveUnit.REPS, min, start, max, floor, enabled, level)

    // Wall push-up -> incline -> knee (floor) -> push-up (floor)
    private val push = listOf(
        rung(1, 5, 10, 20, level = 0),
        rung(2, 5, 8, 15, level = 1),
        rung(3, 4, 8, 15, floor = true, level = 1),
        rung(4, 5, 10, 20, floor = true, level = 2),
    )

    @Test
    fun `one too hard day steps down`() {
        assertEquals(Direction.DOWN, Progression.decide(listOf(listOf(OK, HARD))))
    }

    @Test
    fun `two too easy days within three step up`() {
        assertEquals(Direction.UP, Progression.decide(listOf(listOf(EASY), listOf(null), listOf(EASY, OK))))
    }

    @Test
    fun `one too easy day is not enough`() {
        assertNull(Progression.decide(listOf(listOf(EASY), listOf(OK), listOf(OK))))
    }

    @Test
    fun `older easy days outside the window don't count`() {
        assertNull(Progression.decide(listOf(listOf(EASY), listOf(OK), listOf(OK), listOf(EASY))))
    }

    @Test
    fun `a recent hard day blocks stepping up`() {
        assertNull(Progression.decide(listOf(listOf(EASY), listOf(EASY), listOf(HARD))))
    }

    @Test
    fun `no answers means no change`() {
        assertNull(Progression.decide(emptyList()))
        assertNull(Progression.decide(listOf(listOf(null, null), listOf(null))))
    }

    @Test
    fun `step sizes grow with the set`() {
        assertEquals(1, Progression.step(MoveUnit.REPS, 9))
        assertEquals(2, Progression.step(MoveUnit.REPS, 10))
        assertEquals(5, Progression.step(MoveUnit.SECONDS, 25))
        assertEquals(10, Progression.step(MoveUnit.SECONDS, 30))
    }

    @Test
    fun `up grows the set and stops at the rung's max`() {
        assertEquals(LadderSpot(0, 12), Progression.move(push, LadderSpot(0, 10), Direction.UP, avoidFloor = false))
        assertEquals(LadderSpot(0, 20), Progression.move(push, LadderSpot(0, 19), Direction.UP, avoidFloor = false))
    }

    @Test
    fun `up past the max starts the next rung at its usual set`() {
        assertEquals(LadderSpot(1, 8), Progression.move(push, LadderSpot(0, 20), Direction.UP, avoidFloor = false))
    }

    @Test
    fun `down below the min starts the rung below at its usual set`() {
        assertEquals(LadderSpot(2, 8), Progression.move(push, LadderSpot(3, 5), Direction.DOWN, avoidFloor = false))
    }

    @Test
    fun `avoiding the floor caps the ladder at the last floor-free rung`() {
        assertEquals(LadderSpot(1, 15), Progression.move(push, LadderSpot(1, 15), Direction.UP, avoidFloor = true))
    }

    @Test
    fun `turned-off rungs are skipped`() {
        val ladder = push.mapIndexed { i, r -> if (i == 1) r.copy(enabled = false) else r }
        assertEquals(LadderSpot(2, 8), Progression.move(ladder, LadderSpot(0, 20), Direction.UP, avoidFloor = false))
    }

    @Test
    fun `bottom of the ladder stays put`() {
        assertEquals(LadderSpot(0, 5), Progression.move(push, LadderSpot(0, 5), Direction.DOWN, avoidFloor = false))
    }

    @Test
    fun `goal keeps the number of sets per day`() {
        assertEquals(48, Progression.goalFor(oldGoal = 40, oldAmount = 10, newAmount = 12))
        assertEquals(24, Progression.goalFor(oldGoal = 45, oldAmount = 15, newAmount = 8))
    }

    @Test
    fun `effective rung falls back to an easier allowed rung first`() {
        assertEquals(1, Ladders.effectiveRung(push, 3, avoidFloor = true))
        val firstOff = push.mapIndexed { i, r -> if (i == 0) r.copy(enabled = false) else r }
        assertEquals(1, Ladders.effectiveRung(firstOff, 0, avoidFloor = false))
        assertNull(Ladders.effectiveRung(listOf(rung(9, 1, 1, 1, floor = true)), 0, avoidFloor = true))
    }

    @Test
    fun `start rung is the hardest one at or below the level`() {
        assertEquals(0, Ladders.startRung(push, StartLevel.GENTLE))
        assertEquals(2, Ladders.startRung(push, StartLevel.MIDDLE))
        assertEquals(3, Ladders.startRung(push, StartLevel.STRONG))
        assertEquals(0, Ladders.startRung(listOf(rung(9, 1, 1, 1, level = 2)), StartLevel.GENTLE))
    }
}
