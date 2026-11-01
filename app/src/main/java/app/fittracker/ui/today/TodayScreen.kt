package app.fittracker.ui.today

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.data.ExerciseProgress
import app.fittracker.ui.FitCard
import app.fittracker.ui.SetSegments
import app.fittracker.ui.currentLocale
import app.fittracker.ui.exerciseName
import app.fittracker.ui.figure.MoveFigure
import app.fittracker.ui.figure.Poses
import app.fittracker.ui.formatDayLabel
import app.fittracker.ui.formatTime
import app.fittracker.ui.setAmount
import app.fittracker.ui.theme.brand
import app.fittracker.ui.totalAmount

/** No move picked yet: the first unfinished one opens by default. */
private const val AUTO = Long.MIN_VALUE

/** The user closed every card. */
private const val COLLAPSED = Long.MIN_VALUE + 1

@Composable
fun TodayScreen(
    onStartRound: () -> Unit,
    onOpenTutorial: (exerciseId: Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as FitApp
    val vm: TodayViewModel = viewModel { TodayViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Only one unfinished move shows its buttons at a time, so all four focus moves fit without scrolling.
    var pickedOpen by rememberSaveable { mutableLongStateOf(AUTO) }
    // Finished moves open separately, so adding a bonus set doesn't close the move you're working on.
    var pickedDone by rememberSaveable { mutableLongStateOf(COLLAPSED) }

    Scaffold { padding ->
        val s = state ?: return@Scaffold
        val open = s.day.focus.filter { !it.goalMet }
        val finished = s.day.focus.filter { it.goalMet }
        // A picked move that just reached its goal hands over to the next unfinished one.
        val expanded = when {
            pickedOpen == COLLAPSED -> null
            open.any { it.exercise.id == pickedOpen } -> pickedOpen
            else -> open.firstOrNull()?.exercise?.id
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Header(s) }
            if (s.day.focus.isEmpty()) {
                item { EmptyFocus(onOpenSettings) }
            } else {
                item { HeroCard(s, onStartRound) }
            }
            item { Nudges(s, onDismissMissed = vm::dismissMissedReminder) }
            if (s.day.focus.isNotEmpty()) item { FocusHeader(s) }
            items(open, key = { it.exercise.id }) { p ->
                MoveCard(
                    progress = p,
                    isNew = p.exercise.id in s.newMoves,
                    expanded = expanded == p.exercise.id,
                    onToggle = { pickedOpen = if (expanded == p.exercise.id) COLLAPSED else p.exercise.id },
                    onAdd = { vm.addSet(p.exercise.id, p.exercise.amountPerSet) },
                    onUndo = { vm.undo(p.exercise.id, s.day.date) },
                    onSwap = { vm.swap(p.exercise.id, s.day.date) },
                    onOpen = { onOpenTutorial(p.exercise.id) },
                )
            }
            items(finished, key = { it.exercise.id }) { p ->
                if (pickedDone == p.exercise.id) {
                    MoveCard(
                        progress = p,
                        isNew = false,
                        expanded = true,
                        onToggle = { pickedDone = COLLAPSED },
                        onAdd = { vm.addSet(p.exercise.id, p.exercise.amountPerSet) },
                        onUndo = { vm.undo(p.exercise.id, s.day.date) },
                        onSwap = null,
                        onOpen = { onOpenTutorial(p.exercise.id) },
                    )
                } else {
                    DoneRow(p, onClick = { pickedDone = p.exercise.id })
                }
            }
            if (s.day.others.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.others_header), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
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

@Composable
private fun Header(state: TodayUiState) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(
            formatDayLabel(state.day.date, currentLocale()).uppercase(currentLocale()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
private fun HeroCard(state: TodayUiState, onStartRound: () -> Unit) {
    val brand = MaterialTheme.brand
    val focus = state.day.focus
    val round = state.day.round
    val goalsMet = focus.count { it.goalMet }
    Surface(color = brand.hero, contentColor = brand.onHero, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ringLabel = stringResource(R.string.cd_goals_met, goalsMet, focus.size)
                Box(Modifier.size(96.dp).clearAndSetSemantics { contentDescription = ringLabel }, contentAlignment = Alignment.Center) {
                    GoalRing(focus)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$goalsMet/${focus.size}", style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.today_goals_label), style = MaterialTheme.typography.labelSmall, color = brand.onHero.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (round.isEmpty) R.string.hero_all_done else R.string.hero_next_up).uppercase(currentLocale()),
                        style = MaterialTheme.typography.labelMedium,
                        color = brand.onHero.copy(alpha = 0.7f),
                    )
                    Text(
                        if (round.isEmpty) stringResource(R.string.hero_well_done) else stringResource(R.string.hero_round, round.roundNumber),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (state.streak > 0) StreakPill(state.streak, Modifier.padding(top = 8.dp))
                }
            }
            if (!round.isEmpty) {
                Button(
                    onClick = onStartRound,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brand.accent, contentColor = brand.onAccent),
                ) {
                    Text(stringResource(R.string.start_round), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pluralStringResource(R.plurals.round_moves, round.items.size, round.items.size) + " · " +
                            stringResource(R.string.about_minutes, round.estimatedMinutes),
                        style = MaterialTheme.typography.labelLarge,
                        color = brand.onAccent.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** One arc per focus move, filled by that move's progress, so the ring reads as "which moves are left". */
@Composable
private fun GoalRing(focus: List<ExerciseProgress>) {
    val brand = MaterialTheme.brand
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 9.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val n = focus.size.coerceAtLeast(1)
        val gap = if (n > 1) 14f else 0f
        val sweep = 360f / n - gap
        focus.forEachIndexed { i, p ->
            val start = -90f + i * (360f / n) + gap / 2
            val style = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(brand.onHero.copy(alpha = 0.18f), start, sweep, false, Offset(inset, inset), arcSize, style = style)
            val fraction = if (p.goal > 0) (p.done.toFloat() / p.goal).coerceIn(0f, 1f) else 0f
            if (fraction > 0f) drawArc(brand.accent, start, sweep * fraction, false, Offset(inset, inset), arcSize, style = style)
        }
    }
}

@Composable
fun StreakPill(streak: Int, modifier: Modifier = Modifier) {
    val brand = MaterialTheme.brand
    Row(
        modifier.background(brand.onHero.copy(alpha = 0.12f), CircleShape).padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_streak), contentDescription = null, tint = brand.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(pluralStringResource(R.plurals.streak_days, streak, streak), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyFocus(onOpenSettings: () -> Unit) {
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.empty_focus_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.empty_focus_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.empty_focus_button)) }
        }
    }
}

@SuppressLint("BatteryLife") // Personal app, not on Play: asking directly is the clearest way to stop OnePlus killing alarms.
@Composable
private fun Nudges(state: TodayUiState, onDismissMissed: () -> Unit) {
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
        // A missed reminder is the one real problem, so only it gets error red.
        state.missedReminderAt?.let { at ->
            Nudge(
                stringResource(R.string.banner_missed_reminder, formatTime(at, locale)),
                stringResource(R.string.banner_dismiss),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                onAction = onDismissMissed,
            )
        }
        // Setup steps are calm amber hints, not errors: nothing has gone wrong yet.
        if (!status.notificationsEnabled) {
            Nudge(stringResource(R.string.banner_notifications_off), stringResource(R.string.banner_turn_on)) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
        if (!status.batteryUnrestricted) {
            Nudge(stringResource(R.string.banner_battery), stringResource(R.string.banner_fix)) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
                )
            }
        }
    }
}

@Composable
private fun Nudge(
    text: String,
    action: String,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.brand.warnContainer,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.brand.onWarn,
    onAction: () -> Unit,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action, color = content, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun FocusHeader(state: TodayUiState) {
    val locale = currentLocale()
    val reminder = when {
        state.paused -> stringResource(R.string.reminders_paused)
        state.nextReminderAt != null -> stringResource(R.string.next_reminder, formatTime(state.nextReminderAt, locale))
        else -> stringResource(R.string.no_reminders)
    }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(reminder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoveCard(
    progress: ExerciseProgress,
    isNew: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onSwap: (() -> Unit)?,
    onOpen: () -> Unit,
) {
    val e = progress.exercise
    val name = exerciseName(e)
    val openLabel = stringResource(R.string.cd_open_tutorial, name)
    FitCard(Modifier.fillMaxWidth().animateContentSize(), onClick = onToggle) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoveFigure(
                    pose = Poses.forKey(e.builtIn?.key),
                    contentDescription = null,
                    animate = false,
                    modifier = Modifier
                        .width(72.dp)
                        .background(MaterialTheme.brand.track, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                        .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                        .semantics { contentDescription = openLabel },
                    color = MaterialTheme.brand.figure,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (isNew) {
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.brand.accent, contentColor = MaterialTheme.brand.onAccent) { Text(stringResource(R.string.new_move)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(totalAmount(e.unit, progress.done), style = MaterialTheme.typography.titleLarge)
                        Text(
                            " / " + totalAmount(e.unit, progress.goal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    SetSegments(progress.done, progress.goal, e.amountPerSet, Modifier.padding(top = 6.dp))
                }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val undoLabel = stringResource(R.string.cd_undo_set, name)
                    OutlinedIconButton(
                        onClick = onUndo,
                        enabled = progress.done > 0,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = undoLabel },
                    ) { Icon(painterResource(R.drawable.ic_undo), contentDescription = null) }
                    val amount = setAmount(e)
                    val addLabel = stringResource(R.string.cd_add_set, amount, name)
                    Button(onClick = onAdd, modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = addLabel }) {
                        Text("+ $amount", style = MaterialTheme.typography.titleMedium)
                    }
                    if (onSwap != null) TextButton(onClick = onSwap) { Text(stringResource(R.string.swap)) }
                }
            }
        }
    }
}

/** A finished focus move shrinks to one line, so the eye lands on what is still left. */
@Composable
private fun DoneRow(progress: ExerciseProgress, onClick: () -> Unit) {
    val brand = MaterialTheme.brand
    val e = progress.exercise
    FitCard(Modifier.fillMaxWidth(), onClick = onClick, color = brand.doneContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).background(brand.done, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.cd_done), tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(exerciseName(e), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.progress_of, totalAmount(e.unit, progress.done), totalAmount(e.unit, progress.goal)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OtherMoveRow(progress: ExerciseProgress, onAdd: () -> Unit, onUndo: () -> Unit, onOpen: () -> Unit) {
    val e = progress.exercise
    val name = exerciseName(e)
    val openLabel = stringResource(R.string.cd_open_tutorial, name)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoveFigure(
                pose = Poses.forKey(e.builtIn?.key),
                contentDescription = null,
                animate = false,
                modifier = Modifier.width(48.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (progress.done > 0) {
                    Text(totalAmount(e.unit, progress.done), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (progress.done > 0) {
            val undoLabel = stringResource(R.string.cd_undo_set, name)
            TextButton(onClick = onUndo, modifier = Modifier.semantics { contentDescription = undoLabel }) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        val amount = setAmount(e)
        val addLabel = stringResource(R.string.cd_add_set, amount, name)
        TextButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = addLabel }) { Text("+$amount") }
    }
}
