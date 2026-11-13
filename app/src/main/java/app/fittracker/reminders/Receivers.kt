package app.fittracker.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fittracker.FitApp
import app.fittracker.domain.LogSource
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as FitApp
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        runAsync {
            app.settings.markReminderFired(Instant.now())
            // A snooze is a one-off; the regular chain of reminders is already armed.
            if (!snooze) app.scheduler.reschedule()
            if (app.settings.current().paused) return@runAsync
            val repo = app.repository
            val today = repo.today()
            repo.ensureFocus(today)
            val day = repo.day(today)
            if (!day.round.isEmpty) Notifications.showRound(context, day)
        }
    }

    companion object {
        const val EXTRA_SNOOZE = "snooze"
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as FitApp
        Notifications.cancelRound(context)
        when (intent.action) {
            ACTION_DONE_ROUND -> {
                val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE)
                runAsync {
                    val repo = app.repository
                    val today = repo.today()
                    // Yesterday's reminder tapped after midnight must not count toward the new day.
                    if (day != today.toEpochDay()) return@runAsync
                    // Rebuilt now rather than when the reminder fired: sets added in the app since
                    // then would otherwise be logged a second time.
                    val round = repo.day(today).round
                    repo.logAll(round.items.map { it.exerciseId to it.amount }, LogSource.NOTIFICATION)
                }
            }
            ACTION_SNOOZE -> app.scheduler.snooze()
            ACTION_SKIP -> Unit
        }
    }

    companion object {
        const val ACTION_DONE_ROUND = "app.fittracker.DONE_ROUND"
        const val ACTION_SNOOZE = "app.fittracker.SNOOZE"
        const val ACTION_SKIP = "app.fittracker.SKIP"
        const val EXTRA_DAY = "day"
    }
}

/**
 * Alarms don't survive a reboot, and clock or time zone changes shift the next reminder. On Android 12,
 * allowing exact alarms again should swap the inexact fallback alarm for an exact one.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val app = context.applicationContext as FitApp
        runAsync { app.scheduler.reschedule() }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
