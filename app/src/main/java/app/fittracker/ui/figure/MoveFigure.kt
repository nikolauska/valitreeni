package app.fittracker.ui.figure

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min

private const val VIEW_W = 100f
private const val VIEW_H = 70f

/**
 * Stick figure for a move. Loops between the two key poses, or shows the end pose when
 * [animate] is false or the user turned off animations in Android settings.
 */
@Composable
fun MoveFigure(
    pose: Pose,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    color: Color = LocalContentColor.current,
) {
    val context = LocalContext.current
    val animationsOn = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val progress = if (animate && animationsOn) {
        val transition = rememberInfiniteTransition(label = "move")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(pose.durationMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "pose",
        ).value
    } else {
        null
    }
    Canvas(
        modifier = modifier
            .aspectRatio(VIEW_W / VIEW_H)
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        val blend = progress?.let { blendFor(pose, it) } ?: 1f
        drawPose(pose, blend, color)
    }
}

/** 0 = pose a, 1 = pose b. Reps go a -> b -> a; holds ease in, stay, then release. */
internal fun blendFor(pose: Pose, t: Float): Float =
    if (pose.hold) {
        when {
            t < 0.3f -> smooth(t / 0.3f)
            t < 0.75f -> 1f
            else -> smooth(1f - (t - 0.75f) / 0.25f)
        }
    } else {
        smooth(if (t < 0.5f) t * 2f else (1f - t) * 2f)
    }

private fun smooth(x: Float) = x * x * (3f - 2f * x)

private fun lerp(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

private fun DrawScope.drawPose(pose: Pose, blend: Float, color: Color) {
    val scale = min(size.width / VIEW_W, size.height / VIEW_H)
    val dx = (size.width - VIEW_W * scale) / 2f
    val dy = (size.height - VIEW_H * scale) / 2f
    fun map(p: Offset) = Offset(dx + p.x * scale, dy + p.y * scale)
    val body = Stroke(width = 3f * scale, cap = StrokeCap.Round)

    fun strokeLines(points: List<Offset>, c: Color, width: Float) {
        for (i in 0 until points.size - 1) {
            drawLine(c, map(points[i]), map(points[i + 1]), strokeWidth = width, cap = StrokeCap.Round)
        }
    }

    if (pose.floor) {
        drawLine(color.copy(alpha = 0.4f), map(Offset(2f, 62f)), map(Offset(98f, 62f)), strokeWidth = 1.5f * scale)
    }
    pose.extraStrokes.forEach { strokeLines(it, color.copy(alpha = 0.55f), 3f * scale) }
    pose.strokesA.zip(pose.strokesB).forEach { (a, b) ->
        strokeLines(a.zip(b) { pa, pb -> lerp(pa, pb, blend) }, color, body.width)
    }
    val head = lerp(pose.headA, pose.headB, blend)
    drawCircle(color, radius = 5f * scale, center = map(head), style = body)
    pose.face?.let { drawCircle(color, radius = 2.2f * scale, center = map(head + it)) }
}
