package app.fittracker.reminders

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
                val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: return
                val amounts = intent.getIntArrayExtra(EXTRA_AMOUNTS) ?: return
                runAsync { app.repository.logAll(ids.zip(amounts.toList()), LogSource.NOTIFICATION) }
            }
            ACTION_SNOOZE -> app.scheduler.snooze()
            ACTION_SKIP -> Unit
        }
    }

    companion object {
        const val ACTION_DONE_ROUND = "app.fittracker.DONE_ROUND"
        const val ACTION_SNOOZE = "app.fittracker.SNOOZE"
        const val ACTION_SKIP = "app.fittracker.SKIP"
        const val EXTRA_IDS = "ids"
        const val EXTRA_AMOUNTS = "amounts"
    }
}

/** Alarms don't survive a reboot, and clock or time zone changes shift the next reminder. */
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
        )
    }
}
