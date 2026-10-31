package app.fittracker.ui.figure

import app.fittracker.data.MoveCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosesTest {
    @Test
    fun `every built-in move has its own drawing`() {
        // Pose construction also checks that a and b have matching points.
        assertEquals(MoveCatalog.all.map { it.key }.toSet(), Poses.keys)
    }

    @Test
    fun `reps loop back to the start and holds pause in the end pose`() {
        val reps = Poses.forKey("pushups")
        assertEquals(0f, blendFor(reps, 0f), 1e-4f)
        assertEquals(1f, blendFor(reps, 0.5f), 1e-4f)
        assertEquals(0f, blendFor(reps, 1f), 1e-4f)
        val hold = Poses.forKey("plank")
        assertEquals(1f, blendFor(hold, 0.5f), 1e-4f)
        assertTrue(blendFor(hold, 0.9f) < 1f)
    }
}
