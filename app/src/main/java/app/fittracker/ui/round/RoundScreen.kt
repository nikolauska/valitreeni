@file:OptIn(ExperimentalMaterial3Api::class)

package app.fittracker.ui.round

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.data.DayView
import app.fittracker.domain.LogSource
import app.fittracker.reminders.Notifications
import app.fittracker.ui.BackButton
import app.fittracker.ui.exerciseName
import app.fittracker.ui.figure.MoveFigure
import app.fittracker.ui.figure.Poses
import app.fittracker.ui.setAmount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoundUiState(val day: DayView, val newMoves: Set<Long>)

@OptIn(ExperimentalCoroutinesApi::class)
class RoundViewModel(private val app: FitApp) : ViewModel() {
    private val repo = app.repository

    val state: StateFlow<RoundUiState?> = flow {
        val today = repo.today()
        repo.ensureFocus(today)
        emitAll(
            repo.observeDay(today).map { day ->
                RoundUiState(day, day.round.items.map { it.exerciseId }.filter { repo.isNewMove(it, today) }.toSet())
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun log(sets: List<Pair<Long, Int>>, onDone: () -> Unit) = viewModelScope.launch {
        repo.logAll(sets, LogSource.APP)
        // The round is done from the app, so the reminder for it is no longer needed.
        Notifications.cancelRound(app)
        onDone()
    }
}

@Composable
fun RoundScreen(onBack: () -> Unit, onOpenTutorial: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as FitApp
    val vm: RoundViewModel = viewModel { RoundViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Moves the user unticked because they skipped them this round; everything else counts as done.
    val skipped = remember { mutableStateMapOf<Long, Boolean>() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.round_title)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        val s = state ?: return@Scaffold
        val round = s.day.round
        val byId = s.day.focus.associateBy { it.exercise.id }
        if (round.isEmpty) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.all_focus_done), style = MaterialTheme.typography.titleLarge)
                Button(onClick = onBack) { Text(stringResource(R.string.back_to_today)) }
            }
            return@Scaffold
        }
        val selected = round.items.filter { skipped[it.exerciseId] != true }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.notif_round_summary, round.roundNumber, round.totalRounds) + " · " +
                        stringResource(R.string.about_minutes, round.estimatedMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(round.items, key = { it.exerciseId }) { item ->
                val progress = byId[item.exerciseId] ?: return@items
                val checked = skipped[item.exerciseId] != true
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .toggleable(checked, role = Role.Checkbox) { skipped[item.exerciseId] = !it }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MoveFigure(
                            Poses.forKey(progress.exercise.builtIn?.key),
                            contentDescription = null,
                            modifier = Modifier.width(96.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(exerciseName(progress.exercise), style = MaterialTheme.typography.titleMedium)
                            Text(setAmount(progress.exercise, item.amount), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            if (item.exerciseId in s.newMoves) {
                                TextButton(onClick = { onOpenTutorial(item.exerciseId) }, contentPadding = PaddingValues(0.dp)) {
                                    Text(stringResource(R.string.new_move_see_how))
                                }
                            }
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
            item {
                Button(
                    onClick = { vm.log(selected.map { it.exerciseId to it.amount }, onBack) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(pluralStringResource(R.plurals.log_sets, selected.size, selected.size))
                }
            }
        }
    }
}
