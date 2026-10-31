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
    )

    /** Standing figure for moves the user added themselves; they have no drawing of their own. */
    val generic = Pose(1000, false, Offset(50f, 10f), Offset(50f, 10f),
        "M 50 16 L 50 38 L 46 62 M 50 38 L 54 62 M 50 22 L 42 34 M 50 22 L 58 34",
        "M 50 16 L 50 38 L 46 62 M 50 38 L 54 62 M 50 22 L 42 34 M 50 22 L 58 34")

    fun forKey(key: String?): Pose = key?.let(byKey::get) ?: generic

    val keys: Set<String> get() = byKey.keys
}
