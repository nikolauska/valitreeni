package app.fittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalsTest {
    private val changes = listOf(
        GoalChange(1, 60, LocalDate.of(2026, 1, 1)),
        GoalChange(1, 80, LocalDate.of(2026, 11, 10)),
        GoalChange(2, 40, LocalDate.of(2026, 1, 1)),
    )

    @Test
    fun `old days keep the goal they had`() {
        assertEquals(60, Goals.goalFor(1, LocalDate.of(2026, 11, 9), changes))
        assertEquals(80, Goals.goalFor(1, LocalDate.of(2026, 11, 10), changes))
        assertEquals(80, Goals.goalFor(1, LocalDate.of(2027, 1, 1), changes))
    }

    @Test
    fun `no goal before the first change`() {
        assertNull(Goals.goalFor(1, LocalDate.of(2025, 12, 31), changes))
    }
}
