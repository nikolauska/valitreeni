package app.fittracker

import android.app.Application
import app.fittracker.data.FitDatabase
import app.fittracker.data.FitRepository
import app.fittracker.data.SettingsStore
import app.fittracker.reminders.Notifications
import app.fittracker.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the app's single instances; small enough that a DI framework would only add ceremony. */
class FitApp : Application() {
    val database by lazy { FitDatabase.create(this) }
    val repository by lazy { FitRepository(database.dao()) }
    val settings by lazy { SettingsStore(this) }
    val scheduler by lazy { ReminderScheduler(this, settings) }

    /** For work that must outlive any one screen, such as re-arming alarms after a settings change. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        appScope.launch { scheduler.checkMissedAndReschedule() }
    }
}
