package app.fittracker.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val times = listOf("17:00", "08:30", "13:30").map(LocalTime::parse)

    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone)

    @Test
    fun `picks the next time later today`() {
        // 2026-11-02 is a Monday.
        assertEquals(at("2026-11-02T13:30"), ReminderSchedule.next(at("2026-11-02T09:00"), times, weekends = true))
    }

    @Test
    fun `a time equal to now is not repeated`() {
        assertEquals(at("2026-11-02T17:00"), ReminderSchedule.next(at("2026-11-02T13:30"), times, weekends = true))
    }

    @Test
    fun `rolls over to tomorrow after the last time`() {
        assertEquals(at("2026-11-03T08:30"), ReminderSchedule.next(at("2026-11-02T20:00"), times, weekends = true))
    }

    @Test
    fun `skips weekends when they are off`() {
        // Friday evening -> Monday morning.
        assertEquals(at("2026-11-09T08:30"), ReminderSchedule.next(at("2026-11-06T18:00"), times, weekends = false))
    }

    @Test
    fun `nothing to schedule without times`() {
        assertNull(ReminderSchedule.next(at("2026-11-02T09:00"), emptyList(), weekends = true))
    }

    @Test
    fun `reminder inside the spring-forward gap still fires`() {
        // In Helsinki 2027-03-28 03:00 jumps to 04:00.
        val result = ReminderSchedule.next(at("2027-03-28T01:00"), listOf(LocalTime.of(3, 30)), weekends = true)
        assertEquals(ZonedDateTime.of(LocalDateTime.parse("2027-03-28T04:30"), zone), result)
    }

    private val due = Instant.parse("2026-11-02T06:30:00Z")

    @Test
    fun `reminder is missed when its time is long past and nothing fired`() {
        assertTrue(ReminderSchedule.isMissed(due.plusSeconds(31 * 60), due, lastFired = due.minusSeconds(3 * 3600)))
        assertTrue(ReminderSchedule.isMissed(due.plusSeconds(31 * 60), due, lastFired = null))
    }

    @Test
    fun `reminder is not missed within the grace period or after it fired`() {
        assertFalse(ReminderSchedule.isMissed(due.plusSeconds(10 * 60), due, lastFired = null))
        assertFalse(ReminderSchedule.isMissed(due.plusSeconds(3600), due, lastFired = due.plusSeconds(5)))
        assertFalse(ReminderSchedule.isMissed(due.plusSeconds(3600), expected = null, lastFired = null))
    }
}
