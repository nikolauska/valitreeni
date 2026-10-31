@file:OptIn(ExperimentalMaterial3Api::class)

package app.fittracker.ui.history

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
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
import app.fittracker.data.Exercise
import app.fittracker.data.HistoryData
import app.fittracker.domain.DayStatus
import app.fittracker.domain.Goals
import app.fittracker.domain.Streak
import app.fittracker.ui.currentLocale
import app.fittracker.ui.exerciseName
import app.fittracker.ui.totalAmount
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Loaded once for the screen; a year and a bit covers the calendar's back-navigation and long streaks. */
private const val HISTORY_DAYS = 400L

data class HistoryUiState(val today: LocalDate, val data: HistoryData, val streak: Int)

class HistoryViewModel(app: FitApp) : ViewModel() {
    private val today = app.repository.today()
    val state: StateFlow<HistoryUiState?> = app.repository.observeHistory(today.minusDays(HISTORY_DAYS), today)
        .map { HistoryUiState(today, it, Streak.length(it.days, today)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HistoryScreen() {
    val app = LocalContext.current.applicationContext as FitApp
    val vm: HistoryViewModel = viewModel { HistoryViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StreakCard(s.streak) }
            item { WeekChart(s) }
            item { MonthCalendar(s) }
        }
    }
}

@Composable
private fun StreakCard(streak: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔥 $streak", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                pluralStringResource(R.plurals.history_streak, streak),
                Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun WeekChart(s: HistoryUiState) {
    val enabled = s.data.exercises.filter { it.enabled || s.data.totals.values.any { day -> (day[it.id] ?: 0) > 0 } }
    // Default to the move logged most recently, which is usually the one worth looking at.
    val defaultId = s.data.totals.keys.sortedDescending().firstNotNullOfOrNull { s.data.totals[it]?.keys?.firstOrNull() }
        ?: enabled.firstOrNull()?.id ?: return
    var selectedId by rememberSaveable { mutableLongStateOf(defaultId) }
    var weekEnd by rememberSaveable { mutableStateOf(s.today) }
    val exercise = enabled.firstOrNull { it.id == selectedId } ?: enabled.first()
    val days = (6L downTo 0L).map { weekEnd.minusDays(it) }
    val values = days.map { s.data.totals[it]?.get(exercise.id) ?: 0 }
    val goals = days.map { Goals.goalFor(exercise.id, it, s.data.goalChanges) ?: 0 }
    val locale = currentLocale()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_week), style = MaterialTheme.typography.titleMedium)
            // One swipeable row: with 13+ moves, wrapping chips would push the chart off screen.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                enabled.forEach { e ->
                    FilterChip(selected = e.id == exercise.id, onClick = { selectedId = e.id }, label = { Text(exerciseName(e)) })
                }
            }
            val summary = days.zip(values).joinToString { (d, v) -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} $v" }
            BarChart(values, goals, highlightLast = weekEnd == s.today, modifier = Modifier.semantics { contentDescription = summary })
            Row(Modifier.fillMaxWidth()) {
                days.forEach {
                    Text(
                        it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            val total = values.sum()
            Text(
                stringResource(R.string.history_week_total, exerciseName(exercise), totalAmount(exercise.unit, total)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val dayFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dM"), locale)
            NavRow(
                label = dayFormat.format(days.first()) + " – " + dayFormat.format(days.last()),
                onPrev = { weekEnd = weekEnd.minusWeeks(1) },
                onNext = if (weekEnd < s.today) ({ weekEnd = minOf(weekEnd.plusWeeks(1), s.today) }) else null,
            )
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
        values.forEachIndexed { i, v ->
            val left = i * slot + (slot - barWidth) / 2f
            val h = size.height * v / max
            // Today's bar is lighter: the day isn't over yet.
            val color = if (highlightLast && i == values.lastIndex) bar.copy(alpha = 0.45f) else bar
            if (v > 0) drawRect(color, Offset(left, size.height - h), Size(barWidth, h))
            val g = goals[i]
            if (g > 0) {
                val y = size.height - size.height * g / max
                drawLine(goalColor, Offset(i * slot, y), Offset((i + 1) * slot, y), strokeWidth = 2f, pathEffect = dash)
            }
        }
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
    }
}

@Composable
private fun MonthCalendar(s: HistoryUiState) {
    val locale = currentLocale()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(s.today)) }
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - firstDay.value + 7) % 7
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val full = MaterialTheme.colorScheme.primary
    // Plain surface stands out from the card, so days with nothing logged are still visible as cells.
    val empty = MaterialTheme.colorScheme.surface

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_goal_days), style = MaterialTheme.typography.titleMedium)
            NavRow(
                label = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year,
                onPrev = { month = month.minusMonths(1) },
                onNext = if (month < YearMonth.from(s.today)) ({ month = month.plusMonths(1) }) else null,
            )
            Row(Modifier.fillMaxWidth()) {
                (0L until 7L).forEach {
                    Text(
                        firstDay.plus(it).getDisplayName(TextStyle.NARROW, locale),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 7).forEach { i ->
                        val date = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val status = Streak.status(s.data.days[date])
                                val bg = when {
                                    date > s.today -> Color.Transparent
                                    status == DayStatus.ALL_GOALS -> full
                                    status == DayStatus.SOME -> full.copy(alpha = 0.35f)
                                    else -> empty
                                }
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(bg)
                                        .then(if (date == s.today) Modifier.border(2.dp, full, RoundedCornerShape(6.dp)) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (status == DayStatus.ALL_GOALS && date <= s.today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.history_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavRow(label: String, onPrev: () -> Unit, onNext: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text("‹", style = MaterialTheme.typography.titleLarge) }
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onNext?.invoke() }, enabled = onNext != null) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
}
