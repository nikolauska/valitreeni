package app.fittracker.data

import app.fittracker.domain.BodyArea
import app.fittracker.domain.Effort
import app.fittracker.domain.LadderSpot
import app.fittracker.domain.Ladders
import app.fittracker.domain.Progression
import app.fittracker.domain.Rung
import app.fittracker.domain.StartLevel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    /** The version of each move the user is on now, plus their own moves; see [FitRepository.observeMoves]. */
    val currentIds: Set<Long>,
)

/** One ladder as Settings shows it: every version with its goal, easiest first. */
data class LadderView(
    val key: String,
    val rungs: List<Pair<Exercise, Int>>,
    /** The version actually trained; null when every version is off or on the floor while the floor is avoided. */
    val currentId: Long?,
    val autoAdjust: Boolean,
) {
    val current get() = rungs.firstOrNull { it.first.id == currentId }
}

data class MovesView(val ladders: List<LadderView>, val custom: List<Pair<Exercise, Int>>)

/** A built-in ladder joined with its database rows; [entities] line up with [moves]. */
private class LadderData(
    val key: String,
    val moves: List<BuiltInMove>,
    val entities: List<ExerciseEntity>,
    val state: LadderStateEntity,
) {
    val rungs: List<Rung> = moves.zip(entities) { m, e ->
        Rung(e.id, e.unit, m.minAmount, m.amountPerSet, m.maxAmount, m.floor, e.enabled, m.level)
    }
    val activeIndex: Int = entities.indexOfFirst { it.id == state.exerciseId }.coerceAtLeast(0)

    fun effectiveIndex(avoidFloor: Boolean): Int? = Ladders.effectiveRung(rungs, activeIndex, avoidFloor)

    fun effective(avoidFloor: Boolean): ExerciseEntity? = effectiveIndex(avoidFloor)?.let(entities::get)

    fun easierIndex(from: Int, avoidFloor: Boolean): Int? =
        (from - 1 downTo 0).firstOrNull { Ladders.isAllowed(rungs[it], avoidFloor) }
}

class FitRepository(
    private val dao: FitDao,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
    /** The "avoid moves on the floor" setting; a flow so open screens follow it when it changes. */
    private val avoidFloor: Flow<Boolean> = flowOf(false),
) {
    // Focus is created lazily by whoever looks first (app or alarm); the lock stops both picking at once.
    private val focusLock = Mutex()

    fun today(): LocalDate = now().toLocalDate()

    /** Today's date, re-checked every 30 s so open screens roll over to the new day at midnight. */
    fun observeToday(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(30_000)
        }
    }.distinctUntilChanged()

    /** Goal that applies to the move on [date], also for moves that are turned off. */
    suspend fun goal(exerciseId: Long, date: LocalDate): Int =
        Goals.goalFor(exerciseId, date, dao.goalChanges().map { it.toModel() }) ?: 0

    fun observeExercises(): Flow<List<Exercise>> = dao.observeExercises().map { list -> list.map { it.toModel() } }

    suspend fun exercise(id: Long): Exercise? = dao.exercise(id)?.toModel()

    fun observeDay(date: LocalDate): Flow<DayView> =
        combine(
            combine(dao.observeExercises(), dao.observeGoalChanges(), dao.observeTotals(date, date), dao.observeFocus(date, date), ::DayRows),
            dao.observeLadderStates(),
            avoidFloor,
        ) { rows, states, avoid ->
            buildDay(date, rows.exercises, rows.changes.map { it.toModel() }, rows.totals, rows.focus, currentIds(rows.exercises, states, avoid))
        }

    suspend fun day(date: LocalDate): DayView {
        val exercises = dao.exercises()
        return buildDay(
            date, exercises, dao.goalChanges().map { it.toModel() }, dao.totals(date, date), dao.focus(date),
            currentIds(exercises, dao.ladderStates(), avoidFloor.first()),
        )
    }

    /** Every move grouped into its ladder, for Settings. */
    fun observeMoves(date: LocalDate): Flow<MovesView> =
        combine(dao.observeExercises(), dao.observeGoalChanges(), dao.observeLadderStates(), avoidFloor) { exercises, changes, states, avoid ->
            val models = changes.map { it.toModel() }
            fun withGoal(e: ExerciseEntity) = e.toModel() to (Goals.goalFor(e.id, date, models) ?: 0)
            MovesView(
                ladders = ladders(exercises, states).map { l ->
                    LadderView(l.key, l.entities.map(::withGoal), l.effective(avoid)?.id, l.state.autoAdjust)
                },
                custom = exercises.filter { it.builtinKey == null }.map(::withGoal),
            )
        }

    suspend fun ladderOf(exerciseId: Long): LadderView? =
        observeMoves(today()).first().ladders.firstOrNull { l -> l.rungs.any { it.first.id == exerciseId } }

    /** Whether the first-run level question still makes sense: nobody has trained on this phone yet. */
    fun observeAnyLogs(): Flow<Boolean> = dao.observeAnyLogs()

    fun observeHistory(from: LocalDate, to: LocalDate): Flow<HistoryData> =
        combine6(
            dao.observeExercises(),
            dao.observeGoalChanges(),
            dao.observeTotals(from, to),
            dao.observeFocus(from, to),
            dao.observeLadderStates(),
            avoidFloor,
        ) { exercises, changeEntities, totals, focus, states, avoid ->
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
            HistoryData(exercises.map { it.toModel() }, totalsByDay, changes, days, currentIds(exercises, states, avoid))
        }

    /** Makes sure [date] has its focus moves; safe to call any number of times. */
    suspend fun ensureFocus(date: LocalDate) = focusLock.withLock {
        if (dao.focus(date).isNotEmpty()) return@withLock
        // Adjusting right before the day's first pick means it happens once a day and nothing
        // changes under the user later in the day.
        adjustLadders(date)
        val picked = FocusPicker.pick(candidates())
        dao.insertFocus(picked.map { DailyFocusEntity(date, it) })
    }

    suspend fun swapFocus(date: LocalDate, exerciseId: Long) = focusLock.withLock {
        val current = dao.focus(date).map { it.exerciseId }
        val replacement = FocusPicker.replacement(candidates(), current, exerciseId) ?: return@withLock
        dao.deleteFocus(date, exerciseId)
        dao.insertFocus(listOf(DailyFocusEntity(date, replacement)))
    }

    /**
     * Takes a move out of [date]'s focus, putting another one in its place when there is one.
     * Unlike [swapFocus] the move leaves even without a replacement: it was turned off or deleted.
     */
    private suspend fun dropFromFocus(date: LocalDate, exerciseId: Long) = focusLock.withLock {
        val current = dao.focus(date).map { it.exerciseId }
        if (exerciseId !in current) return@withLock
        val replacement = FocusPicker.replacement(candidates().filter { it.exerciseId != exerciseId }, current, exerciseId)
        dao.deleteFocus(date, exerciseId)
        replacement?.let { dao.insertFocus(listOf(DailyFocusEntity(date, it))) }
    }

    suspend fun isNewMove(exerciseId: Long, date: LocalDate): Boolean = dao.daysInFocusBefore(exerciseId, date) == 0

    suspend fun log(exerciseId: Long, amount: Int, source: LogSource, effort: Effort? = null) {
        val at = now()
        dao.insertLogs(listOf(LogEntryEntity(exerciseId = exerciseId, amount = amount, timestamp = at.toInstant(), localDate = at.toLocalDate(), source = source, effort = effort)))
    }

    /** Logs one set of every given move at once (the notification's "Done round"); no answers, so no adjustment signal. */
    suspend fun logAll(sets: List<Pair<Long, Int>>, source: LogSource) {
        val at = now()
        dao.insertLogs(
            sets.map { (id, amount) ->
                LogEntryEntity(exerciseId = id, amount = amount, timestamp = at.toInstant(), localDate = at.toLocalDate(), source = source)
            },
        )
    }

    /** The next easier version the user could switch to right now, or null at the bottom of the ladder. */
    suspend fun easierVersion(exerciseId: Long): Exercise? {
        val (ladder, index) = findLadder(exerciseId) ?: return null
        return ladder.easierIndex(index, avoidFloor.first())?.let { ladder.entities[it].toModel() }
    }

    /**
     * "Easier version" during a round: moves the ladder down a version now instead of tomorrow,
     * because someone who can't do the move at all shouldn't be stuck with it for the rest of the day.
     */
    suspend fun switchToEasier(exerciseId: Long, date: LocalDate): Exercise? = focusLock.withLock {
        val (ladder, index) = findLadder(exerciseId) ?: return@withLock null
        val easier = ladder.easierIndex(index, avoidFloor.first()) ?: return@withLock null
        moveTo(ladder, index, LadderSpot(easier, ladder.rungs[easier].startAmount), date)
        val newId = ladder.entities[easier].id
        replaceInFocus(date, exerciseId, newId)
        dao.exercise(newId)?.toModel()
    }

    /** Picking a version by hand in Settings. Automatic adjustment carries on from there if it's on. */
    suspend fun chooseVersion(exerciseId: Long) = focusLock.withLock {
        val (ladder, _) = findLadder(exerciseId) ?: return@withLock
        val date = today()
        val previous = ladder.effective(avoidFloor.first())?.id
        dao.upsertLadderState(ladder.state.copy(exerciseId = exerciseId, changedOn = date))
        if (previous != null && previous != exerciseId) replaceInFocus(date, previous, exerciseId)
    }

    suspend fun setAutoAdjust(ladderKey: String, on: Boolean) {
        val state = dao.ladderStates().firstOrNull { it.ladderKey == ladderKey } ?: return
        // Restarting from today: answers given while it was off were about sets the user picked.
        dao.upsertLadderState(state.copy(autoAdjust = on, changedOn = today()))
    }

    /**
     * Puts every ladder at the version that suits [level] with the usual set sizes, as if the app was
     * just installed. Moves without an easier version are only switched on from the level they suit.
     */
    suspend fun applyStartLevel(level: StartLevel) = focusLock.withLock {
        val date = today()
        val exercises = dao.exercises()
        val changes = dao.goalChanges().map { it.toModel() }
        for (ladder in ladders(exercises, dao.ladderStates())) {
            ladder.moves.zip(ladder.entities).forEach { (move, entity) ->
                val enabled = if (ladder.moves.size == 1) move.level <= level.level else entity.enabled
                dao.updateExercise(entity.copy(amountPerSet = move.amountPerSet, enabled = enabled))
                if (Goals.goalFor(entity.id, date, changes) != move.dailyGoal) dao.setGoal(entity.id, move.dailyGoal, date)
            }
            val start = Ladders.startRung(ladder.rungs, level)
            dao.upsertLadderState(LadderStateEntity(ladder.key, ladder.entities[start].id, autoAdjust = true, changedOn = date))
        }
        // Today's focus was picked from the old moves; the next look picks it again.
        dao.clearFocus(date)
    }

    suspend fun undoLast(exerciseId: Long, date: LocalDate) = dao.deleteLatestLog(exerciseId, date)

    suspend fun updateExercise(id: Long, amountPerSet: Int, enabled: Boolean, goal: Int, customName: String?) {
        val existing = dao.exercise(id) ?: return
        val oldGoal = Goals.goalFor(id, today(), dao.goalChanges().map { it.toModel() })
        // A set size or goal typed by hand would be overwritten by the next adjustment, so it stops.
        if (existing.builtinKey != null && (amountPerSet != existing.amountPerSet || goal != oldGoal)) {
            MoveCatalog.byKey(existing.builtinKey)?.let { setAutoAdjust(it.ladder, false) }
        }
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
        // "Never picked for focus" should hold today too, not only from tomorrow.
        if (!enabled) dropFromFocus(today, id)
    }

    /** Only for the user's own moves; built-in ones can be turned off instead. */
    suspend fun deleteExercise(id: Long) {
        if (dao.exercise(id)?.builtinKey != null) return
        // Replace it in today's focus first, so today keeps a full set of focus moves.
        dropFromFocus(today(), id)
        dao.deleteExercise(id)
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

    /** One candidate per ladder (the version in use) plus the user's own moves. */
    private suspend fun candidates(): List<FocusCandidate> {
        val lastDone = dao.lastDoneDates().associate { it.exerciseId to it.lastDate }
        val exercises = dao.exercises()
        val avoid = avoidFloor.first()
        val fromLadders = ladders(exercises, dao.ladderStates()).mapNotNull { ladder ->
            val current = ladder.effective(avoid) ?: return@mapNotNull null
            // Last done across all versions: switching version shouldn't make the move look never done.
            FocusCandidate(current.id, current.bodyArea, ladder.entities.mapNotNull { lastDone[it.id] }.maxOrNull(), ladder.entities.minOf { it.sortOrder })
        }
        val custom = exercises.filter { it.builtinKey == null && it.enabled }.map {
            FocusCandidate(it.id, it.bodyArea, lastDone[it.id], it.sortOrder)
        }
        return fromLadders + custom
    }

    private suspend fun adjustLadders(date: LocalDate) {
        val avoid = avoidFloor.first()
        for (ladder in ladders(dao.exercises(), dao.ladderStates())) {
            if (!ladder.state.autoAdjust) continue
            val index = ladder.effectiveIndex(avoid) ?: continue
            val current = ladder.entities[index]
            val days = dao.efforts(current.id, ladder.state.changedOn, date)
                .groupBy({ it.localDate }, { it.effort })
                .toSortedMap(compareByDescending { it })
                .values.toList()
            val direction = Progression.decide(days) ?: continue
            val from = LadderSpot(index, current.amountPerSet)
            val target = Progression.move(ladder.rungs, from, direction, avoid)
            if (target != from) moveTo(ladder, index, target, date)
        }
    }

    /** Switches [ladder] to [target], keeping the number of sets per day. */
    private suspend fun moveTo(ladder: LadderData, fromIndex: Int, target: LadderSpot, date: LocalDate) {
        val from = ladder.entities[fromIndex]
        val to = ladder.entities[target.rung]
        val changes = dao.goalChanges().map { it.toModel() }
        val oldGoal = Goals.goalFor(from.id, date, changes) ?: from.amountPerSet
        val newGoal = Progression.goalFor(oldGoal, from.amountPerSet, target.amount)
        if (to.amountPerSet != target.amount) dao.updateExercise(to.copy(amountPerSet = target.amount))
        // Effective today, through goal history, so earlier days keep the goal they were judged by.
        if (Goals.goalFor(to.id, date, changes) != newGoal) dao.setGoal(to.id, newGoal, date)
        dao.upsertLadderState(ladder.state.copy(exerciseId = to.id, changedOn = date))
    }

    private suspend fun replaceInFocus(date: LocalDate, old: Long, new: Long) {
        if (dao.focus(date).none { it.exerciseId == old }) return
        dao.deleteFocus(date, old)
        dao.insertFocus(listOf(DailyFocusEntity(date, new)))
    }

    private suspend fun findLadder(exerciseId: Long): Pair<LadderData, Int>? =
        ladders(dao.exercises(), dao.ladderStates()).firstNotNullOfOrNull { ladder ->
            ladder.entities.indexOfFirst { it.id == exerciseId }.takeIf { it >= 0 }?.let { ladder to it }
        }

    private fun ladders(exercises: List<ExerciseEntity>, states: List<LadderStateEntity>): List<LadderData> {
        val byKey = exercises.filter { it.builtinKey != null }.associateBy { it.builtinKey }
        val stateByKey = states.associateBy { it.ladderKey }
        return MoveCatalog.ladders.mapNotNull { (key, moves) ->
            val entities = moves.map { byKey[it.key] ?: return@mapNotNull null }
            // The catalog sync adds a state for every ladder; the fallback only covers a half-open database.
            val state = stateByKey[key]
                ?: LadderStateEntity(key, byKey.getValue(MoveCatalog.classicRung(key).key).id, changedOn = LocalDate.ofEpochDay(0))
            LadderData(key, moves, entities, state)
        }.sortedBy { ladder -> ladder.entities.minOf { it.sortOrder } }
    }

    private fun currentIds(exercises: List<ExerciseEntity>, states: List<LadderStateEntity>, avoid: Boolean): Set<Long> =
        ladders(exercises, states).mapNotNullTo(mutableSetOf()) { it.effective(avoid)?.id } +
            exercises.filter { it.builtinKey == null && it.enabled }.map { it.id }

    private fun buildDay(
        date: LocalDate,
        exercises: List<ExerciseEntity>,
        changes: List<GoalChange>,
        totals: List<DailyTotal>,
        focus: List<DailyFocusEntity>,
        currentIds: Set<Long>,
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
            // Only the version in use, but anything logged that day still shows (also turned-off moves).
            others = progress.filter { !it.inFocus && (it.exercise.id in currentIds || it.done > 0) },
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

private class DayRows(
    val exercises: List<ExerciseEntity>,
    val changes: List<GoalChangeEntity>,
    val totals: List<DailyTotal>,
    val focus: List<DailyFocusEntity>,
)

/** kotlinx only has typed combine up to five flows. */
@Suppress("UNCHECKED_CAST")
private fun <A, B, C, D, E, F, R> combine6(
    a: Flow<A>, b: Flow<B>, c: Flow<C>, d: Flow<D>, e: Flow<E>, f: Flow<F>,
    transform: suspend (A, B, C, D, E, F) -> R,
): Flow<R> = combine(a, b, c, d, e, f) { v -> transform(v[0] as A, v[1] as B, v[2] as C, v[3] as D, v[4] as E, v[5] as F) }
