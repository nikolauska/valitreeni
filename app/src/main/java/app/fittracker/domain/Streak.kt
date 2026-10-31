package app.fittracker.domain

import java.time.LocalDate

enum class DayStatus { ALL_GOALS, SOME, NONE }

data class FocusDay(
    /** Focus moves of the day and whether each reached the goal that applied that day. */
    val goalsMet: List<Boolean>,
    val anythingLogged: Boolean,
)

object Streak {
    fun status(day: FocusDay?): DayStatus = when {
        day == null -> DayStatus.NONE
        day.goalsMet.isNotEmpty() && day.goalsMet.all { it } -> DayStatus.ALL_GOALS
        day.anythingLogged -> DayStatus.SOME
        else -> DayStatus.NONE
    }

    /**
     * Goal days in a row ending today. Today only adds to the streak once its goals are met,
     * but an unfinished today doesn't break it - otherwise the streak would read 0 every morning.
     */
    fun length(days: Map<LocalDate, FocusDay>, today: LocalDate): Int {
        var count = if (status(days[today]) == DayStatus.ALL_GOALS) 1 else 0
        var day = today.minusDays(1)
        while (status(days[day]) == DayStatus.ALL_GOALS) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
