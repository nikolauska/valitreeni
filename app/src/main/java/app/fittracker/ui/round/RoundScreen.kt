@file:OptIn(ExperimentalMaterial3Api::class)

package app.fittracker.ui.round

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.domain.Effort
import app.fittracker.domain.MoveUnit
import app.fittracker.ui.BackButton
import app.fittracker.ui.FitCard
import app.fittracker.ui.currentLocale
import app.fittracker.ui.exerciseName
import app.fittracker.ui.figure.MoveFigure
import app.fittracker.ui.figure.Poses
import app.fittracker.ui.setAmount
import app.fittracker.ui.theme.brand
import kotlinx.coroutines.delay

@Composable
fun RoundScreen(onBack: () -> Unit, onOpenTutorial: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as FitApp
    val vm: RoundViewModel = viewModel { RoundViewModel(app) }
    val plan by vm.plan.collectAsStateWithLifecycle()
    var checklist by rememberSaveable { mutableStateOf(false) }

    // The phone lies on the floor during a round; it must not dim and lock between moves.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val p = plan ?: return
    when {
        p.sets.isEmpty() -> AllDone(onBack)
        checklist -> Checklist(p, p.sets.drop(vm.index), vm.saving, onBack = { checklist = false }, onLog = { vm.logAll(it, onBack) })
        vm.index >= p.sets.size -> Finished(vm.logged, onBack)
        else -> Guided(
            p, vm.index, onClose = onBack, onLogAll = { checklist = true }, onDone = vm::done, onSkip = vm::skip,
            onEasier = vm::switchToEasier, onOpenTutorial = onOpenTutorial,
        )
    }
}

@Composable
private fun Guided(
    plan: RoundPlan,
    index: Int,
    onClose: () -> Unit,
    onLogAll: () -> Unit,
    onDone: (Effort) -> Unit,
    onSkip: () -> Unit,
    onEasier: () -> Unit,
    onOpenTutorial: (Long) -> Unit,
) {
    val set = plan.sets[index]
    // Starts on OK for every move, so answering costs no tap when the set felt fine.
    var effort by rememberSaveable(index, set.exercise.id) { mutableStateOf(Effort.OK) }
    val e = set.exercise
    val brand = MaterialTheme.brand
    val next = plan.sets.getOrNull(index + 1)?.let { exerciseName(it.exercise) }
    // Keyed by the move too: "Easier version" swaps the move at the same index, and its hold
    // time must not inherit the harder move's countdown.
    val timer = if (e.unit == MoveUnit.SECONDS) rememberHoldTimer(set.amount, index, e.id) else null
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cd_close)) }
                },
                title = { ProgressDots(plan.sets.size, index) },
                actions = { TextButton(onClick = onLogAll) { Text(stringResource(R.string.round_log_all)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(
                    stringResource(R.string.notif_round_summary, plan.roundNumber, plan.totalRounds).uppercase(currentLocale()) +
                        "  ·  ${index + 1} / ${plan.sets.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(exerciseName(e), style = MaterialTheme.typography.displaySmall)
            }
            FitCard(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    // The tile owns the width and the figure inside may shrink. With fillMaxWidth on the
                    // figure itself, its aspect ratio couldn't be met when the timer took the height, so
                    // the canvas was measured too tall and drew over the timer.
                    val tileShape = RoundedCornerShape(16.dp)
                    val holdFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    // The countdown ticks once a second; easing between ticks keeps the fill from stepping.
                    val holdProgress by animateFloatAsState(timer?.progress ?: 0f, tween(1_000, easing = LinearEasing), label = "hold")
                    Box(
                        Modifier.fillMaxWidth().weight(1f, fill = false)
                            .clip(tileShape).background(brand.track)
                            // Hold progress fills the figure's tile, so the timer below needs no ring of its own.
                            .drawBehind { if (holdProgress > 0f) drawRect(holdFill, size = Size(size.width * holdProgress, size.height)) }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MoveFigure(
                            Poses.forKey(e.builtIn?.key),
                            contentDescription = exerciseName(e),
                            color = brand.figure,
                            accent = brand.accent,
                        )
                    }
                    if (timer != null) {
                        HoldCountdown(timer)
                    } else {
                        Text(set.amount.toString(), style = MaterialTheme.typography.displayLarge)
                        Text(
                            if (e.builtIn?.perSide == true) stringResource(R.string.round_reps_per_leg) else stringResource(R.string.round_reps),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Tip(set, onOpenTutorial)
                }
            }
            EffortPicker(effort) { effort = it }
            set.easier?.let { easier ->
                TextButton(onClick = onEasier, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.round_easier_version, exerciseName(easier)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.height(56.dp)) { Text(stringResource(R.string.round_skip)) }
                Button(
                    onClick = { onDone(effort) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brand.accent, contentColor = brand.onAccent),
                ) {
                    Text(
                        if (next != null) stringResource(R.string.round_done_next, next) else stringResource(R.string.round_done),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        // Some move names are long (e.g. "Istuvat polven ojennukset"); show the cut instead of hiding it.
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EffortPicker(selected: Effort, onSelect: (Effort) -> Unit) {
    val labels = listOf(
        Effort.HARD to stringResource(R.string.round_effort_hard),
        Effort.OK to stringResource(R.string.round_effort_ok),
        Effort.EASY to stringResource(R.string.round_effort_easy),
    )
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.round_effort_question),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, (effort, label) ->
                SegmentedButton(
                    selected = selected == effort,
                    onClick = { onSelect(effort) },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
    }
}

@Composable
private fun ProgressDots(count: Int, current: Int) {
    val brand = MaterialTheme.brand
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val color = when {
                i < current -> MaterialTheme.colorScheme.primary
                i == current -> brand.accent
                else -> brand.track
            }
            Box(Modifier.width(24.dp).height(6.dp).background(color, CircleShape))
        }
    }
}

/** One line from the tutorial's "avoid" list: the most common form mistake, right where it matters. */
@Composable
private fun Tip(set: PlannedSet, onOpenTutorial: (Long) -> Unit) {
    val tip = set.exercise.builtIn?.tutorial?.let { stringArrayResource(it.avoid).firstOrNull() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (tip != null) {
            Text(
                stringResource(R.string.round_watch, tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        TextButton(onClick = { onOpenTutorial(set.exercise.id) }) {
            Text(stringResource(if (set.isNew) R.string.new_move_see_how else R.string.tutorial_how_to))
        }
    }
}

/** Countdown for a timed move. Lives in [Guided] so the figure tile and the number below it share it. */
@Stable
private class HoldTimer(val seconds: Int, remainingState: MutableIntState, runningState: MutableState<Boolean>) {
    var remaining by remainingState
    var running by runningState
    val progress: Float get() = 1f - remaining.toFloat() / seconds.coerceAtLeast(1)

    fun toggle() {
        if (remaining == 0) {
            remaining = seconds
            running = true
        } else {
            running = !running
        }
    }
}

@Composable
private fun rememberHoldTimer(seconds: Int, index: Int, exerciseId: Long): HoldTimer {
    val context = LocalContext.current
    // Saveable so rotating the phone or the app being killed mid-hold doesn't restart the countdown.
    val remaining = rememberSaveable(index, exerciseId) { mutableIntStateOf(seconds) }
    val running = rememberSaveable(index, exerciseId) { mutableStateOf(false) }
    val timer = remember(seconds, remaining, running) { HoldTimer(seconds, remaining, running) }
    LaunchedEffect(timer, timer.running) {
        while (timer.running && timer.remaining > 0) {
            delay(1_000)
            timer.remaining--
        }
        if (timer.running && timer.remaining == 0) {
            timer.running = false
            buzz(context)
        }
    }
    return timer
}

/** Shown where a rep move shows its count, so timed and rep moves read the same way. */
@Composable
private fun HoldCountdown(timer: HoldTimer) {
    val remaining = timer.remaining
    Text(
        if (remaining == 0) stringResource(R.string.round_timer_up) else "%d:%02d".format(remaining / 60, remaining % 60),
        style = MaterialTheme.typography.displayLarge,
        // Announced once a second would be noise; TalkBack users hear only the end.
        modifier = Modifier.semantics { if (remaining == 0) liveRegion = LiveRegionMode.Polite },
    )
    Text(
        stringResource(R.string.round_timer_of, timer.seconds),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledTonalButton(onClick = timer::toggle) {
        Text(
            stringResource(
                when {
                    remaining == 0 -> R.string.round_timer_again
                    timer.running -> R.string.round_timer_pause
                    remaining < timer.seconds -> R.string.round_timer_resume
                    else -> R.string.round_timer_start
                },
            ),
        )
    }
}

/** Short double buzz: the hold is over, and the user isn't looking at the screen. */
private fun buzz(context: Context) {
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
}

@Composable
private fun Finished(logged: Int, onBack: () -> Unit) {
    val brand = MaterialTheme.brand
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(88.dp).background(brand.done, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp))
            }
            Text(stringResource(R.string.round_finished_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                pluralStringResource(R.plurals.round_finished_sets, logged, logged),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.back_to_today)) }
        }
    }
}

@Composable
private fun AllDone(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.round_title)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.all_focus_done), style = MaterialTheme.typography.titleLarge)
            Button(onClick = onBack) { Text(stringResource(R.string.back_to_today)) }
        }
    }
}

/** For a round done without the phone: tick off what was done and log it in one go. */
@Composable
private fun Checklist(plan: RoundPlan, remaining: List<PlannedSet>, saving: Boolean, onBack: () -> Unit, onLog: (List<PlannedSet>) -> Unit) {
    // Moves the user unticked because they skipped them; everything else counts as done.
    val skipped = remember { mutableStateMapOf<Long, Boolean>() }
    val selected = remaining.filter { skipped[it.exercise.id] != true }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.round_checklist_title)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.notif_round_summary, plan.roundNumber, plan.totalRounds) + " · " +
                        stringResource(R.string.about_minutes, plan.estimatedMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(remaining, key = { it.exercise.id }) { set ->
                val checked = skipped[set.exercise.id] != true
                FitCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.toggleable(checked, role = Role.Checkbox) { skipped[set.exercise.id] = !it }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MoveFigure(
                            Poses.forKey(set.exercise.builtIn?.key),
                            contentDescription = null,
                            animate = false,
                            modifier = Modifier.width(72.dp),
                            color = MaterialTheme.brand.figure,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(exerciseName(set.exercise), style = MaterialTheme.typography.titleMedium)
                            Text(setAmount(set.exercise, set.amount), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
            item {
                Button(
                    onClick = { onLog(selected) },
                    // Disabled while saving: a second tap used to log the whole round twice.
                    enabled = selected.isNotEmpty() && !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(pluralStringResource(R.plurals.log_sets, selected.size, selected.size), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
