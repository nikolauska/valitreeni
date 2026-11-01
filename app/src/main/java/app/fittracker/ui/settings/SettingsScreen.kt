@file:OptIn(ExperimentalMaterial3Api::class)

package app.fittracker.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fittracker.BuildConfig
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.ui.FitCard
import app.fittracker.ui.currentLocale
import app.fittracker.ui.exerciseName
import app.fittracker.ui.figure.MoveFigure
import app.fittracker.ui.figure.Poses
import app.fittracker.ui.setAmount
import app.fittracker.ui.theme.brand
import app.fittracker.ui.rememberUpdater
import app.fittracker.ui.today.rememberSystemStatus
import app.fittracker.ui.updateAction
import app.fittracker.ui.updateStatusText
import app.fittracker.update.UpdateState
import app.fittracker.ui.totalAmount
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** Which time the picker is for: a new one, or replacing an existing one. Saved as minutes of day; -1 = new. */
private const val PICK_NEW = -1

@Composable
fun SettingsScreen(onEditExercise: (Long) -> Unit, onAddExercise: () -> Unit) {
    val app = LocalContext.current.applicationContext as FitApp
    val vm: SettingsViewModel = viewModel { SettingsViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale())
    val removedText = stringResource(R.string.settings_time_removed)
    val undoText = stringResource(R.string.settings_undo)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
            }
            item {
                FitCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_reminders), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            BatteryChip()
                        }
                        s.settings.reminderTimes.forEach { time ->
                            TimeRow(
                                label = timeFormat.format(time),
                                onEdit = { picking = time.toSecondOfDay() / 60 },
                                onRemove = {
                                    vm.removeTime(time)
                                    // Undo instead of a confirm dialog: removing is quick to reverse and rarely a mistake.
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val result = snackbar.showSnackbar(removedText.format(timeFormat.format(time)), undoText, duration = SnackbarDuration.Short)
                                        if (result == SnackbarResult.ActionPerformed) vm.addTime(time)
                                    }
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        TextButton(onClick = { picking = PICK_NEW }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.settings_add_time), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(stringResource(R.string.settings_times_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SwitchRow(stringResource(R.string.settings_weekends), s.settings.weekends, vm::setWeekends)
                        SwitchRow(stringResource(R.string.settings_paused), s.settings.paused, vm::setPaused)
                    }
                }
            }
            item {
                FitCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                        LanguagePicker(vm)
                    }
                }
            }
            item { UpdatesCard(s.settings.autoUpdateCheck, vm::setAutoUpdateCheck) }
            item {
                FitCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.settings_moves), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                        s.exercises.forEach { (exercise, goal) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onEditExercise(exercise.id) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MoveFigure(
                                    Poses.forKey(exercise.builtIn?.key),
                                    contentDescription = null,
                                    animate = false,
                                    modifier = Modifier.width(48.dp),
                                    color = if (exercise.enabled) MaterialTheme.brand.figure else MaterialTheme.colorScheme.outline,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(exerciseName(exercise), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(R.string.settings_move_summary, setAmount(exercise), totalAmount(exercise.unit, goal)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!exercise.enabled) {
                                    Chip(stringResource(R.string.settings_move_off), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.settings_add_move)) }
                    }
                }
            }
        }

        picking?.let { minutes ->
            val existing = if (minutes == PICK_NEW) null else LocalTime.ofSecondOfDay(minutes * 60L)
            val initial = existing ?: nextFreeHour(s.settings.reminderTimes)
            val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
            AlertDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        val chosen = LocalTime.of(picker.hour, picker.minute)
                        if (existing == null) vm.addTime(chosen) else vm.replaceTime(existing, chosen)
                        picking = null
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(android.R.string.cancel)) } },
                text = { TimePicker(picker) },
            )
        }
    }
}

@Composable
private fun UpdatesCard(autoCheck: Boolean, onAutoCheck: (Boolean) -> Unit) {
    val updater = rememberUpdater()
    val update by updater.state.collectAsStateWithLifecycle()
    val action = updateAction(update, updater)
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            updateStatusText(update)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (action != null) {
                Button(onClick = action.run, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
            } else {
                val idle = update is UpdateState.Idle || update is UpdateState.UpToDate
                OutlinedButton(onClick = updater::checkNow, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_update_check))
                }
            }
            SwitchRow(stringResource(R.string.settings_update_auto), autoCheck, onAutoCheck)
            Text(stringResource(R.string.settings_update_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Starts the picker on the next whole hour that has no reminder yet, instead of always at noon. */
private fun nextFreeHour(times: List<LocalTime>): LocalTime {
    val taken = times.map { it.hour }.toSet()
    val start = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
    return (0L until 24L).map { start.plusHours(it) }.firstOrNull { it.hour !in taken } ?: start
}

@Composable
private fun TimeRow(label: String, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onEdit).padding(vertical = 10.dp),
        )
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cd_remove_time, label), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Battery status sits with the reminders it affects; tapping opens the app's system page, where OnePlus keeps its own battery switch. */
@Composable
private fun BatteryChip() {
    val context = LocalContext.current
    val status = rememberSystemStatus()
    val brand = MaterialTheme.brand
    val open = {
        context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
    }
    if (status.batteryUnrestricted) {
        Chip(stringResource(R.string.settings_battery_chip_ok), brand.doneContainer, MaterialTheme.colorScheme.onSurface, R.drawable.ic_check, open)
    } else {
        Chip(stringResource(R.string.settings_battery_chip_restricted), brand.warnContainer, brand.onWarn, R.drawable.ic_bolt, open)
    }
}

@Composable
private fun Chip(text: String, container: Color, content: Color, icon: Int? = null, onClick: (() -> Unit)? = null) {
    Surface(
        color = container,
        contentColor = content,
        shape = CircleShape,
        modifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
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
                }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == lang, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(labels.getValue(lang))
            }
        }
    }
}
