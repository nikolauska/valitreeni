package app.fittracker.data

import androidx.annotation.StringRes
import app.fittracker.R
import app.fittracker.domain.BodyArea
import app.fittracker.domain.MoveUnit

/** Built-in move. Text lives in resources keyed by [key], so it can be translated. */
data class BuiltInMove(
    val key: String,
    val area: BodyArea,
    val unit: MoveUnit,
    val amountPerSet: Int,
    val dailyGoal: Int,
    @StringRes val nameRes: Int,
    /** Amount is counted per side (e.g. lunges: 10 per leg). */
    val perSide: Boolean = false,
)

object MoveCatalog {
    val all: List<BuiltInMove> = listOf(
        BuiltInMove("pushups", BodyArea.PUSH, MoveUnit.REPS, 10, 40, R.string.move_pushups),
        BuiltInMove("squats", BodyArea.LEGS, MoveUnit.REPS, 15, 60, R.string.move_squats),
        BuiltInMove("plank", BodyArea.CORE, MoveUnit.SECONDS, 45, 180, R.string.move_plank),
        BuiltInMove("glute_bridges", BodyArea.LEGS, MoveUnit.REPS, 15, 45, R.string.move_glute_bridges),
        BuiltInMove("superman", BodyArea.BACK, MoveUnit.SECONDS, 30, 90, R.string.move_superman),
        BuiltInMove("reverse_lunges", BodyArea.LEGS, MoveUnit.REPS, 10, 40, R.string.move_reverse_lunges, perSide = true),
        BuiltInMove("chair_dips", BodyArea.PUSH, MoveUnit.REPS, 10, 30, R.string.move_chair_dips),
        BuiltInMove("pike_pushups", BodyArea.PUSH, MoveUnit.REPS, 8, 24, R.string.move_pike_pushups),
        BuiltInMove("hollow_hold", BodyArea.CORE, MoveUnit.SECONDS, 20, 60, R.string.move_hollow_hold),
        BuiltInMove("wall_sit", BodyArea.LEGS, MoveUnit.SECONDS, 45, 135, R.string.move_wall_sit),
        BuiltInMove("mountain_climbers", BodyArea.CARDIO, MoveUnit.REPS, 20, 60, R.string.move_mountain_climbers),
        BuiltInMove("burpees", BodyArea.CARDIO, MoveUnit.REPS, 8, 24, R.string.move_burpees),
        BuiltInMove("pullups", BodyArea.PULL, MoveUnit.REPS, 5, 15, R.string.move_pullups),
    )

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String?): BuiltInMove? = key?.let(byKey::get)
}
