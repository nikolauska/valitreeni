package app.fittracker.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.fittracker.data.SettingsStore
import app.fittracker.domain.ReminderSchedule
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

class ReminderScheduler(private val context: Context, private val settings: SettingsStore) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Arms the alarm for the next reminder time, or cancels it when paused or no times are set. */
    suspend fun reschedule(now: ZonedDateTime = ZonedDateTime.now()) {
        val s = settings.current()
        val next = if (s.paused) null else ReminderSchedule.next(now, s.reminderTimes, s.weekends)
        val intent = alarmIntent(snooze = false)
        if (next == null) {
            alarms.cancel(intent)
            settings.setNextReminderAt(null)
        } else {
            setAlarm(next.toInstant(), intent)
            settings.setNextReminderAt(next.toInstant())
        }
    }

    /**
     * Run when the app starts: if the armed reminder's time passed long ago without the alarm firing,
     * battery saving most likely blocked it, so remember that for the warning banner. Then re-arm.
     * Not used after a reboot, because a phone that was off didn't "miss" anything.
     */
    suspend fun checkMissedAndReschedule(now: ZonedDateTime = ZonedDateTime.now()) {
        val s = settings.current()
        if (ReminderSchedule.isMissed(now.toInstant(), s.nextReminderAt, s.lastReminderFiredAt)) {
            settings.setMissedReminderAt(s.nextReminderAt)
        }
        reschedule(now)
    }

    fun snooze(minutes: Long = SNOOZE_MINUTES) {
        val at = Instant.now().plus(Duration.ofMinutes(minutes))
        setAlarm(at, alarmIntent(snooze = true))
    }

    // Lint only knows SCHEDULE_EXACT_ALARM; USE_EXACT_ALARM is declared and the call is guarded below.
    @SuppressLint("MissingPermission")
    private fun setAlarm(at: Instant, intent: PendingIntent) {
        // Inexact alarms got a 1-hour delivery window on Android 16, far too loose for clock-time
        // reminders. USE_EXACT_ALARM is granted at install for this sideloaded app; the inexact call
        // is only a fallback in case the system ever revokes exact alarms.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    private fun alarmIntent(snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            if (snooze) REQUEST_SNOOZE else REQUEST_MAIN,
            Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val SNOOZE_MINUTES = 15L
        private const val REQUEST_MAIN = 1
        private const val REQUEST_SNOOZE = 2
    }
}
