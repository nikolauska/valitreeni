package app.fittracker.domain

import java.time.LocalDate

data class GoalChange(val exerciseId: Long, val dailyGoal: Int, val effectiveFrom: LocalDate)

object Goals {
    /**
     * Goal that applied to [exerciseId] on [date]: the newest change that had already taken effect.
     * Looking goals up by date (instead of storing one per day) keeps old days judged by the goal they had.
     */
    fun goalFor(exerciseId: Long, date: LocalDate, changes: List<GoalChange>): Int? =
        changes
            .filter { it.exerciseId == exerciseId && !it.effectiveFrom.isAfter(date) }
            .maxByOrNull { it.effectiveFrom }
            ?.dailyGoal
}
