package app.fittracker.ui.round

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fittracker.FitApp
import app.fittracker.data.Exercise
import app.fittracker.domain.Effort
import app.fittracker.domain.LogSource
import app.fittracker.reminders.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlannedSet(
    val exercise: Exercise,
    val amount: Int,
    val isNew: Boolean,
    /** The version the "Easier version" link switches to; null at the bottom of the ladder. */
    val easier: Exercise?,
)

data class RoundPlan(val sets: List<PlannedSet>, val roundNumber: Int, val totalRounds: Int, val estimatedMinutes: Int)

/** Guards against a second tap landing on the next move's Done button. */
private const val ADVANCE_COOLDOWN_MS = 500L

class RoundViewModel(private val app: FitApp) : ViewModel() {
    private val repo = app.repository

    // Fixed when the screen opens: each logged set changes today's round, and the plan must not
    // shift under the user halfway through.
    private val _plan = MutableStateFlow<RoundPlan?>(null)
    val plan: StateFlow<RoundPlan?> = _plan.asStateFlow()

    var index by mutableIntStateOf(0)
        private set
    var logged by mutableIntStateOf(0)
        private set
    var saving by mutableStateOf(false)
        private set
    private var lastAdvance = 0L

    init {
        viewModelScope.launch {
            val today = repo.today()
            repo.ensureFocus(today)
            val day = repo.day(today)
            val byId = day.focus.associateBy { it.exercise.id }
            val sets = day.round.items.mapNotNull { item ->
                byId[item.exerciseId]?.let { plannedSet(it.exercise, item.amount) }
            }
            _plan.value = RoundPlan(sets, day.round.roundNumber, day.round.totalRounds, day.round.estimatedMinutes)
        }
    }

    private suspend fun plannedSet(exercise: Exercise, amount: Int) =
        PlannedSet(exercise, amount, repo.isNewMove(exercise.id, repo.today()), repo.easierVersion(exercise.id))

    /** Logs the current move's set right away, so leaving halfway keeps what was done. */
    fun done(effort: Effort) {
        val set = _plan.value?.sets?.getOrNull(index) ?: return
        if (!advance()) return
        logged++
        // Runs in the app scope: closing the screen right after the last Done must not drop the save.
        app.appScope.launch {
            repo.log(set.exercise.id, set.amount, LogSource.APP, effort)
            // The user is doing the round in the app, so the reminder for it is no longer needed.
            Notifications.cancelRound(app)
        }
    }

    /** Swaps the current move for its easier version in place; the rest of the round stays as it was. */
    fun switchToEasier() {
        val plan = _plan.value ?: return
        val set = plan.sets.getOrNull(index) ?: return
        if (set.easier == null || saving) return
        saving = true
        viewModelScope.launch {
            val easier = repo.switchToEasier(set.exercise.id, repo.today())
            if (easier != null) {
                val sets = plan.sets.toMutableList()
                sets[index] = plannedSet(easier, easier.amountPerSet)
                _plan.value = plan.copy(sets = sets)
            }
            saving = false
        }
    }

    fun skip() {
        advance()
    }

    private fun advance(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastAdvance < ADVANCE_COOLDOWN_MS) return false
        lastAdvance = now
        index++
        return true
    }

    /** "Log all at once" for a round done without the phone; ignores taps while saving. */
    fun logAll(sets: List<PlannedSet>, onDone: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            repo.logAll(sets.map { it.exercise.id to it.amount }, LogSource.APP)
            Notifications.cancelRound(app)
            onDone()
        }
    }
}
