package app.fittracker.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderSchedule {
    /** Next reminder strictly after [now], or null when there is nothing to schedule. */
    fun next(now: ZonedDateTime, times: List<LocalTime>, weekends: Boolean): ZonedDateTime? {
        if (times.isEmpty()) return null
        val sorted = times.sorted()
        // Looking 8 days ahead always reaches a weekday, even when weekends are off.
        for (dayOffset in 0L..8L) {
            val date = now.toLocalDate().plusDays(dayOffset)
            if (!weekends && date.dayOfWeek in WEEKEND) continue
            for (time in sorted) {
                // atZone resolves DST gaps by shifting forward, so a 02:30 reminder still fires on change night.
                val candidate = date.atTime(time).atZone(now.zone)
                if (candidate.isAfter(now)) return candidate
            }
        }
        return null
    }

    /**
     * A reminder counts as missed when its time is well past and no alarm fired since then -
     * most likely the phone's battery saving killed it. The grace period covers normal delivery delay.
     */
    fun isMissed(now: Instant, expected: Instant?, lastFired: Instant?, grace: Duration = MISSED_GRACE): Boolean =
        expected != null && now.isAfter(expected.plus(grace)) && !hasFired(expected, lastFired)

    /**
     * The armed reminder is due but not delivered yet, and may still arrive. Re-arming now would
     * replace it with the next time and silently drop it, so the app must leave it alone.
     */
    fun isStillPending(now: Instant, expected: Instant?, lastFired: Instant?, grace: Duration = MISSED_GRACE): Boolean =
        expected != null && !now.isBefore(expected) && !now.isAfter(expected.plus(grace)) && !hasFired(expected, lastFired)

    private fun hasFired(expected: Instant, lastFired: Instant?) = lastFired != null && !lastFired.isBefore(expected)

    /** OxygenOS widens even exact alarms to a 1-hour delivery window, so allow a bit more than that. */
    val MISSED_GRACE: Duration = Duration.ofMinutes(75)

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}
