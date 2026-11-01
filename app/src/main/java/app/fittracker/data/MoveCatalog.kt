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
    /** Smallest and largest set the automatic adjustment may pick before switching to another rung. */
    val minAmount: Int,
    val maxAmount: Int,
    /** Easier and harder versions of the same move share a ladder; rung 0 is the easiest. */
    val ladder: String = key,
    val rung: Int = 0,
    /** Starts on the floor; left out when the user avoids getting down there. */
    val floor: Boolean = false,
    /** Lowest [app.fittracker.domain.StartLevel] this move suits; 0 gentle, 1 middle, 2 strong. */
    val level: Int,
)

object MoveCatalog {
    val all: List<BuiltInMove> = listOf(
        // The original 13 keep their order, so sortOrder stays the same for new and updated installs.
        BuiltInMove("pushups", BodyArea.PUSH, MoveUnit.REPS, 10, 40, R.string.move_pushups,
            Tutorial(R.array.tut_pushups_steps, R.array.tut_pushups_avoid, R.string.tut_pushups_easier, R.string.tut_pushups_harder),
            minAmount = 5, maxAmount = 20, ladder = "push", rung = 3, floor = true, level = 2),
        BuiltInMove("squats", BodyArea.LEGS, MoveUnit.REPS, 15, 60, R.string.move_squats,
            Tutorial(R.array.tut_squats_steps, R.array.tut_squats_avoid, R.string.tut_squats_easier, R.string.tut_squats_harder),
            minAmount = 8, maxAmount = 25, ladder = "squat", rung = 2, level = 1),
        BuiltInMove("plank", BodyArea.CORE, MoveUnit.SECONDS, 45, 180, R.string.move_plank,
            Tutorial(R.array.tut_plank_steps, R.array.tut_plank_avoid, R.string.tut_plank_easier, R.string.tut_plank_harder),
            minAmount = 20, maxAmount = 90, ladder = "plank", rung = 2, floor = true, level = 2),
        BuiltInMove("glute_bridges", BodyArea.LEGS, MoveUnit.REPS, 15, 45, R.string.move_glute_bridges,
            Tutorial(R.array.tut_glute_bridges_steps, R.array.tut_glute_bridges_avoid, R.string.tut_glute_bridges_easier, R.string.tut_glute_bridges_harder),
            minAmount = 8, maxAmount = 25, floor = true, level = 0),
        BuiltInMove("superman", BodyArea.BACK, MoveUnit.SECONDS, 30, 90, R.string.move_superman,
            Tutorial(R.array.tut_superman_steps, R.array.tut_superman_avoid, R.string.tut_superman_easier, R.string.tut_superman_harder),
            minAmount = 15, maxAmount = 60, ladder = "back", rung = 2, floor = true, level = 2),
        BuiltInMove("reverse_lunges", BodyArea.LEGS, MoveUnit.REPS, 10, 40, R.string.move_reverse_lunges,
            Tutorial(R.array.tut_reverse_lunges_steps, R.array.tut_reverse_lunges_avoid, R.string.tut_reverse_lunges_easier, R.string.tut_reverse_lunges_harder), perSide = true,
            minAmount = 5, maxAmount = 15, level = 1),
        BuiltInMove("chair_dips", BodyArea.PUSH, MoveUnit.REPS, 10, 30, R.string.move_chair_dips,
            Tutorial(R.array.tut_chair_dips_steps, R.array.tut_chair_dips_avoid, R.string.tut_chair_dips_easier, R.string.tut_chair_dips_harder),
            minAmount = 5, maxAmount = 20, level = 2),
        BuiltInMove("pike_pushups", BodyArea.PUSH, MoveUnit.REPS, 8, 24, R.string.move_pike_pushups,
            Tutorial(R.array.tut_pike_pushups_steps, R.array.tut_pike_pushups_avoid, R.string.tut_pike_pushups_easier, R.string.tut_pike_pushups_harder),
            minAmount = 4, maxAmount = 15, floor = true, level = 2),
        BuiltInMove("hollow_hold", BodyArea.CORE, MoveUnit.SECONDS, 20, 60, R.string.move_hollow_hold,
            Tutorial(R.array.tut_hollow_hold_steps, R.array.tut_hollow_hold_avoid, R.string.tut_hollow_hold_easier, R.string.tut_hollow_hold_harder),
            minAmount = 10, maxAmount = 45, floor = true, level = 2),
        BuiltInMove("wall_sit", BodyArea.LEGS, MoveUnit.SECONDS, 45, 135, R.string.move_wall_sit,
            Tutorial(R.array.tut_wall_sit_steps, R.array.tut_wall_sit_avoid, R.string.tut_wall_sit_easier, R.string.tut_wall_sit_harder),
            minAmount = 15, maxAmount = 90, level = 1),
        BuiltInMove("mountain_climbers", BodyArea.CARDIO, MoveUnit.REPS, 20, 60, R.string.move_mountain_climbers,
            Tutorial(R.array.tut_mountain_climbers_steps, R.array.tut_mountain_climbers_avoid, R.string.tut_mountain_climbers_easier, R.string.tut_mountain_climbers_harder),
            minAmount = 10, maxAmount = 40, ladder = "cardio", rung = 2, floor = true, level = 2),
        BuiltInMove("burpees", BodyArea.CARDIO, MoveUnit.REPS, 8, 24, R.string.move_burpees,
            Tutorial(R.array.tut_burpees_steps, R.array.tut_burpees_avoid, R.string.tut_burpees_easier, R.string.tut_burpees_harder),
            minAmount = 4, maxAmount = 15, floor = true, level = 2),
        BuiltInMove("pullups", BodyArea.PULL, MoveUnit.REPS, 5, 15, R.string.move_pullups,
            Tutorial(R.array.tut_pullups_steps, R.array.tut_pullups_avoid, R.string.tut_pullups_easier, R.string.tut_pullups_harder),
            minAmount = 1, maxAmount = 12, ladder = "pull", rung = 1, level = 2),
        // Gentler versions added in 0.3.0. Most work standing or with a chair, wall or table.
        newMove("wall_pushups", BodyArea.PUSH, MoveUnit.REPS, 10, 40, R.string.move_wall_pushups, Tutorial(R.array.tut_wall_pushups_steps, R.array.tut_wall_pushups_avoid, R.string.tut_wall_pushups_easier, R.string.tut_wall_pushups_harder), 5, 20, "push", 0, level = 0),
        newMove("incline_pushups", BodyArea.PUSH, MoveUnit.REPS, 8, 32, R.string.move_incline_pushups, Tutorial(R.array.tut_incline_pushups_steps, R.array.tut_incline_pushups_avoid, R.string.tut_incline_pushups_easier, R.string.tut_incline_pushups_harder), 5, 15, "push", 1, level = 1),
        newMove("knee_pushups", BodyArea.PUSH, MoveUnit.REPS, 8, 32, R.string.move_knee_pushups, Tutorial(R.array.tut_knee_pushups_steps, R.array.tut_knee_pushups_avoid, R.string.tut_knee_pushups_easier, R.string.tut_knee_pushups_harder), 4, 15, "push", 2, floor = true, level = 1),
        newMove("seated_leg_extensions", BodyArea.LEGS, MoveUnit.REPS, 10, 40, R.string.move_seated_leg_extensions, Tutorial(R.array.tut_seated_leg_extensions_steps, R.array.tut_seated_leg_extensions_avoid, R.string.tut_seated_leg_extensions_easier, R.string.tut_seated_leg_extensions_harder), 5, 20, "squat", 0, perSide = true, level = 0),
        newMove("sit_to_stand", BodyArea.LEGS, MoveUnit.REPS, 8, 32, R.string.move_sit_to_stand, Tutorial(R.array.tut_sit_to_stand_steps, R.array.tut_sit_to_stand_avoid, R.string.tut_sit_to_stand_easier, R.string.tut_sit_to_stand_harder), 4, 15, "squat", 1, level = 1),
        newMove("standing_knee_raises", BodyArea.CORE, MoveUnit.REPS, 10, 40, R.string.move_standing_knee_raises, Tutorial(R.array.tut_standing_knee_raises_steps, R.array.tut_standing_knee_raises_avoid, R.string.tut_standing_knee_raises_easier, R.string.tut_standing_knee_raises_harder), 5, 20, "plank", 0, perSide = true, level = 0),
        newMove("incline_plank", BodyArea.CORE, MoveUnit.SECONDS, 20, 60, R.string.move_incline_plank, Tutorial(R.array.tut_incline_plank_steps, R.array.tut_incline_plank_avoid, R.string.tut_incline_plank_easier, R.string.tut_incline_plank_harder), 10, 60, "plank", 1, level = 1),
        newMove("wall_angels", BodyArea.BACK, MoveUnit.REPS, 8, 24, R.string.move_wall_angels, Tutorial(R.array.tut_wall_angels_steps, R.array.tut_wall_angels_avoid, R.string.tut_wall_angels_easier, R.string.tut_wall_angels_harder), 5, 15, "back", 0, level = 0),
        newMove("bird_dog", BodyArea.BACK, MoveUnit.REPS, 6, 18, R.string.move_bird_dog, Tutorial(R.array.tut_bird_dog_steps, R.array.tut_bird_dog_avoid, R.string.tut_bird_dog_easier, R.string.tut_bird_dog_harder), 4, 12, "back", 1, perSide = true, floor = true, level = 1),
        newMove("march_in_place", BodyArea.CARDIO, MoveUnit.SECONDS, 30, 90, R.string.move_march_in_place, Tutorial(R.array.tut_march_in_place_steps, R.array.tut_march_in_place_avoid, R.string.tut_march_in_place_easier, R.string.tut_march_in_place_harder), 20, 90, "cardio", 0, level = 0),
        newMove("step_jacks", BodyArea.CARDIO, MoveUnit.REPS, 16, 48, R.string.move_step_jacks, Tutorial(R.array.tut_step_jacks_steps, R.array.tut_step_jacks_avoid, R.string.tut_step_jacks_easier, R.string.tut_step_jacks_harder), 10, 30, "cardio", 1, level = 1),
        newMove("doorframe_rows", BodyArea.PULL, MoveUnit.REPS, 8, 24, R.string.move_doorframe_rows, Tutorial(R.array.tut_doorframe_rows_steps, R.array.tut_doorframe_rows_avoid, R.string.tut_doorframe_rows_easier, R.string.tut_doorframe_rows_harder), 5, 15, "pull", 0, level = 0),
    )

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String?): BuiltInMove? = key?.let(byKey::get)

    /** Every ladder's moves, easiest first; a move without easier or harder versions is a ladder of one. */
    val ladders: Map<String, List<BuiltInMove>> = all.groupBy { it.ladder }.mapValues { (_, moves) -> moves.sortedBy { it.rung } }

    /**
     * The rung people who used the app before ladders existed were already on: the original move.
     * Starting them there means the update changes nothing until they give feedback.
     */
    fun classicRung(ladder: String): BuiltInMove =
        ladders.getValue(ladder).last { it.key in CLASSIC_KEYS }

    private val CLASSIC_KEYS = all.take(13).map { it.key }.toSet()
}

/** Shorter form for the newer moves, which all name their limits and ladder. */
private fun newMove(
    key: String,
    area: BodyArea,
    unit: MoveUnit,
    amountPerSet: Int,
    dailyGoal: Int,
    @StringRes nameRes: Int,
    tutorial: Tutorial,
    minAmount: Int,
    maxAmount: Int,
    ladder: String,
    rung: Int,
    perSide: Boolean = false,
    floor: Boolean = false,
    level: Int,
) = BuiltInMove(
    key, area, unit, amountPerSet, dailyGoal, nameRes, tutorial, perSide,
    minAmount, maxAmount, ladder, rung, floor, level,
)
