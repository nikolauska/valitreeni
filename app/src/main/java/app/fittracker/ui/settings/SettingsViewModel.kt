package app.fittracker.ui.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fittracker.FitApp
import app.fittracker.data.MovesView
import app.fittracker.data.Settings
import app.fittracker.domain.StartLevel
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val settings: Settings, val moves: MovesView)

/** Language choices offered in Settings; null tag means "follow the phone". */
enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), FINNISH("fi") }

class SettingsViewModel(private val app: FitApp) : ViewModel() {
    private val store = app.settings
    // Per-app language arrived in Android 13; older phones simply follow the phone's language.
    private val locales: LocaleManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) app.getSystemService(LocaleManager::class.java) else null

    val languageSupported: Boolean get() = locales != null

    val state: StateFlow<SettingsUiState?> =
        combine(store.settings, app.repository.observeMoves(app.repository.today())) { s, m -> SettingsUiState(s, m) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addTime(time: LocalTime) = updateTimes { (it + time).distinct() }

    fun removeTime(time: LocalTime) = updateTimes { it - time }

    fun replaceTime(old: LocalTime, new: LocalTime) = updateTimes { (it - old + new).distinct() }

    fun setWeekends(enabled: Boolean) = changeAndReschedule { store.setWeekends(enabled) }

    fun setPaused(paused: Boolean) = changeAndReschedule { store.setPaused(paused) }

    fun setAutoUpdateCheck(enabled: Boolean) {
        app.appScope.launch { store.setAutoUpdateCheck(enabled) }
    }

    fun setAvoidFloor(avoid: Boolean) {
        app.appScope.launch { store.setAvoidFloor(avoid) }
    }

    /** Resets every move to the level's version, the same as answering the first-run question again. */
    fun setStartLevel(level: StartLevel) {
        app.appScope.launch {
            store.setStartLevel(level)
            app.repository.applyStartLevel(level)
        }
    }

    fun language(): AppLanguage {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || locales == null) return AppLanguage.SYSTEM
        val tag = locales.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    /** Uses Android's per-app language, so the choice also shows in system settings and survives restarts. */
    fun setLanguage(language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || locales == null) return
        locales.applicationLocales = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }

    private fun updateTimes(change: (List<LocalTime>) -> List<LocalTime>) = changeAndReschedule {
        store.setReminderTimes(change(store.current().reminderTimes))
    }

    // Runs in the app scope: leaving the screen right after a change must not cancel re-arming the alarm.
    private fun changeAndReschedule(change: suspend () -> Unit) = app.appScope.launch {
        change()
        app.scheduler.reschedule()
    }.let { }
}
