package app.fittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    private val today = LocalDate.of(2026, 11, 7)
    private val full = FocusDay(listOf(true, true, true, true), anythingLogged = true)
    private val partial = FocusDay(listOf(true, false, true, true), anythingLogged = true)

    @Test
    fun `unfinished today does not break the streak`() {
        val days = mapOf(today.minusDays(1) to full, today.minusDays(2) to full, today to partial)
        assertEquals(2, Streak.length(days, today))
    }

    @Test
    fun `finished today adds to the streak`() {
        val days = mapOf(today to full, today.minusDays(1) to full)
        assertEquals(2, Streak.length(days, today))
    }

    @Test
    fun `a partial day breaks the streak`() {
        val days = mapOf(today.minusDays(1) to full, today.minusDays(2) to partial, today.minusDays(3) to full)
        assertEquals(1, Streak.length(days, today))
    }

    @Test
    fun `status of days`() {
        assertEquals(DayStatus.ALL_GOALS, Streak.status(full))
        assertEquals(DayStatus.SOME, Streak.status(partial))
        assertEquals(DayStatus.NONE, Streak.status(FocusDay(listOf(false), anythingLogged = false)))
        assertEquals(DayStatus.NONE, Streak.status(null))
    }

    @Test
    fun `best streak finds the longest run even when it is over`() {
        val days = mapOf(
            today.minusDays(10) to full, today.minusDays(9) to full, today.minusDays(8) to full,
            today.minusDays(7) to partial,
            today.minusDays(1) to full,
        )
        assertEquals(3, Streak.best(days, today))
    }

    @Test
    fun `a missing day splits runs`() {
        // Days with nothing logged have no entry at all, so a gap in dates must end the run.
        val days = mapOf(today.minusDays(5) to full, today.minusDays(4) to full, today.minusDays(2) to full)
        assertEquals(2, Streak.best(days, today))
    }

    @Test
    fun `best is never below the current streak`() {
        val days = mapOf(today to full, today.minusDays(1) to full)
        assertEquals(2, Streak.best(days, today))
    }
}
