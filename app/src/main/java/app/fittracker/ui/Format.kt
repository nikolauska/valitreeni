package app.fittracker.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.fittracker.R
import app.fittracker.data.Exercise
import app.fittracker.domain.MoveUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
@ReadOnlyComposable
fun exerciseName(exercise: Exercise): String =
    exercise.builtIn?.let { stringResource(it.nameRes) } ?: exercise.customName.orEmpty()

/** "10", "45 s" or "10 / leg" for one set. */
@Composable
@ReadOnlyComposable
fun setAmount(exercise: Exercise, amount: Int = exercise.amountPerSet): String = when {
    exercise.unit == MoveUnit.SECONDS -> stringResource(R.string.amount_seconds, amount)
    exercise.builtIn?.perSide == true -> stringResource(R.string.amount_per_leg, amount)
    else -> amount.toString()
}

/** Same as [exerciseName] for places outside Compose, such as notifications. */
fun exerciseNameText(context: Context, exercise: Exercise): String =
    exercise.builtIn?.let { context.getString(it.nameRes) } ?: exercise.customName.orEmpty()

/** Same as [setAmount] for places outside Compose. */
fun setAmountText(context: Context, exercise: Exercise, amount: Int = exercise.amountPerSet): String = when {
    exercise.unit == MoveUnit.SECONDS -> context.getString(R.string.amount_seconds, amount)
    exercise.builtIn?.perSide == true -> context.getString(R.string.amount_per_leg, amount)
    else -> amount.toString()
}

/** Totals never say "per leg": the per-side count is what gets summed. */
@Composable
@ReadOnlyComposable
fun totalAmount(unit: MoveUnit, amount: Int): String =
    if (unit == MoveUnit.SECONDS) stringResource(R.string.amount_seconds, amount) else amount.toString()

/** Weekday and date in the user's language, e.g. "Sat, Oct 31" or "la 31. lokak.". */
fun formatDayLabel(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(date)

fun formatTime(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(ZoneId.systemDefault()))
