package app.fittracker.domain

import kotlin.math.ceil

/** The user's one-tap answer after a set; null in the database means they didn't answer. */
enum class Effort { HARD, OK, EASY }

/** Where a new user wants to start; [level] matches [Rung.level]. */
enum class StartLevel(val level: Int) { GENTLE(0), MIDDLE(1), STRONG(2) }

/** One version of a move inside a ladder, easiest first. */
data class Rung(
    val exerciseId: Long,
    val unit: MoveUnit,
    val minAmount: Int,
    val startAmount: Int,
    val maxAmount: Int,
    val floor: Boolean,
    val enabled: Boolean,
    val level: Int,
)

/** Which rung is trained and with how big a set. */
data class LadderSpot(val rung: Int, val amount: Int)

enum class Direction { UP, DOWN }

object Ladders {
    /**
     * The rung that is actually trained: the active one unless the user turned it off or it is a
     * floor move while floor moves are avoided. Easier rungs are tried first, so a move someone
     * can't or won't do is never replaced by a harder one when an easier one exists.
     */
    fun effectiveRung(rungs: List<Rung>, active: Int, avoidFloor: Boolean): Int? {
        fun allowed(i: Int) = isAllowed(rungs[i], avoidFloor)
        val start = active.coerceIn(0, rungs.lastIndex)
        if (allowed(start)) return start
        (start - 1 downTo 0).firstOrNull(::allowed)?.let { return it }
        return (start + 1..rungs.lastIndex).firstOrNull(::allowed)
    }

    fun isAllowed(rung: Rung, avoidFloor: Boolean) = rung.enabled && !(avoidFloor && rung.floor)

    /** Hardest rung at or below the chosen level; the easiest rung when every rung is above it. */
    fun startRung(rungs: List<Rung>, level: StartLevel): Int =
        rungs.indexOfLast { it.level <= level.level }.coerceAtLeast(0)
}

object Progression {
    /** How many of the most recent training days are looked at. */
    const val WINDOW_DAYS = 3

    /** "Too easy" days within the window needed before the move gets harder. */
    const val EASY_DAYS_TO_STEP_UP = 2

    /**
     * Decides the next step from the answers given since the move last changed, one list per day it
     * was done, newest first. Going down needs only one "too hard" day and going up needs two
     * "too easy" days: a day that's too easy costs little, a move someone can't do makes them quit.
     */
    fun decide(days: List<List<Effort?>>): Direction? {
        val latest = days.firstOrNull() ?: return null
        if (Effort.HARD in latest) return Direction.DOWN
        val window = days.take(WINDOW_DAYS)
        if (window.any { Effort.HARD in it }) return null
        return if (window.count { Effort.EASY in it } >= EASY_DAYS_TO_STEP_UP) Direction.UP else null
    }

    /** Small steps for small sets, so 5 push-ups don't jump straight to 7. */
    fun step(unit: MoveUnit, amount: Int): Int = when (unit) {
        MoveUnit.REPS -> if (amount < 10) 1 else 2
        MoveUnit.SECONDS -> if (amount < 30) 5 else 10
    }

    /**
     * One step in [direction]: the set size changes inside the rung, and past its limits the next
     * allowed rung starts at its usual set size. Stays put at either end of the ladder.
     */
    fun move(rungs: List<Rung>, spot: LadderSpot, direction: Direction, avoidFloor: Boolean): LadderSpot {
        val rung = rungs[spot.rung]
        val step = step(rung.unit, spot.amount)
        return when (direction) {
            Direction.UP ->
                if (spot.amount < rung.maxAmount) {
                    spot.copy(amount = (spot.amount + step).coerceAtMost(rung.maxAmount))
                } else {
                    nextRung(rungs, spot, spot.rung + 1..rungs.lastIndex, avoidFloor)
                }
            Direction.DOWN ->
                if (spot.amount > rung.minAmount) {
                    spot.copy(amount = (spot.amount - step).coerceAtLeast(rung.minAmount))
                } else {
                    nextRung(rungs, spot, spot.rung - 1 downTo 0, avoidFloor)
                }
        }
    }

    private fun nextRung(rungs: List<Rung>, spot: LadderSpot, order: IntProgression, avoidFloor: Boolean): LadderSpot =
        order.firstOrNull { Ladders.isAllowed(rungs[it], avoidFloor) }
            ?.let { LadderSpot(it, rungs[it].startAmount) }
            ?: spot

    /**
     * Keeps the number of sets per day when the set size changes, so a bigger set means a bigger
     * goal instead of fewer rounds.
     */
    fun goalFor(oldGoal: Int, oldAmount: Int, newAmount: Int): Int {
        val sets = ceil(oldGoal.toDouble() / oldAmount.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        return sets * newAmount
    }
}
