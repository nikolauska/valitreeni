package app.fittracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fittracker.R
import app.fittracker.ui.theme.brand

/** Plain arrow instead of an icon library: the app needs only a handful of symbols. */
@Composable
fun BackButton(onBack: () -> Unit) {
    val label = stringResource(R.string.back)
    TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = label }) { Text("←", style = MaterialTheme.typography.headlineSmall) }
}

/** The brand's card: light paper surface with a hairline, instead of Material's tinted filled card. */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.brand.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val colors = CardDefaults.cardColors(containerColor = color)
    val border = if (color == MaterialTheme.brand.card) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    }
}

/** More sets than this and the blocks get too thin to read, so it falls back to one bar. */
private const val MAX_SEGMENTS = 12

/**
 * Progress as one block per set (40 push-ups at 10 a set = 4 blocks), because people count
 * what's left in sets ("two more"), not in percent.
 */
@Composable
fun SetSegments(done: Int, goal: Int, perSet: Int, modifier: Modifier = Modifier) {
    val sets = if (perSet > 0 && goal > 0) ((goal + perSet - 1) / perSet).coerceAtLeast(1) else 1
    val segments = if (sets > MAX_SEGMENTS) 1 else sets
    val unit = if (segments == 1) goal.coerceAtLeast(1).toFloat() else perSet.toFloat()
    val filled = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.brand.track
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val r = CornerRadius(size.height / 2)
        for (i in 0 until segments) {
            val left = i * (w + gap)
            drawRoundRect(track, Offset(left, 0f), Size(w, size.height), r)
            val fraction = ((done - i * unit) / unit).coerceIn(0f, 1f)
            if (fraction > 0f) drawRoundRect(filled, Offset(left, 0f), Size(w * fraction, size.height), r)
        }
    }
}
