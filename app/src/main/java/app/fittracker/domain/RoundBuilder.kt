package app.fittracker.domain

import kotlin.math.ceil

data class FocusProgress(
    val exerciseId: Long,
    val unit: MoveUnit,
    val amountPerSet: Int,
    val goal: Int,
    val doneToday: Int,
)

data class RoundItem(val exerciseId: Long, val unit: MoveUnit, val amount: Int)

data class Round(
    val items: List<RoundItem>,
    /** 1-based number of the round the user is about to do. */
    val roundNumber: Int,
    val totalRounds: Int,
    val estimatedMinutes: Int,
) {
    val isEmpty get() = items.isEmpty()
}

object RoundBuilder {
    // Rough pace for estimating how long a round takes; only used for the "~N min" hint.
    private const val SECONDS_PER_REP = 3
    private const val SECONDS_BETWEEN_MOVES = 15

    /** One set of every focus move that is still short of its goal. */
    fun build(focus: List<FocusProgress>): Round {
        val open = focus.filter { it.doneToday < it.goal }
        val items = open.map { RoundItem(it.exerciseId, it.unit, it.amountPerSet) }
        val setsNeeded = focus.map { setsFor(it.goal, it.amountPerSet) }
        val totalRounds = setsNeeded.maxOrNull() ?: 0
        val roundNumber = open.minOfOrNull { it.doneToday / it.amountPerSet.coerceAtLeast(1) }
            ?.plus(1)
            ?.coerceAtMost(totalRounds)
            ?: totalRounds
        val seconds = items.sumOf {
            val work = if (it.unit == MoveUnit.SECONDS) it.amount else it.amount * SECONDS_PER_REP
            work + SECONDS_BETWEEN_MOVES
        }
        return Round(items, roundNumber, totalRounds, ceil(seconds / 60.0).toInt())
    }

    private fun setsFor(goal: Int, amountPerSet: Int): Int =
        ceil(goal.toDouble() / amountPerSet.coerceAtLeast(1)).toInt()
}
