package app.fittracker

import android.app.Application
import app.fittracker.data.FitDatabase
import app.fittracker.data.FitRepository
import app.fittracker.data.SettingsStore

/** Holds the app's single instances; small enough that a DI framework would only add ceremony. */
class FitApp : Application() {
    val database by lazy { FitDatabase.create(this) }
    val repository by lazy { FitRepository(database.dao()) }
    val settings by lazy { SettingsStore(this) }
}
