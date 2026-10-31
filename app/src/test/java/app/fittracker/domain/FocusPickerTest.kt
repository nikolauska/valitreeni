package app.fittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusPickerTest {
    private val day = LocalDate.of(2026, 11, 1)

    private fun c(id: Long, area: BodyArea, daysAgo: Long?) =
        FocusCandidate(id, area, daysAgo?.let { day.minusDays(it) }, id.toInt())

    @Test
    fun `prefers least recently done and one move per body area`() {
        val picked = FocusPicker.pick(
            listOf(
                c(1, BodyArea.PUSH, 1),
                c(2, BodyArea.PUSH, 5),
                c(3, BodyArea.LEGS, 3),
                c(4, BodyArea.LEGS, 4),
                c(5, BodyArea.CORE, 2),
                c(6, BodyArea.CARDIO, 0),
                c(7, BodyArea.BACK, 6),
            ),
        )
        assertEquals(listOf(7L, 2L, 4L, 5L), picked)
    }

    @Test
    fun `never done moves come first`() {
        val picked = FocusPicker.pick(
            listOf(c(1, BodyArea.PUSH, 1), c(2, BodyArea.LEGS, null), c(3, BodyArea.CORE, 2)),
            count = 2,
        )
        assertEquals(listOf(2L, 3L), picked)
    }

    @Test
    fun `fills remaining slots when there are too few body areas`() {
        val picked = FocusPicker.pick(
            listOf(c(1, BodyArea.PUSH, 3), c(2, BodyArea.PUSH, 2), c(3, BodyArea.PUSH, 1), c(4, BodyArea.LEGS, 4)),
        )
        assertEquals(listOf(4L, 1L, 2L, 3L), picked)
    }

    @Test
    fun `replacement prefers a body area not already in focus`() {
        val all = listOf(
            c(1, BodyArea.PUSH, 1), c(2, BodyArea.LEGS, 1), c(3, BodyArea.CORE, 1),
            c(4, BodyArea.LEGS, 9), c(5, BodyArea.BACK, 2),
        )
        assertEquals(5L, FocusPicker.replacement(all, currentFocus = listOf(1, 2, 3), swappedOut = 3))
        // Swapping out the legs move frees the LEGS area, so the oldest move (legs) is fine again.
        assertEquals(4L, FocusPicker.replacement(all, currentFocus = listOf(1, 2, 3), swappedOut = 2))
    }
}
