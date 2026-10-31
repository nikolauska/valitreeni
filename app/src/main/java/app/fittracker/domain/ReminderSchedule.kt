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
        expected != null && now.isAfter(expected.plus(grace)) && (lastFired == null || lastFired.isBefore(expected))

    val MISSED_GRACE: Duration = Duration.ofMinutes(30)

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}
