package app.fittracker

import android.app.Application
import app.fittracker.data.FitDatabase
import app.fittracker.data.FitRepository
import app.fittracker.data.MoveCatalog
import app.fittracker.data.SettingsStore
import app.fittracker.reminders.Notifications
import app.fittracker.reminders.ReminderScheduler
import app.fittracker.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Holds the app's single instances; small enough that a DI framework would only add ceremony. */
class FitApp : Application() {
    val database by lazy { FitDatabase.create(this) }
    val repository by lazy {
        FitRepository(database.dao(), avoidFloor = settings.settings.map { it.avoidFloor }.distinctUntilChanged())
    }
    val settings by lazy { SettingsStore(this) }
    val scheduler by lazy { ReminderScheduler(this, settings) }
    val updater by lazy { Updater(this, settings, appScope) }

    /** For work that must outlive any one screen, such as re-arming alarms after a settings change. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        appScope.launch {
            // Before alarms are re-armed, so a reminder right after an update already lists placed moves.
            placeNewMoves()
            scheduler.checkMissedAndReschedule()
        }
    }

    /**
     * Runs once per catalog version. On a fresh install it changes nothing that matters, because the
     * first-run level question places every ladder anyway.
     */
    private suspend fun placeNewMoves() {
        val current = settings.current()
        if (current.catalogVersion >= MoveCatalog.VERSION) return
        repository.placeNewVersions(current.startLevel, current.catalogVersion)
        settings.setCatalogVersion(MoveCatalog.VERSION)
    }
}
