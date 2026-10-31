package app.fittracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fittracker.FitApp
import app.fittracker.data.DayView
import app.fittracker.domain.LogSource
import app.fittracker.domain.Streak
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUiState(
    val day: DayView,
    val streak: Int,
    val newMoves: Set<Long>,
    val nextReminderAt: Instant?,
    val missedReminderAt: Instant?,
    val paused: Boolean,
)

/** How far back history is loaded for the streak; a longer streak simply shows as this many days. */
private const val STREAK_LOOKBACK_DAYS = 400L

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val app: FitApp) : ViewModel() {
    private val repo = app.repository

    // Re-checked every 30 s so the screen rolls over to a new day at midnight while it is open.
    private val date = flow {
        while (true) {
            emit(repo.today())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<TodayUiState?> = date.flatMapLatest { day ->
        flow {
            repo.ensureFocus(day)
            emitAll(
                combine(
                    repo.observeDay(day),
                    repo.observeHistory(day.minusDays(STREAK_LOOKBACK_DAYS), day),
                    app.settings.settings,
                ) { view, history, settings ->
                    TodayUiState(
                        day = view,
                        streak = Streak.length(history.days, day),
                        // Re-evaluated on every change so a swapped-in move gets its "new" badge too.
                        newMoves = view.focus.map { it.exercise.id }.filter { repo.isNewMove(it, day) }.toSet(),
                        nextReminderAt = settings.nextReminderAt,
                        missedReminderAt = settings.missedReminderAt,
                        paused = settings.paused,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addSet(exerciseId: Long, amount: Int) = viewModelScope.launch { repo.log(exerciseId, amount, LogSource.APP) }

    fun undo(exerciseId: Long, day: LocalDate) = viewModelScope.launch { repo.undoLast(exerciseId, day) }

    fun swap(exerciseId: Long, day: LocalDate) = viewModelScope.launch { repo.swapFocus(day, exerciseId) }

    fun dismissMissedReminder() = viewModelScope.launch { app.settings.setMissedReminderAt(null) }
}
