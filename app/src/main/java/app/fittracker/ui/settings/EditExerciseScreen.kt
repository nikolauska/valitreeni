@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fittracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.data.LadderView
import app.fittracker.domain.BodyArea
import app.fittracker.domain.MoveUnit
import app.fittracker.ui.BackButton
import app.fittracker.ui.exerciseName
import kotlinx.coroutines.launch

const val NEW_EXERCISE_ID = -1L

/** Edits a move's set size, daily goal and on/off; for a new id it creates the user's own move. */
@Composable
fun EditExerciseScreen(exerciseId: Long, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as FitApp
    val scope = rememberCoroutineScope()
    val isNew = exerciseId == NEW_EXERCISE_ID
    // Picking another version below switches the screen to that version's row.
    var editingId by rememberSaveable { mutableLongStateOf(exerciseId) }
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var ladder by remember { mutableStateOf<LadderView?>(null) }
    var builtInName by rememberSaveable { mutableStateOf<Int?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(MoveUnit.REPS) }
    var area by rememberSaveable { mutableStateOf(BodyArea.CORE) }
    var amount by rememberSaveable { mutableStateOf("10") }
    var goal by rememberSaveable { mutableStateOf("30") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(editingId) {
        if (isNew) return@LaunchedEffect
        val repo = app.repository
        // Read every time, also after rotation: the ladder can change from the version picker.
        ladder = repo.ladderOf(editingId)
        if (loaded) return@LaunchedEffect
        val e = repo.exercise(editingId) ?: return@LaunchedEffect onDone()
        builtInName = e.builtIn?.nameRes
        name = e.customName.orEmpty()
        unit = e.unit
        area = e.area
        amount = e.amountPerSet.toString()
        // Read from goal history, not today's list: turned-off moves are left out of that list.
        goal = repo.goal(e.id, repo.today()).toString()
        enabled = e.enabled
        loaded = true
    }

    val amountValue = amount.toIntOrNull()?.takeIf { it > 0 }
    val goalValue = goal.toIntOrNull()?.takeIf { it > 0 }
    val nameOk = builtInName != null || name.isNotBlank()
    val canSave = loaded && amountValue != null && goalValue != null && nameOk

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(builtInName?.let { stringResource(it) } ?: stringResource(if (isNew) R.string.edit_new_move else R.string.edit_own_move))
                },
                navigationIcon = { BackButton(onDone) },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (builtInName == null) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.edit_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (isNew) {
                Text(stringResource(R.string.edit_counted_in), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(unit == MoveUnit.REPS, { unit = MoveUnit.REPS }, label = { Text(stringResource(R.string.edit_unit_reps)) })
                    FilterChip(unit == MoveUnit.SECONDS, { unit = MoveUnit.SECONDS }, label = { Text(stringResource(R.string.edit_unit_seconds)) })
                }
                Text(stringResource(R.string.edit_body_area), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyArea.entries.forEach { a ->
                        FilterChip(area == a, { area = a }, label = { Text(stringResource(areaLabel(a))) })
                    }
                }
            }
            ladder?.let { l ->
                VersionPicker(l, editingId) { id ->
                    scope.launch {
                        app.repository.chooseVersion(id)
                        loaded = false
                        editingId = id
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(l.autoAdjust, role = Role.Switch) { on ->
                        scope.launch {
                            app.repository.setAutoAdjust(l.key, on)
                            ladder = app.repository.ladderOf(editingId)
                        }
                    },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.edit_auto_adjust), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.edit_auto_adjust_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(l.autoAdjust, onCheckedChange = null)
                }
            }
            val unitHint = stringResource(if (unit == MoveUnit.SECONDS) R.string.edit_unit_seconds else R.string.edit_unit_reps)
            NumberField(stringResource(R.string.edit_per_set, unitHint), amount) { amount = it }
            NumberField(stringResource(R.string.edit_daily_goal, unitHint), goal) { goal = it }
            if (!isNew) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.toggleable(enabled, role = Role.Switch) { enabled = it }) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.edit_enabled), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.edit_enabled_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(enabled, onCheckedChange = null)
                }
            }
            if (!isNew) {
                Text(stringResource(R.string.edit_goal_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        if (isNew) {
                            app.repository.addCustomExercise(name.trim(), unit, area, amountValue!!, goalValue!!)
                        } else {
                            app.repository.updateExercise(editingId, amountValue!!, enabled, goalValue!!, name.trim())
                        }
                        onDone()
                    }
                },
            ) { Text(stringResource(R.string.edit_save)) }
            if (!isNew && builtInName == null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.edit_delete)) }
            }
        }
    }

    if (confirmDelete) {
        // Confirmed, not undoable: deleting also removes every set logged for the move.
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.edit_delete_title, name.trim())) },
            text = { Text(stringResource(R.string.edit_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.repository.deleteExercise(editingId)
                        onDone()
                    }
                }) { Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** Lists a ladder's versions easiest first; only shown for moves that have more than one. */
@Composable
private fun VersionPicker(ladder: LadderView, selectedId: Long, onPick: (Long) -> Unit) {
    if (ladder.rungs.size < 2) return
    Column {
        Text(stringResource(R.string.edit_version), style = MaterialTheme.typography.labelLarge)
        ladder.rungs.forEach { (exercise, _) ->
            Row(
                Modifier.fillMaxWidth().selectable(exercise.id == selectedId, role = Role.RadioButton) {
                    if (exercise.id != selectedId) onPick(exercise.id)
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = exercise.id == selectedId, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(exerciseName(exercise), style = MaterialTheme.typography.bodyLarge)
                    if (exercise.builtIn?.floor == true) {
                        Text(stringResource(R.string.edit_version_floor), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.toIntOrNull()?.let { it <= 0 } ?: true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun areaLabel(area: BodyArea) = when (area) {
    BodyArea.PUSH -> R.string.area_push
    BodyArea.PULL -> R.string.area_pull
    BodyArea.LEGS -> R.string.area_legs
    BodyArea.CORE -> R.string.area_core
    BodyArea.BACK -> R.string.area_back
    BodyArea.CARDIO -> R.string.area_cardio
}
