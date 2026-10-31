package app.fittracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundBuilderTest {
    private fun p(id: Long, per: Int, goal: Int, done: Int, unit: MoveUnit = MoveUnit.REPS) =
        FocusProgress(id, unit, per, goal, done)

    @Test
    fun `round has one set of every move still short of its goal`() {
        val round = RoundBuilder.build(
            listOf(p(1, 10, 40, 20), p(2, 15, 60, 60), p(3, 45, 180, 90, MoveUnit.SECONDS)),
        )
        assertEquals(listOf(1L, 3L), round.items.map { it.exerciseId })
        assertEquals(listOf(10, 45), round.items.map { it.amount })
        assertEquals(3, round.roundNumber)
        assertEquals(4, round.totalRounds)
    }

    @Test
    fun `four focus moves of three to four sets finish within four rounds`() {
        var focus = listOf(p(1, 10, 40, 0), p(2, 15, 60, 0), p(3, 15, 45, 0), p(4, 30, 90, 0, MoveUnit.SECONDS))
        var rounds = 0
        while (true) {
            val round = RoundBuilder.build(focus)
            if (round.isEmpty) break
            rounds++
            focus = focus.map { f ->
                round.items.find { it.exerciseId == f.exerciseId }?.let { f.copy(doneToday = f.doneToday + it.amount) } ?: f
            }
        }
        assertEquals(4, rounds)
    }

    @Test
    fun `empty when every goal is met`() {
        assertTrue(RoundBuilder.build(listOf(p(1, 10, 40, 40))).isEmpty)
    }

    @Test
    fun `estimate counts reps at three seconds plus a pause per move`() {
        // 10 reps = 30 s + 15 s, plank 45 s + 15 s -> 105 s -> 2 min.
        val round = RoundBuilder.build(listOf(p(1, 10, 40, 0), p(2, 45, 180, 0, MoveUnit.SECONDS)))
        assertEquals(2, round.estimatedMinutes)
    }
}
