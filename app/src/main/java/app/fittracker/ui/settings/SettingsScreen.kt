@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fittracker.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.ui.currentLocale
import app.fittracker.ui.exerciseName
import app.fittracker.ui.figure.MoveFigure
import app.fittracker.ui.figure.Poses
import app.fittracker.ui.setAmount
import app.fittracker.ui.today.rememberSystemStatus
import app.fittracker.ui.totalAmount
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsScreen(onEditExercise: (Long) -> Unit, onAddExercise: () -> Unit) {
    val app = LocalContext.current.applicationContext as FitApp
    val vm: SettingsViewModel = viewModel { SettingsViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_reminders)) }
            item {
                val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.settings.reminderTimes.forEach { time ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeTime(time) },
                            label = { Text(timeFormat.format(time) + "  ✕") },
                        )
                    }
                    AssistChip(onClick = { showTimePicker = true }, label = { Text(stringResource(R.string.settings_add_time)) })
                }
                Text(stringResource(R.string.settings_times_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { SwitchRow(stringResource(R.string.settings_weekends), s.settings.weekends, vm::setWeekends) }
            item { SwitchRow(stringResource(R.string.settings_paused), s.settings.paused, vm::setPaused) }
            item { BatteryRow() }

            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_language)) }
            item { LanguagePicker(vm) }

            item { HorizontalDivider() }
            item { SectionTitle(stringResource(R.string.settings_moves)) }
            items(s.exercises, key = { it.first.id }) { (exercise, goal) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEditExercise(exercise.id) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoveFigure(
                        Poses.forKey(exercise.builtIn?.key),
                        contentDescription = null,
                        animate = false,
                        modifier = Modifier.width(48.dp),
                        color = if (exercise.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exerciseName(exercise), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_move_summary, setAmount(exercise), totalAmount(exercise.unit, goal)) +
                                if (exercise.enabled) "" else " · " + stringResource(R.string.settings_move_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_add_move)) } }
        }
    }

    if (showTimePicker) {
        val picker = rememberTimePickerState(initialHour = 12, initialMinute = 0)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.addTime(LocalTime.of(picker.hour, picker.minute))
                    showTimePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(android.R.string.cancel)) } },
            text = { TimePicker(picker) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun BatteryRow() {
    val context = LocalContext.current
    val status = rememberSystemStatus()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(if (status.batteryUnrestricted) R.string.settings_battery_ok else R.string.settings_battery_restricted),
            style = MaterialTheme.typography.bodyMedium,
        )
        // OnePlus keeps its own per-app battery page here, beyond what the system exemption covers.
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
            )
        }) { Text(stringResource(R.string.settings_open_app_settings)) }
    }
}

@Composable
private fun LanguagePicker(vm: SettingsViewModel) {
    var current by remember { mutableStateOf(vm.language()) }
    val labels = mapOf(
        AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
        AppLanguage.ENGLISH to "English",
        AppLanguage.FINNISH to "Suomi",
    )
    Column {
        AppLanguage.entries.forEach { lang ->
            Row(
                Modifier.fillMaxWidth().selectable(current == lang, role = Role.RadioButton) {
                    current = lang
                    vm.setLanguage(lang)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == lang, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(labels.getValue(lang))
            }
        }
    }
}
