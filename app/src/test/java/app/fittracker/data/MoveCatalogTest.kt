package app.fittracker.data

import org.junit.Assert.assertEquals
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
    fun `every ladder contains one of the original moves for updating users`() {
        MoveCatalog.ladders.keys.forEach { MoveCatalog.classicRung(it) }
        assertEquals("pushups", MoveCatalog.classicRung("push").key)
    }

    @Test
    fun `easier rungs never need a higher start level`() {
        MoveCatalog.ladders.forEach { (ladder, moves) ->
            assertEquals(ladder, moves.map { it.level }.sorted(), moves.map { it.level })
        }
    }

    @Test
    fun `every ladder has a floor-free rung except moves that only exist on the floor`() {
        val floorOnly = MoveCatalog.ladders.filterValues { moves -> moves.all { it.floor } }.keys
        assertEquals(setOf("glute_bridges", "pike_pushups", "hollow_hold", "burpees"), floorOnly)
    }
}
