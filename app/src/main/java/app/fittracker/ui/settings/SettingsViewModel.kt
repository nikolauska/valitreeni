package app.fittracker.ui.settings

import android.app.LocaleManager
import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fittracker.FitApp
import app.fittracker.data.Exercise
import app.fittracker.data.Settings
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val settings: Settings, val exercises: List<Pair<Exercise, Int>>)

/** Language choices offered in Settings; null tag means "follow the phone". */
enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), FINNISH("fi") }

class SettingsViewModel(private val app: FitApp) : ViewModel() {
    private val store = app.settings
    private val locales = app.getSystemService(LocaleManager::class.java)

    val state: StateFlow<SettingsUiState?> =
        combine(store.settings, app.repository.observeExercisesWithGoals(app.repository.today())) { s, e -> SettingsUiState(s, e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addTime(time: LocalTime) = updateTimes { (it + time).distinct() }

    fun removeTime(time: LocalTime) = updateTimes { it - time }

    fun replaceTime(old: LocalTime, new: LocalTime) = updateTimes { (it - old + new).distinct() }

    fun setWeekends(enabled: Boolean) = changeAndReschedule { store.setWeekends(enabled) }

    fun setPaused(paused: Boolean) = changeAndReschedule { store.setPaused(paused) }

    fun setAutoUpdateCheck(enabled: Boolean) {
        app.appScope.launch { store.setAutoUpdateCheck(enabled) }
    }

    fun language(): AppLanguage {
        val tag = locales.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    /** Uses Android's per-app language, so the choice also shows in system settings and survives restarts. */
    fun setLanguage(language: AppLanguage) {
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
