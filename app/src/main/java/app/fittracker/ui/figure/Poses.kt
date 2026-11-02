package app.fittracker.ui.figure

import androidx.compose.ui.geometry.Offset

/**
 * A move drawn as two key poses on a 100 x 70 grid (floor at y = 62). The figure loops a -> b -> a.
 * Paths use only "M x y" / "L x y" so both poses can be blended point by point; that is why every
 * a/b pair must have exactly the same commands.
 */
class Pose(
    val durationMillis: Int,
    /** Holds ease into pose b, stay there, then release; reps just go back and forth. */
    val hold: Boolean,
    val headA: Offset,
    val headB: Offset,
    a: String,
    b: String,
    /** Small dot next to the head showing which way the face points (superman vs hollow body). */
    val face: Offset? = null,
    /** Static props such as a chair, wall or bar. */
    extra: String? = null,
    val floor: Boolean = true,
) {
    val strokesA: List<List<Offset>> = parse(a)
    val strokesB: List<List<Offset>> = parse(b)
    val extraStrokes: List<List<Offset>> = extra?.let(::parse).orEmpty()

    init {
        require(strokesA.map { it.size } == strokesB.map { it.size }) { "Pose a/b must have matching points" }
    }

    companion object {
        private fun parse(path: String): List<List<Offset>> {
            val strokes = mutableListOf<MutableList<Offset>>()
            val tokens = path.trim().split(Regex("\\s+"))
            var i = 0
            while (i < tokens.size) {
                val cmd = tokens[i]
                val point = Offset(tokens[i + 1].toFloat(), tokens[i + 2].toFloat())
                when (cmd) {
                    "M" -> strokes += mutableListOf(point)
                    "L" -> strokes.last() += point
                    else -> error("Unsupported path command $cmd")
                }
                i += 3
            }
            return strokes
        }
    }
}

object Poses {
    private val byKey: Map<String, Pose> = mapOf(
        "pushups" to Pose(2000, false, Offset(18f, 38f), Offset(18f, 50f),
            "M 24 42 L 60 50 L 86 60 M 26 43 L 27 52 L 28 62",
            "M 24 54 L 60 57 L 86 60 M 26 54 L 36 52 L 28 62"),
        "squats" to Pose(2000, false, Offset(50f, 8f), Offset(50f, 16f),
            "M 50 14 L 50 36 L 52 49 L 52 62 L 60 62 M 50 20 L 52 34",
            "M 49 22 L 42 42 L 60 44 L 56 62 L 64 62 M 47 28 L 72 30"),
        // Forearms point forward (toward the head), like a real forearm plank.
        "plank" to Pose(3500, true, Offset(17f, 55f), Offset(17f, 41f),
            "M 23 58 L 58 60 L 88 61 M 23 58 L 24 61 L 8 61",
            "M 23 46 L 58 51 L 88 60 M 23 46 L 24 61 L 8 61"),
        "glute_bridges" to Pose(2000, false, Offset(12f, 56f), Offset(12f, 56f),
            "M 18 57 L 46 59 L 64 40 L 72 61 M 22 57 L 42 61",
            "M 18 57 L 50 40 L 68 34 L 72 61 M 22 56 L 42 61"),
        // Face down: the face dot sits below the head.
        "superman" to Pose(3500, true, Offset(28f, 56f), Offset(28f, 50f),
            "M 34 59 L 62 60 L 90 60 M 34 59 L 10 59",
            "M 34 54 L 62 59 L 90 51 M 34 54 L 10 50",
            face = Offset(-6f, 6f)),
        "reverse_lunges" to Pose(2400, false, Offset(50f, 10f), Offset(50f, 12f),
            "M 50 16 L 50 38 L 52 50 L 52 62 M 50 38 L 50 50 L 50 62 M 50 22 L 44 34 M 50 22 L 56 34",
            "M 50 18 L 50 40 L 64 46 L 64 62 M 50 40 L 38 59 L 24 58 M 50 24 L 44 36 M 50 24 L 56 36"),
        "chair_dips" to Pose(2000, false, Offset(38f, 18f), Offset(38f, 30f),
            "M 38 24 L 38 46 L 62 48 L 66 62 M 38 26 L 34 33 L 30 40",
            "M 38 36 L 38 56 L 62 52 L 66 62 M 38 38 L 26 32 L 30 40",
            extra = "M 6 40 L 30 40 M 8 40 L 8 62 M 28 40 L 28 62"),
        "pike_pushups" to Pose(2000, false, Offset(31f, 52f), Offset(27f, 57f),
            "M 25 62 L 30 54 L 35 45 L 55 24 L 80 62",
            "M 25 62 L 38 58 L 32 54 L 55 32 L 80 62"),
        // Face up: the face dot sits above the head.
        "hollow_hold" to Pose(3500, true, Offset(28f, 56f), Offset(29f, 45f),
            "M 34 59 L 50 60 L 84 60 M 34 59 L 14 59",
            "M 34 50 L 50 59 L 84 44 M 34 50 L 16 36",
            face = Offset(-3f, -8f)),
        "wall_sit" to Pose(3500, true, Offset(27f, 8f), Offset(27f, 18f),
            "M 26 14 L 26 34 L 27 48 L 27 62 M 26 20 L 30 32",
            "M 26 24 L 26 44 L 50 44 L 50 62 M 26 30 L 38 38",
            extra = "M 20 4 L 20 62"),
        "mountain_climbers" to Pose(900, false, Offset(20f, 35f), Offset(20f, 35f),
            "M 26 40 L 56 46 L 72 53 L 86 60 M 26 40 L 24 62 M 56 46 L 38 52 L 46 61",
            "M 26 40 L 56 46 L 38 52 L 46 61 M 26 40 L 24 62 M 56 46 L 72 53 L 86 60"),
        "burpees" to Pose(1600, false, Offset(50f, 32f), Offset(50f, 10f),
            "M 50 38 L 50 50 M 50 50 L 40 52 L 44 62 M 50 50 L 60 52 L 56 62 M 50 42 L 44 62 M 50 42 L 56 62",
            "M 50 16 L 50 36 M 50 36 L 46 46 L 44 54 M 50 36 L 54 46 L 56 54 M 50 20 L 38 4 M 50 20 L 62 4"),
        "pullups" to Pose(2400, false, Offset(50f, 28f), Offset(50f, 10f),
            "M 50 34 L 50 52 L 48 68 M 50 52 L 52 68 M 50 36 L 42 26 L 40 14 M 50 36 L 58 26 L 60 14",
            "M 50 16 L 50 36 L 48 52 M 50 36 L 52 52 M 50 18 L 36 20 L 40 14 M 50 18 L 64 20 L 60 14",
            extra = "M 20 14 L 80 14", floor = false),
        "wall_pushups" to Pose(2000, false, Offset(25f, 18f), Offset(22f, 20f),
            "M 58 62 L 44 43 L 30 24 M 30 24 L 24 26 L 17 27",
            "M 58 62 L 42 44 L 27 26 M 27 26 L 22 32 L 17 27",
            extra = "M 16 4 L 16 62"),
        "incline_pushups" to Pose(2000, false, Offset(20f, 24f), Offset(15f, 32f),
            "M 58 62 L 42 46 L 26 30 M 26 30 L 23 35 L 20 40",
            "M 58 62 L 40 50 L 22 37 M 22 37 L 28 42 L 20 40",
            extra = "M 2 40 L 24 40 M 22 40 L 22 62 M 4 40 L 4 62"),
        "knee_pushups" to Pose(2000, false, Offset(18f, 38f), Offset(18f, 50f),
            "M 24 42 L 46 51 L 62 61 L 74 52 M 26 43 L 27 52 L 28 62",
            "M 24 54 L 46 58 L 62 61 L 74 52 M 26 54 L 36 52 L 28 62"),
        // The other leg stays planted, so it reads as one leg lifting. Both shin poses have the
        // same length, and the swing is kept under 90° so the straight-line blend doesn't shrink it much.
        "seated_leg_extensions" to Pose(3000, true, Offset(37f, 14f), Offset(37f, 14f),
            "M 36 20 L 35 41 L 50 43 L 59 60 L 66 61 M 35 41 L 50 44 L 51 62 L 58 62 M 36 25 L 33 38",
            "M 36 20 L 35 41 L 50 43 L 69 40 L 70 33 M 35 41 L 50 44 L 51 62 L 58 62 M 36 25 L 33 38",
            extra = "M 24 44 L 44 44 M 26 44 L 26 62 M 42 44 L 42 62 M 24 44 L 24 24"),
        "sit_to_stand" to Pose(2400, false, Offset(39f, 14f), Offset(52f, 10f),
            "M 38 20 L 35 41 L 49 44 L 52 62 L 59 62 M 38 25 L 47 37",
            "M 52 16 L 51 38 L 52 50 L 52 62 L 59 62 M 52 22 L 53 34",
            extra = "M 24 44 L 44 44 M 26 44 L 26 62 M 42 44 L 42 62 M 24 44 L 24 24"),
        "standing_knee_raises" to Pose(1800, false, Offset(50f, 10f), Offset(50f, 10f),
            "M 50 16 L 50 38 L 48 50 L 48 62 M 50 38 L 52 50 L 52 62 M 50 22 L 44 34 M 50 22 L 56 34",
            "M 50 16 L 50 38 L 48 50 L 48 62 M 50 38 L 62 38 L 62 50 M 50 22 L 44 34 M 50 22 L 56 34"),
        // Hips start sagging and lift into a straight line, like the floor plank lifting off the floor.
        "incline_plank" to Pose(3500, true, Offset(19f, 26f), Offset(19f, 26f),
            "M 25 30 L 52 54 L 78 62 M 25 30 L 26 39 L 12 39",
            "M 25 30 L 51 46 L 78 62 M 25 30 L 26 39 L 12 39",
            extra = "M 2 40 L 30 40 M 4 40 L 4 62 M 28 40 L 28 62"),
        // Seen from the front: arms slide from a W up into a Y.
        "wall_angels" to Pose(3000, false, Offset(50f, 10f), Offset(50f, 10f),
            "M 50 16 L 50 38 L 47 62 M 50 38 L 53 62 M 50 22 L 40 26 L 38 16 M 50 22 L 60 26 L 62 16",
            "M 50 16 L 50 38 L 47 62 M 50 38 L 53 62 M 50 22 L 43 13 L 40 3 M 50 22 L 57 13 L 60 3"),
        // Face down, like superman.
        "bird_dog" to Pose(3500, true, Offset(23f, 40f), Offset(23f, 40f),
            "M 30 44 L 56 44 L 56 62 L 70 62 M 30 44 L 30 62 M 30 44 L 31 53 L 32 62 M 56 44 L 57 62 L 71 61",
            "M 30 44 L 56 44 L 56 62 L 70 62 M 30 44 L 30 62 M 30 44 L 20 43 L 10 42 M 56 44 L 70 44 L 84 43",
            face = Offset(-3f, 5f)),
        // Legs and arms swap places each half loop, like mountain climbers.
        "march_in_place" to Pose(1000, false, Offset(50f, 10f), Offset(50f, 10f),
            "M 50 16 L 50 38 M 50 38 L 58 44 L 56 54 M 50 38 L 50 50 L 50 62 M 50 22 L 56 30 L 60 24 M 50 22 L 46 34 L 44 40",
            "M 50 16 L 50 38 M 50 38 L 50 50 L 50 62 M 50 38 L 58 44 L 56 54 M 50 22 L 46 34 L 44 40 M 50 22 L 56 30 L 60 24"),
        "step_jacks" to Pose(1400, false, Offset(50f, 10f), Offset(50f, 10f),
            "M 50 16 L 50 38 M 50 38 L 48 50 L 48 62 M 50 38 L 52 50 L 52 62 M 50 22 L 44 32 L 42 40 M 50 22 L 56 32 L 58 40",
            "M 50 16 L 50 38 M 50 38 L 48 50 L 48 62 M 50 38 L 58 50 L 64 62 M 50 22 L 42 14 L 38 4 M 50 22 L 58 14 L 62 4"),
        "doorframe_rows" to Pose(2400, false, Offset(47f, 22f), Offset(37f, 19f),
            "M 30 62 L 33 53 L 37 45 L 44 28 M 44 28 L 32 29 L 21 30",
            "M 30 62 L 31 53 L 33 44 L 36 26 M 36 26 L 30 33 L 21 30",
            extra = "M 20 4 L 20 62"),
        "jump_squats" to Pose(1600, false, Offset(50f, 16f), Offset(50f, 5f),
            "M 49 22 L 42 42 L 60 44 L 56 62 L 64 62 M 47 28 L 72 30",
            "M 50 11 L 50 31 L 52 43 L 52 54 L 59 56 M 50 15 L 63 7"),
        // Lying under a table, pulling the chest up to its edge; the table's far legs frame it.
        "table_rows" to Pose(2400, false, Offset(39f, 45f), Offset(40f, 36f),
            "M 46 46 L 68 54 L 88 61 M 46 46 L 47 37 L 48 27",
            "M 47 37 L 68 49 L 88 61 M 47 37 L 57 36 L 48 27",
            extra = "M 4 26 L 50 26 M 6 26 L 6 62 M 30 26 L 30 62"),
        "standing_glute_kickbacks" to Pose(1800, false, Offset(44f, 10f), Offset(44f, 10f),
            "M 46 16 L 52 38 L 52 50 L 52 62 M 52 38 L 53 50 L 54 62 M 47 22 L 42 28 L 37 25",
            "M 46 16 L 52 38 L 52 50 L 52 62 M 52 38 L 62 46 L 72 50 M 47 22 L 42 28 L 37 25",
            extra = "M 16 44 L 36 44 M 18 44 L 18 62 M 34 44 L 34 62 M 36 44 L 36 24"),
        // The raised leg stays in line with the thigh, so it rises with the hips.
        "single_leg_glute_bridges" to Pose(2400, false, Offset(12f, 56f), Offset(12f, 56f),
            "M 18 57 L 46 59 L 64 40 L 72 61 M 46 59 L 52 47 L 58 35 M 22 57 L 42 61",
            "M 18 57 L 50 40 L 68 34 L 72 61 M 50 40 L 59 31 L 68 22 M 22 56 L 42 61"),
        "supported_reverse_lunges" to Pose(2400, false, Offset(50f, 10f), Offset(50f, 12f),
            "M 50 16 L 50 38 L 52 50 L 52 62 M 50 38 L 50 50 L 50 62 M 50 22 L 44 34 M 50 22 L 60 28 L 69 26",
            "M 50 18 L 50 40 L 62 46 L 62 62 M 50 40 L 38 59 L 24 58 M 50 24 L 44 36 M 50 24 L 60 30 L 69 26",
            extra = "M 70 24 L 70 62 M 70 44 L 90 44 M 88 44 L 88 62"),
        "bulgarian_split_squats" to Pose(2400, false, Offset(52f, 10f), Offset(52f, 18f),
            "M 52 16 L 52 38 L 57 50 L 57 62 L 64 62 M 52 38 L 44 48 L 33 49 M 52 22 L 58 32",
            "M 52 24 L 52 46 L 63 50 L 60 62 L 67 62 M 52 46 L 44 56 L 33 49 M 52 30 L 58 40",
            extra = "M 14 50 L 34 50 M 16 50 L 16 62 M 32 50 L 32 62"),
        "high_wall_sit" to Pose(3500, true, Offset(27f, 8f), Offset(27f, 12f),
            "M 26 14 L 26 34 L 27 48 L 27 62 M 26 20 L 30 32",
            "M 26 18 L 26 38 L 40 46 L 42 62 M 26 24 L 34 34",
            extra = "M 20 4 L 20 62"),
        "single_leg_wall_sit" to Pose(3500, true, Offset(27f, 8f), Offset(27f, 18f),
            "M 26 14 L 26 34 L 27 48 L 27 62 M 26 34 L 28 48 L 28 62 M 26 20 L 30 32",
            "M 26 24 L 26 44 L 50 44 L 50 62 M 26 44 L 50 45 L 70 44 M 26 30 L 38 38",
            extra = "M 20 4 L 20 62"),
        "seated_dips" to Pose(2000, false, Offset(39f, 14f), Offset(39f, 10f),
            "M 38 20 L 36 41 L 49 44 L 52 62 L 59 62 M 38 25 L 33 34 L 36 44",
            "M 38 16 L 36 37 L 49 41 L 52 62 L 59 62 M 38 21 L 37 32 L 36 44",
            extra = "M 24 44 L 44 44 M 26 44 L 26 62 M 42 44 L 42 62 M 24 44 L 24 24"),
        "bent_knee_chair_dips" to Pose(2000, false, Offset(38f, 18f), Offset(38f, 28f),
            "M 38 24 L 38 44 L 54 42 L 54 62 L 60 62 M 38 26 L 34 33 L 30 40",
            "M 38 34 L 38 52 L 54 46 L 54 62 L 60 62 M 38 36 L 26 31 L 30 40",
            extra = "M 6 40 L 30 40 M 8 40 L 8 62 M 28 40 L 28 62"),
        // A stool without a back, so the lean-back can't be mistaken for resting against it.
        "seated_lean_backs" to Pose(3500, true, Offset(43f, 14f), Offset(32f, 16f),
            "M 44 20 L 46 42 L 60 44 L 62 62 L 69 62 M 44 25 L 51 31 L 45 34",
            "M 35 22 L 46 42 L 60 44 L 62 62 L 69 62 M 37 27 L 45 30 L 40 35",
            extra = "M 30 44 L 50 44 M 32 44 L 32 62 M 48 44 L 48 62"),
        "tuck_hollow_hold" to Pose(3500, true, Offset(28f, 56f), Offset(30f, 45f),
            "M 34 59 L 50 60 L 62 46 L 72 61 M 34 59 L 48 57",
            "M 36 50 L 50 59 L 58 44 L 70 43 M 36 50 L 50 46",
            face = Offset(-3f, -8f)),
        "squat_reaches" to Pose(2000, false, Offset(50f, 16f), Offset(50f, 8f),
            "M 49 22 L 42 42 L 60 44 L 56 62 L 64 62 M 47 28 L 72 30",
            "M 50 14 L 50 36 L 52 49 L 52 62 L 60 62 M 50 20 L 64 9"),
        "incline_burpees" to Pose(2000, false, Offset(36f, 10f), Offset(20f, 26f),
            "M 36 62 L 36 38 L 36 16 M 36 16 L 32 26 L 30 36",
            "M 60 62 L 43 47 L 26 32 M 26 32 L 23 36 L 20 40",
            extra = "M 4 40 L 24 40 M 6 40 L 6 62 M 22 40 L 22 62"),
        "incline_pike_pushups" to Pose(2000, false, Offset(33f, 32f), Offset(25f, 35f),
            "M 22 40 L 27 32 L 32 24 L 58 22 L 80 62",
            "M 22 40 L 34 36 L 30 30 L 56 24 L 80 62",
            extra = "M 4 40 L 24 40 M 6 40 L 6 62 M 22 40 L 22 62"),
        "hip_hinges" to Pose(2400, false, Offset(50f, 10f), Offset(33f, 21f),
            "M 50 16 L 50 38 L 51 50 L 50 62 L 57 62 M 50 22 L 56 30 L 51 37",
            "M 38 25 L 56 38 L 53 50 L 50 62 L 57 62 M 42 28 L 50 25 L 55 37"),
        "supported_single_leg_hinges" to Pose(2600, false, Offset(50f, 10f), Offset(29f, 25f),
            "M 50 16 L 50 38 L 50 50 L 50 62 L 43 62 M 50 38 L 51 50 L 51 61 M 50 22 L 33 23 L 18 24",
            "M 34 29 L 54 38 L 52 50 L 50 62 L 43 62 M 54 38 L 66 36 L 78 34 M 38 31 L 28 29 L 18 24",
            extra = "M 16 24 L 16 62 M 16 44 L 2 44 M 4 44 L 4 62"),
        "single_leg_deadlifts" to Pose(2600, false, Offset(50f, 10f), Offset(26f, 35f),
            "M 50 16 L 50 38 L 50 50 L 50 62 L 43 62 M 50 38 L 51 50 L 51 61 M 50 22 L 49 31 L 48 40",
            "M 32 36 L 54 38 L 52 50 L 50 62 L 43 62 M 54 38 L 66 38 L 78 37 M 36 37 L 36 46 L 36 55"),
        // Seen from the front, like the floor side planks; hips sag toward the wall and lift into a line.
        "wall_side_plank" to Pose(3500, true, Offset(26f, 13f), Offset(26f, 13f),
            "M 51 62 L 36 42 L 29 20 M 29 20 L 23 29 L 23 17",
            "M 51 62 L 40 40 L 29 20 M 29 20 L 23 29 L 23 17",
            extra = "M 20 4 L 20 62"),
        "knee_side_plank" to Pose(3500, true, Offset(19f, 45f), Offset(19f, 45f),
            "M 26 48 L 48 60 L 68 61 M 26 48 L 26 61 L 12 61",
            "M 26 48 L 48 54 L 68 61 M 26 48 L 26 61 L 12 61"),
        "side_plank" to Pose(3500, true, Offset(19f, 43f), Offset(19f, 43f),
            "M 26 46 L 54 60 L 86 61 M 26 46 L 26 61 L 12 61 M 26 46 L 38 52 L 50 58",
            "M 26 46 L 54 53 L 86 61 M 26 46 L 26 61 L 12 61 M 26 46 L 28 34 L 30 22"),
        // Seen from the front: one knee rises across while the opposite elbow comes down to meet it.
        "standing_crossbody_knees" to Pose(1800, false, Offset(50f, 10f), Offset(50f, 12f),
            "M 50 16 L 50 38 M 50 38 L 47 50 L 47 62 M 50 38 L 53 50 L 53 62 M 50 22 L 40 18 L 46 11 M 50 22 L 60 18 L 54 11",
            "M 50 18 L 50 38 M 50 38 L 56 30 L 51 41 M 50 38 L 53 50 L 53 62 M 50 23 L 40 20 L 46 13 M 50 23 L 56 29 L 50 18"),
        "seated_knee_tucks" to Pose(2000, false, Offset(34f, 16f), Offset(35f, 16f),
            "M 37 22 L 46 42 L 58 48 L 70 54 M 39 27 L 35 36 L 38 44",
            "M 38 22 L 46 42 L 54 31 L 63 38 M 40 27 L 36 36 L 38 44",
            extra = "M 30 44 L 50 44 M 32 44 L 32 62 M 48 44 L 48 62"),
        // Legs swap places each half loop, like mountain climbers. Face up, like the hollow hold.
        "bicycle_crunches" to Pose(1200, false, Offset(24f, 50f), Offset(24f, 50f),
            "M 30 52 L 50 60 M 50 60 L 54 46 L 66 48 M 50 60 L 68 56 L 86 54 M 30 52 L 34 44 L 26 46",
            "M 30 52 L 50 60 M 50 60 L 68 56 L 86 54 M 50 60 L 54 46 L 66 48 M 30 52 L 34 44 L 26 46",
            face = Offset(-3f, -8f)),
    )

    /** Standing figure for moves the user added themselves; they have no drawing of their own. */
    val generic = Pose(1000, false, Offset(50f, 10f), Offset(50f, 10f),
        "M 50 16 L 50 38 L 46 62 M 50 38 L 54 62 M 50 22 L 42 34 M 50 22 L 58 34",
        "M 50 16 L 50 38 L 46 62 M 50 38 L 54 62 M 50 22 L 42 34 M 50 22 L 58 34")

    fun forKey(key: String?): Pose = key?.let(byKey::get) ?: generic

    val keys: Set<String> get() = byKey.keys
}
