@file:OptIn(ExperimentalMaterial3Api::class)

package app.fittracker.ui.today

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.data.ExerciseProgress
import app.fittracker.ui.currentLocale
import app.fittracker.ui.exerciseName
import app.fittracker.ui.figure.MoveFigure
import app.fittracker.ui.figure.Poses
import app.fittracker.ui.formatDayLabel
import app.fittracker.ui.formatTime
import app.fittracker.ui.setAmount
import app.fittracker.ui.totalAmount

@Composable
fun TodayScreen(
    onStartRound: () -> Unit,
    onOpenTutorial: (exerciseId: Long) -> Unit,
) {
    val app = LocalContext.current.applicationContext as FitApp
    val vm: TodayViewModel = viewModel { TodayViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val locale = currentLocale()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.today_title))
                        state?.let {
                            Text(
                                formatDayLabel(it.day.date, locale),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    state?.takeIf { it.streak > 0 }?.let {
                        Text(
                            pluralStringResource(R.plurals.streak_days, it.streak, it.streak),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Banners(s, onDismissMissed = vm::dismissMissedReminder) }
            item { ReminderLine(s) }
            item { RoundCard(s, onStartRound) }
            item {
                Text(
                    pluralStringResource(R.plurals.focus_header, s.day.focus.size, s.day.focus.size, s.day.focus.size + s.day.others.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(s.day.focus, key = { it.exercise.id }) { p ->
                MoveCard(
                    progress = p,
                    isNew = p.exercise.id in s.newMoves,
                    onAdd = { vm.addSet(p.exercise.id, p.exercise.amountPerSet) },
                    onUndo = { vm.undo(p.exercise.id, s.day.date) },
                    onSwap = { vm.swap(p.exercise.id, s.day.date) },
                    onOpen = { onOpenTutorial(p.exercise.id) },
                )
            }
            if (s.day.others.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.others_header), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(s.day.others, key = { it.exercise.id }) { p ->
                    OtherMoveRow(
                        progress = p,
                        onAdd = { vm.addSet(p.exercise.id, p.exercise.amountPerSet) },
                        onUndo = { vm.undo(p.exercise.id, s.day.date) },
                        onOpen = { onOpenTutorial(p.exercise.id) },
                    )
                }
            }
        }
    }
}

@SuppressLint("BatteryLife") // Personal app, not on Play: asking directly is the clearest way to stop OnePlus killing alarms.
@Composable
private fun Banners(state: TodayUiState, onDismissMissed: () -> Unit) {
    val context = LocalContext.current
    val status = rememberSystemStatus()
    val locale = currentLocale()
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Ask once per app start; Android itself stops showing the dialog after the user declines twice.
    LaunchedEffect(status.notificationsEnabled) {
        if (!status.notificationsEnabled && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!status.notificationsEnabled) {
            Banner(stringResource(R.string.banner_notifications_off), stringResource(R.string.banner_turn_on)) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
        if (!status.batteryUnrestricted) {
            Banner(stringResource(R.string.banner_battery), stringResource(R.string.banner_fix)) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
                )
            }
        }
        state.missedReminderAt?.let { at ->
            Banner(stringResource(R.string.banner_missed_reminder, formatTime(at, locale)), stringResource(R.string.banner_dismiss), onDismissMissed)
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun ReminderLine(state: TodayUiState) {
    val locale = currentLocale()
    val text = when {
        state.paused -> stringResource(R.string.reminders_paused)
        state.nextReminderAt != null -> stringResource(R.string.next_reminder, formatTime(state.nextReminderAt, locale))
        else -> stringResource(R.string.no_reminders)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RoundCard(state: TodayUiState, onStartRound: () -> Unit) {
    val round = state.day.round
    if (state.day.focus.isEmpty()) return
    if (round.isEmpty) {
        Text(stringResource(R.string.all_focus_done), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        return
    }
    Button(onClick = onStartRound, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.start_round) + " · " +
                pluralStringResource(R.plurals.round_moves, round.items.size, round.items.size) + " · " +
                stringResource(R.string.about_minutes, round.estimatedMinutes),
        )
    }
}

@Composable
private fun MoveCard(
    progress: ExerciseProgress,
    isNew: Boolean,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onSwap: () -> Unit,
    onOpen: () -> Unit,
) {
    val e = progress.exercise
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoveFigure(
                    pose = Poses.forKey(e.builtIn?.key),
                    contentDescription = null,
                    modifier = Modifier.width(88.dp).clickable(onClick = onOpen),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(exerciseName(e), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (isNew) {
                            Spacer(Modifier.width(6.dp))
                            Badge { Text(stringResource(R.string.new_move)) }
                        }
                    }
                    Text(
                        stringResource(R.string.progress_of, totalAmount(e.unit, progress.done), totalAmount(e.unit, progress.goal)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (progress.goalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (progress.goal > 0) (progress.done.toFloat() / progress.goal).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onUndo, enabled = progress.done > 0) { Text("−") }
                FilledTonalButton(onClick = onAdd) { Text("+" + setAmount(e)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSwap) { Text(stringResource(R.string.swap)) }
            }
        }
    }
}

@Composable
private fun OtherMoveRow(progress: ExerciseProgress, onAdd: () -> Unit, onUndo: () -> Unit, onOpen: () -> Unit) {
    val e = progress.exercise
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        MoveFigure(
            pose = Poses.forKey(e.builtIn?.key),
            contentDescription = null,
            animate = false,
            modifier = Modifier.width(48.dp).clickable(onClick = onOpen),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
            Text(exerciseName(e), style = MaterialTheme.typography.bodyLarge)
            if (progress.done > 0) {
                Text(totalAmount(e.unit, progress.done), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (progress.done > 0) TextButton(onClick = onUndo) { Text("−") }
        TextButton(onClick = onAdd) { Text("+" + setAmount(e)) }
    }
}
