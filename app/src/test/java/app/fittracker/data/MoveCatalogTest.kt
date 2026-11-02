package app.fittracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveCatalogTest {
    @Test
    fun `rungs are numbered 0, 1, 2 within each ladder`() {
        MoveCatalog.ladders.forEach { (ladder, moves) ->
            assertEquals(ladder, moves.indices.toList(), moves.map { it.rung })
        }
    }

    @Test
    fun `rungs in a ladder train the same body area`() {
        MoveCatalog.ladders.forEach { (ladder, moves) ->
            assertEquals(ladder, 1, moves.map { it.area }.toSet().size)
        }
    }

    @Test
    fun `usual set size lies inside the adjustment limits`() {
        MoveCatalog.all.forEach {
            assertTrue(it.key, it.minAmount in 1..it.amountPerSet && it.amountPerSet <= it.maxAmount)
        }
    }

    @Test
    fun `older ladders start updating users on the original move, newer ones on their easiest`() {
        MoveCatalog.ladders.filterValues { moves -> moves.any { it.since == 0 } }.keys.forEach {
            assertNotNull(it, MoveCatalog.classicRung(it))
        }
        assertEquals("pushups", MoveCatalog.seedRung("push").key)
        assertEquals("chair_dips", MoveCatalog.seedRung("chair_dips").key)
        assertNull(MoveCatalog.classicRung("hinge"))
        assertEquals("hip_hinges", MoveCatalog.seedRung("hinge").key)
    }

    @Test
    fun `every ladder has a gentle, a middle and a strong version`() {
        // Pike push-ups have no good gentle version without equipment, so that slot stays empty.
        MoveCatalog.ladders.forEach { (ladder, moves) ->
            val expected = if (ladder == "pike_pushups") setOf(1, 2) else setOf(0, 1, 2)
            assertEquals(ladder, expected, moves.map { it.level }.toSet())
        }
    }

    @Test
    fun `newer moves come after older ones so existing sort order never changes`() {
        val versions = MoveCatalog.all.map { it.since }
        assertEquals(versions.sorted(), versions)
        assertEquals(MoveCatalog.VERSION, versions.last())
    }

    @Test
    fun `easier rungs never need a higher start level`() {
        MoveCatalog.ladders.forEach { (ladder, moves) ->
            assertEquals(ladder, moves.map { it.level }.sorted(), moves.map { it.level })
        }
    }

    @Test
    fun `every ladder has a floor-free rung`() {
        val floorOnly = MoveCatalog.ladders.filterValues { moves -> moves.all { it.floor } }.keys
        assertEquals(emptySet<String>(), floorOnly)
    }
}
