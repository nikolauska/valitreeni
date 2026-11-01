package app.fittracker.ui.history

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.data.HistoryData
import app.fittracker.domain.DayStatus
import app.fittracker.domain.Goals
import app.fittracker.domain.Streak
import app.fittracker.ui.FitCard
import app.fittracker.ui.currentLocale
import app.fittracker.ui.exerciseName
import app.fittracker.ui.theme.brand
import app.fittracker.ui.totalAmount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Loaded once for the screen; a year and a bit covers paging back and long streaks. */
private const val HISTORY_DAYS = 400L

/** Five weeks fit on one screen and always show the last month's worth, whatever the date. */
private const val GRID_WEEKS = 5L

data class HistoryUiState(val today: LocalDate, val data: HistoryData, val streak: Int, val best: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(app: FitApp) : ViewModel() {
    private val repo = app.repository

    // Follows the date like Today does, so a screen left open overnight isn't a day behind.
    val state: StateFlow<HistoryUiState?> = repo.observeToday().flatMapLatest { today ->
        repo.observeHistory(today.minusDays(HISTORY_DAYS), today).map {
            HistoryUiState(today, it, Streak.length(it.days, today), Streak.best(it.days, today))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HistoryScreen() {
    val app = LocalContext.current.applicationContext as FitApp
    val vm: HistoryViewModel = viewModel { HistoryViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            item { StreakCard(s.streak, s.best) }
            item { GoalGrid(s) }
            item { WeekChart(s) }
        }
    }
}

@Composable
private fun StreakCard(streak: Int, best: Int) {
    val brand = MaterialTheme.brand
    Surface(color = brand.hero, contentColor = brand.onHero, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp)) {
            StreakNumber(stringResource(R.string.history_streak_label), streak, Modifier.weight(1f))
            StreakNumber(stringResource(R.string.history_best_label), best, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StreakNumber(label: String, days: Int, modifier: Modifier) {
    val brand = MaterialTheme.brand
    Column(modifier) {
        Text(label.uppercase(currentLocale()), style = MaterialTheme.typography.labelMedium, color = brand.onHero.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(days.toString(), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(6.dp))
            Text(
                pluralStringResource(R.plurals.history_days_unit, days),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun GoalGrid(s: HistoryUiState) {
    val locale = currentLocale()
    val brand = MaterialTheme.brand
    // Pages are counted back from today rather than stored as dates, so midnight moves them along.
    var page by rememberSaveable { mutableIntStateOf(0) }
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val end = s.today.minusWeeks(GRID_WEEKS * page)
    val start = end.with(TemporalAdjusters.previousOrSame(firstDay)).minusWeeks(GRID_WEEKS - 1)
    val days = (0 until GRID_WEEKS * 7).map { start.plusDays(it) }
    val canGoBack = start.minusWeeks(GRID_WEEKS) >= s.today.minusDays(HISTORY_DAYS)
    val full = MaterialTheme.colorScheme.primary
    val some = full.copy(alpha = 0.3f).compositeOver(brand.track)
    val dayFormat = shortDate(locale)

    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NavRow(
                title = stringResource(R.string.history_goal_days),
                label = dayFormat.format(start) + " – " + dayFormat.format(minOf(start.plusDays(GRID_WEEKS * 7 - 1), s.today)),
                onPrev = if (canGoBack) ({ page++ }) else null,
                onNext = if (page > 0) ({ page-- }) else null,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0L until 7L).forEach {
                    Text(
                        firstDay.plus(it).getDisplayName(TextStyle.NARROW, locale),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { date ->
                        val status = Streak.status(s.data.days[date])
                        val future = date > s.today
                        val bg = when {
                            future -> Color.Transparent
                            status == DayStatus.ALL_GOALS -> full
                            status == DayStatus.SOME -> some
                            else -> brand.track
                        }
                        val shape = RoundedCornerShape(8.dp)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(shape).background(bg)
                                .then(if (date == s.today) Modifier.border(2.dp, brand.accent, shape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!future) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (status == DayStatus.ALL_GOALS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem(full, stringResource(R.string.history_legend_all))
                LegendItem(some, stringResource(R.string.history_legend_some))
                LegendItem(brand.track, stringResource(R.string.history_legend_none))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekChart(s: HistoryUiState) {
    val shown = s.data.exercises.filter { it.enabled || s.data.totals.values.any { day -> (day[it.id] ?: 0) > 0 } }
    // Default to the move logged most recently, which is usually the one worth looking at.
    val defaultId = s.data.totals.keys.sortedDescending().firstNotNullOfOrNull { s.data.totals[it]?.keys?.firstOrNull() }
        ?: shown.firstOrNull()?.id ?: return
    var selectedId by rememberSaveable { mutableLongStateOf(defaultId) }
    var weeksBack by rememberSaveable { mutableIntStateOf(0) }
    val exercise = shown.firstOrNull { it.id == selectedId } ?: shown.first()
    val weekEnd = s.today.minusWeeks(weeksBack.toLong())
    val days = (6L downTo 0L).map { weekEnd.minusDays(it) }
    val values = days.map { s.data.totals[it]?.get(exercise.id) ?: 0 }
    val goals = days.map { Goals.goalFor(exercise.id, it, s.data.goalChanges) ?: 0 }
    val locale = currentLocale()
    val dayFormat = shortDate(locale)

    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NavRow(
                title = exerciseName(exercise),
                label = dayFormat.format(days.first()) + " – " + dayFormat.format(days.last()),
                onPrev = if (weekEnd.minusWeeks(1) > s.today.minusDays(HISTORY_DAYS)) ({ weeksBack++ }) else null,
                onNext = if (weeksBack > 0) ({ weeksBack-- }) else null,
            )
            // One swipeable row: with 13+ moves, wrapping chips would push the chart off screen.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { e ->
                    FilterChip(
                        selected = e.id == exercise.id,
                        onClick = { selectedId = e.id },
                        label = { Text(exerciseName(e)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            val summary = days.zip(values).joinToString { (d, v) -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} $v" }
            BarChart(values, goals, highlightLast = weeksBack == 0, modifier = Modifier.semantics { contentDescription = summary })
            Row(Modifier.fillMaxWidth()) {
                days.forEach {
                    Text(
                        it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(totalAmount(exercise.unit, values.sum()), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.history_week_total) +
                        (goals.last().takeIf { it > 0 }?.let { " · " + stringResource(R.string.history_goal_per_day, totalAmount(exercise.unit, it)) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Int>, goals: List<Int>, highlightLast: Boolean, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axis = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val max = (values + goals).maxOrNull()?.takeIf { it > 0 }?.times(1.15f) ?: 1f
        val slot = size.width / values.size
        val barWidth = slot * 0.6f
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        val radius = CornerRadius(5.dp.toPx())
        values.forEachIndexed { i, v ->
            val left = i * slot + (slot - barWidth) / 2f
            val h = size.height * v / max
            // Today's bar is lighter: the day isn't over yet.
            val color = if (highlightLast && i == values.lastIndex) bar.copy(alpha = 0.4f) else bar
            if (v > 0) drawRoundRect(color, Offset(left, size.height - h), Size(barWidth, h + radius.y), radius)
            val g = goals[i]
            if (g > 0) {
                val y = size.height - size.height * g / max
                drawLine(goalColor, Offset(i * slot, y), Offset((i + 1) * slot, y), strokeWidth = 2f, pathEffect = dash)
            }
        }
        // Drawn last so it also hides the bars' rounded bottoms, leaving only their tops rounded.
        drawRect(axis, Offset(0f, size.height - 1f), Size(size.width, 2f))
    }
}

/** Title on the left, the shown date range with paging arrows on the right. */
@Composable
private fun NavRow(title: String, label: String, onPrev: (() -> Unit)?, onNext: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.cd_earlier))
        }
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.cd_later))
        }
    }
}

private fun shortDate(locale: java.util.Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
