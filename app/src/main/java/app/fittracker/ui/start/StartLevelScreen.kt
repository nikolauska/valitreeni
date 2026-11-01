package app.fittracker.ui.start

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.fittracker.R
import app.fittracker.domain.StartLevel
import app.fittracker.ui.figure.MoveFigure
import app.fittracker.ui.figure.Poses
import app.fittracker.ui.theme.brand

private data class LevelOption(val level: StartLevel, @StringRes val title: Int, @StringRes val text: Int, val poseKey: String)

private val options = listOf(
    LevelOption(StartLevel.GENTLE, R.string.start_level_gentle, R.string.start_level_gentle_text, "wall_pushups"),
    LevelOption(StartLevel.MIDDLE, R.string.start_level_middle, R.string.start_level_middle_text, "knee_pushups"),
    LevelOption(StartLevel.STRONG, R.string.start_level_strong, R.string.start_level_strong_text, "pushups"),
)

@StringRes
fun startLevelLabel(level: StartLevel): Int = options.first { it.level == level }.title

/** Asked once on a fresh install, so a fit person doesn't spend a week climbing up from wall push-ups. */
@Composable
fun StartLevelScreen(onChosen: (StartLevel) -> Unit) {
    // Nothing preselected: the answer should be a real choice, not a default someone taps past.
    var selected by rememberSaveable { mutableStateOf<StartLevel?>(null) }
    val brand = MaterialTheme.brand
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.start_level_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.start_level_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            options.forEach { option ->
                LevelCard(option, selected == option.level) { selected = option.level }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { selected?.let(onChosen) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brand.accent, contentColor = brand.onAccent),
            ) { Text(stringResource(R.string.start_level_start), style = MaterialTheme.typography.titleMedium) }
            Text(
                stringResource(R.string.start_level_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelCard(option: LevelOption, selected: Boolean, onSelect: () -> Unit) {
    val brand = MaterialTheme.brand
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = brand.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MoveFigure(
                Poses.forKey(option.poseKey),
                contentDescription = null,
                animate = selected,
                modifier = Modifier.width(88.dp).background(brand.track, RoundedCornerShape(12.dp)).padding(6.dp),
                color = brand.figure,
                accent = brand.accent,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(option.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(option.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
