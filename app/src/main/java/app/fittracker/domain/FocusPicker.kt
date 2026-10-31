package app.fittracker.domain

import java.time.LocalDate

data class FocusCandidate(
    val exerciseId: Long,
    val area: BodyArea,
    val lastDone: LocalDate?,
    val sortOrder: Int,
)

object FocusPicker {
    const val FOCUS_SIZE = 4

    /**
     * Picks the day's focus moves: least recently done first, at most one per body area.
     * Never-done moves count as the oldest so new moves get introduced quickly.
     * If there are fewer areas than slots, the remaining slots are filled ignoring areas.
     */
    fun pick(candidates: List<FocusCandidate>, count: Int = FOCUS_SIZE): List<Long> {
        val ordered = candidates.sortedWith(
            compareBy<FocusCandidate> { it.lastDone ?: LocalDate.MIN }.thenBy { it.sortOrder },
        )
        val picked = mutableListOf<FocusCandidate>()
        for (c in ordered) {
            if (picked.size == count) break
            if (picked.none { it.area == c.area }) picked += c
        }
        for (c in ordered) {
            if (picked.size == count) break
            if (c !in picked) picked += c
        }
        return picked.map { it.exerciseId }
    }

    /** Replacement for one focus move the user wants to swap out; prefers a body area not already in focus. */
    fun replacement(
        candidates: List<FocusCandidate>,
        currentFocus: List<Long>,
        swappedOut: Long,
    ): Long? {
        val remainingAreas = candidates
            .filter { it.exerciseId in currentFocus && it.exerciseId != swappedOut }
            .map { it.area }
            .toSet()
        val pool = candidates.filter { it.exerciseId !in currentFocus }
            .sortedWith(compareBy<FocusCandidate> { it.lastDone ?: LocalDate.MIN }.thenBy { it.sortOrder })
        return (pool.firstOrNull { it.area !in remainingAreas } ?: pool.firstOrNull())?.exerciseId
    }
}
