package app.fittracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val reminderTimes: List<LocalTime>,
    val weekends: Boolean,
    val paused: Boolean,
    /** When the currently armed reminder alarm should fire. */
    val nextReminderAt: Instant?,
    val lastReminderFiredAt: Instant?,
    /** A reminder that should have fired but didn't - most likely blocked by battery saving. */
    val missedReminderAt: Instant?,
    /** Whether opening the app asks GitHub for a newer version; the only time the app goes online. */
    val autoUpdateCheck: Boolean,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val times = stringSetPreferencesKey("reminder_times")
        val weekends = booleanPreferencesKey("weekends")
        val paused = booleanPreferencesKey("paused")
        val nextAt = longPreferencesKey("next_reminder_at")
        val firedAt = longPreferencesKey("last_reminder_fired_at")
        val missedAt = longPreferencesKey("missed_reminder_at")
        val autoUpdate = booleanPreferencesKey("auto_update_check")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            reminderTimes = (p[Keys.times] ?: DEFAULT_TIMES).map(LocalTime::parse).sorted(),
            weekends = p[Keys.weekends] ?: true,
            paused = p[Keys.paused] ?: false,
            nextReminderAt = p[Keys.nextAt]?.let(Instant::ofEpochMilli),
            lastReminderFiredAt = p[Keys.firedAt]?.let(Instant::ofEpochMilli),
            missedReminderAt = p[Keys.missedAt]?.let(Instant::ofEpochMilli),
            autoUpdateCheck = p[Keys.autoUpdate] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setReminderTimes(times: List<LocalTime>) = context.dataStore.edit { p ->
        p[Keys.times] = times.map { it.withSecond(0).withNano(0).toString() }.toSet()
    }

    suspend fun setWeekends(enabled: Boolean) = context.dataStore.edit { it[Keys.weekends] = enabled }

    suspend fun setAutoUpdateCheck(enabled: Boolean) = context.dataStore.edit { it[Keys.autoUpdate] = enabled }

    suspend fun setPaused(paused: Boolean) = context.dataStore.edit { it[Keys.paused] = paused }

    suspend fun setNextReminderAt(at: Instant?) = context.dataStore.edit { p ->
        if (at == null) p.remove(Keys.nextAt) else p[Keys.nextAt] = at.toEpochMilli()
    }

    suspend fun markReminderFired(at: Instant) = context.dataStore.edit { p ->
        p[Keys.firedAt] = at.toEpochMilli()
        p.remove(Keys.missedAt)
    }

    suspend fun setMissedReminderAt(at: Instant?) = context.dataStore.edit { p ->
        if (at == null) p.remove(Keys.missedAt) else p[Keys.missedAt] = at.toEpochMilli()
    }

    companion object {
        val DEFAULT_TIMES = setOf("08:30", "11:00", "13:30", "17:00", "20:00")
    }
}
