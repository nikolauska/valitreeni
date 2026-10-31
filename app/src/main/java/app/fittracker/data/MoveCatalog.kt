package app.fittracker.data

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import app.fittracker.R
import app.fittracker.domain.BodyArea
import app.fittracker.domain.MoveUnit

/** How-to text shown on the tutorial screen; all of it is translated. */
data class Tutorial(
    @ArrayRes val steps: Int,
    @ArrayRes val avoid: Int,
    @StringRes val easier: Int,
    @StringRes val harder: Int,
)

/** Built-in move. Text lives in resources keyed by [key], so it can be translated. */
data class BuiltInMove(
    val key: String,
    val area: BodyArea,
    val unit: MoveUnit,
    val amountPerSet: Int,
    val dailyGoal: Int,
    @StringRes val nameRes: Int,
    val tutorial: Tutorial,
    /** Amount is counted per side (e.g. lunges: 10 per leg). */
    val perSide: Boolean = false,
)

object MoveCatalog {
    val all: List<BuiltInMove> = listOf(
        BuiltInMove("pushups", BodyArea.PUSH, MoveUnit.REPS, 10, 40, R.string.move_pushups,
            Tutorial(R.array.tut_pushups_steps, R.array.tut_pushups_avoid, R.string.tut_pushups_easier, R.string.tut_pushups_harder)),
        BuiltInMove("squats", BodyArea.LEGS, MoveUnit.REPS, 15, 60, R.string.move_squats,
            Tutorial(R.array.tut_squats_steps, R.array.tut_squats_avoid, R.string.tut_squats_easier, R.string.tut_squats_harder)),
        BuiltInMove("plank", BodyArea.CORE, MoveUnit.SECONDS, 45, 180, R.string.move_plank,
            Tutorial(R.array.tut_plank_steps, R.array.tut_plank_avoid, R.string.tut_plank_easier, R.string.tut_plank_harder)),
        BuiltInMove("glute_bridges", BodyArea.LEGS, MoveUnit.REPS, 15, 45, R.string.move_glute_bridges,
            Tutorial(R.array.tut_glute_bridges_steps, R.array.tut_glute_bridges_avoid, R.string.tut_glute_bridges_easier, R.string.tut_glute_bridges_harder)),
        BuiltInMove("superman", BodyArea.BACK, MoveUnit.SECONDS, 30, 90, R.string.move_superman,
            Tutorial(R.array.tut_superman_steps, R.array.tut_superman_avoid, R.string.tut_superman_easier, R.string.tut_superman_harder)),
        BuiltInMove("reverse_lunges", BodyArea.LEGS, MoveUnit.REPS, 10, 40, R.string.move_reverse_lunges,
            Tutorial(R.array.tut_reverse_lunges_steps, R.array.tut_reverse_lunges_avoid, R.string.tut_reverse_lunges_easier, R.string.tut_reverse_lunges_harder), perSide = true),
        BuiltInMove("chair_dips", BodyArea.PUSH, MoveUnit.REPS, 10, 30, R.string.move_chair_dips,
            Tutorial(R.array.tut_chair_dips_steps, R.array.tut_chair_dips_avoid, R.string.tut_chair_dips_easier, R.string.tut_chair_dips_harder)),
        BuiltInMove("pike_pushups", BodyArea.PUSH, MoveUnit.REPS, 8, 24, R.string.move_pike_pushups,
            Tutorial(R.array.tut_pike_pushups_steps, R.array.tut_pike_pushups_avoid, R.string.tut_pike_pushups_easier, R.string.tut_pike_pushups_harder)),
        BuiltInMove("hollow_hold", BodyArea.CORE, MoveUnit.SECONDS, 20, 60, R.string.move_hollow_hold,
            Tutorial(R.array.tut_hollow_hold_steps, R.array.tut_hollow_hold_avoid, R.string.tut_hollow_hold_easier, R.string.tut_hollow_hold_harder)),
        BuiltInMove("wall_sit", BodyArea.LEGS, MoveUnit.SECONDS, 45, 135, R.string.move_wall_sit,
            Tutorial(R.array.tut_wall_sit_steps, R.array.tut_wall_sit_avoid, R.string.tut_wall_sit_easier, R.string.tut_wall_sit_harder)),
        BuiltInMove("mountain_climbers", BodyArea.CARDIO, MoveUnit.REPS, 20, 60, R.string.move_mountain_climbers,
            Tutorial(R.array.tut_mountain_climbers_steps, R.array.tut_mountain_climbers_avoid, R.string.tut_mountain_climbers_easier, R.string.tut_mountain_climbers_harder)),
        BuiltInMove("burpees", BodyArea.CARDIO, MoveUnit.REPS, 8, 24, R.string.move_burpees,
            Tutorial(R.array.tut_burpees_steps, R.array.tut_burpees_avoid, R.string.tut_burpees_easier, R.string.tut_burpees_harder)),
        BuiltInMove("pullups", BodyArea.PULL, MoveUnit.REPS, 5, 15, R.string.move_pullups,
            Tutorial(R.array.tut_pullups_steps, R.array.tut_pullups_avoid, R.string.tut_pullups_easier, R.string.tut_pullups_harder)),
    )

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String?): BuiltInMove? = key?.let(byKey::get)
}
