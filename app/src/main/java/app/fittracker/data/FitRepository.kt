package app.fittracker.data

import app.fittracker.domain.BodyArea
import app.fittracker.domain.FocusCandidate
import app.fittracker.domain.FocusDay
import app.fittracker.domain.FocusPicker
import app.fittracker.domain.FocusProgress
import app.fittracker.domain.GoalChange
import app.fittracker.domain.Goals
import app.fittracker.domain.LogSource
import app.fittracker.domain.MoveUnit
import app.fittracker.domain.Round
import app.fittracker.domain.RoundBuilder
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Exercise(
    val id: Long,
    val builtIn: BuiltInMove?,
    val customName: String?,
    val unit: MoveUnit,
    val area: BodyArea,
    val amountPerSet: Int,
    val enabled: Boolean,
    val sortOrder: Int,
)

data class ExerciseProgress(
    val exercise: Exercise,
    val goal: Int,
    val done: Int,
    val inFocus: Boolean,
) {
    val goalMet get() = done >= goal
}

data class DayView(
    val date: LocalDate,
    val focus: List<ExerciseProgress>,
    val others: List<ExerciseProgress>,
    val round: Round,
)

/** Everything the history screen and streak need for a date range. */
data class HistoryData(
    val exercises: List<Exercise>,
    val totals: Map<LocalDate, Map<Long, Int>>,
    val goalChanges: List<GoalChange>,
    val days: Map<LocalDate, FocusDay>,
)

class FitRepository(
    private val dao: FitDao,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    // Focus is created lazily by whoever looks first (app or alarm); the lock stops both picking at once.
    private val focusLock = Mutex()

    fun today(): LocalDate = now().toLocalDate()

    fun observeExercises(): Flow<List<Exercise>> = dao.observeExercises().map { list -> list.map { it.toModel() } }

    suspend fun exercise(id: Long): Exercise? = dao.exercise(id)?.toModel()

    /** Every move with the goal that applies on [date], including disabled ones (for Settings). */
    fun observeExercisesWithGoals(date: LocalDate): Flow<List<Pair<Exercise, Int>>> =
        combine(dao.observeExercises(), dao.observeGoalChanges()) { exercises, changes ->
            val models = changes.map { it.toModel() }
            exercises.map { it.toModel() to (Goals.goalFor(it.id, date, models) ?: 0) }
        }

    fun observeDay(date: LocalDate): Flow<DayView> =
        combine(
            dao.observeExercises(),
            dao.observeGoalChanges(),
            dao.observeTotals(date, date),
            dao.observeFocus(date, date),
        ) { exercises, changes, totals, focus ->
            buildDay(date, exercises, changes.map { it.toModel() }, totals, focus)
        }

    suspend fun day(date: LocalDate): DayView =
        buildDay(date, dao.exercises(), dao.goalChanges().map { it.toModel() }, dao.totals(date, date), dao.focus(date))

    fun observeHistory(from: LocalDate, to: LocalDate): Flow<HistoryData> =
        combine(
            dao.observeExercises(),
            dao.observeGoalChanges(),
            dao.observeTotals(from, to),
            dao.observeFocus(from, to),
        ) { exercises, changeEntities, totals, focus ->
            val changes = changeEntities.map { it.toModel() }
            val totalsByDay = totals.groupBy { it.localDate }
                .mapValues { (_, rows) -> rows.associate { it.exerciseId to it.total } }
            val focusByDay = focus.groupBy({ it.localDate }, { it.exerciseId })
            val days = (focusByDay.keys + totalsByDay.keys).associateWith { date ->
                val dayTotals = totalsByDay[date].orEmpty()
                FocusDay(
                    goalsMet = focusByDay[date].orEmpty().map { id ->
                        val goal = Goals.goalFor(id, date, changes) ?: 0
                        (dayTotals[id] ?: 0) >= goal
                    },
                    anythingLogged = dayTotals.values.any { it > 0 },
                )
            }
            HistoryData(exercises.map { it.toModel() }, totalsByDay, changes, days)
        }

    /** Makes sure [date] has its focus moves; safe to call any number of times. */
    suspend fun ensureFocus(date: LocalDate) = focusLock.withLock {
        if (dao.focus(date).isNotEmpty()) return@withLock
        val picked = FocusPicker.pick(candidates())
        dao.insertFocus(picked.map { DailyFocusEntity(date, it) })
    }

    suspend fun swapFocus(date: LocalDate, exerciseId: Long) = focusLock.withLock {
        val current = dao.focus(date).map { it.exerciseId }
        val replacement = FocusPicker.replacement(candidates(), current, exerciseId) ?: return@withLock
        dao.deleteFocus(date, exerciseId)
        dao.insertFocus(listOf(DailyFocusEntity(date, replacement)))
    }

    suspend fun isNewMove(exerciseId: Long, date: LocalDate): Boolean = dao.daysInFocusBefore(exerciseId, date) == 0

    suspend fun log(exerciseId: Long, amount: Int, source: LogSource) = logAll(listOf(exerciseId to amount), source)

    /** Logs one set of every given move at once (the notification's "Done round"). */
    suspend fun logAll(sets: List<Pair<Long, Int>>, source: LogSource) {
        val at = now()
        dao.insertLogs(
            sets.map { (id, amount) ->
                LogEntryEntity(exerciseId = id, amount = amount, timestamp = at.toInstant(), localDate = at.toLocalDate(), source = source)
            },
        )
    }

    suspend fun undoLast(exerciseId: Long, date: LocalDate) = dao.deleteLatestLog(exerciseId, date)

    suspend fun updateExercise(id: Long, amountPerSet: Int, enabled: Boolean, goal: Int, customName: String?) {
        val existing = dao.exercise(id) ?: return
        dao.updateExercise(
            existing.copy(
                amountPerSet = amountPerSet,
                enabled = enabled,
                customName = if (existing.builtinKey == null) customName else null,
            ),
        )
        val today = today()
        if (Goals.goalFor(id, today, dao.goalChanges().map { it.toModel() }) != goal) {
            dao.setGoal(id, goal, today)
        }
    }

    suspend fun addCustomExercise(name: String, unit: MoveUnit, area: BodyArea, amountPerSet: Int, goal: Int): Long {
        val id = dao.insertExercise(
            ExerciseEntity(
                builtinKey = null,
                customName = name,
                unit = unit,
                bodyArea = area,
                amountPerSet = amountPerSet,
                sortOrder = dao.maxSortOrder() + 1,
            ),
        )
        // A new move's goal applies from day 0, so it doesn't make older days look unfinished.
        dao.setGoal(id, goal, LocalDate.ofEpochDay(0))
        return id
    }

    private suspend fun candidates(): List<FocusCandidate> {
        val lastDone = dao.lastDoneDates().associate { it.exerciseId to it.lastDate }
        return dao.exercises().filter { it.enabled }.map {
            FocusCandidate(it.id, it.bodyArea, lastDone[it.id], it.sortOrder)
        }
    }

    private fun buildDay(
        date: LocalDate,
        exercises: List<ExerciseEntity>,
        changes: List<GoalChange>,
        totals: List<DailyTotal>,
        focus: List<DailyFocusEntity>,
    ): DayView {
        val done = totals.associate { it.exerciseId to it.total }
        val focusIds = focus.map { it.exerciseId }.toSet()
        val progress = exercises.map { e ->
            ExerciseProgress(
                exercise = e.toModel(),
                goal = Goals.goalFor(e.id, date, changes) ?: 0,
                done = done[e.id] ?: 0,
                inFocus = e.id in focusIds,
            )
        }
        val focusProgress = progress.filter { it.inFocus }
        val round = RoundBuilder.build(
            focusProgress.map {
                FocusProgress(it.exercise.id, it.exercise.unit, it.exercise.amountPerSet, it.goal, it.done)
            },
        )
        return DayView(
            date = date,
            focus = focusProgress,
            // Disabled moves still show if something was logged for them that day.
            others = progress.filter { !it.inFocus && (it.exercise.enabled || it.done > 0) },
            round = round,
        )
    }
}

private fun ExerciseEntity.toModel() = Exercise(
    id = id,
    builtIn = MoveCatalog.byKey(builtinKey),
    customName = customName,
    unit = unit,
    area = bodyArea,
    amountPerSet = amountPerSet,
    enabled = enabled,
    sortOrder = sortOrder,
)

private fun GoalChangeEntity.toModel() = GoalChange(exerciseId, dailyGoal, effectiveFrom)
