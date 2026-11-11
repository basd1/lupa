package dev.bastian.lupa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.lupa.data.work.AnalysisScheduler
import dev.bastian.lupa.domain.Settings
import dev.bastian.lupa.domain.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
    private val scheduler: AnalysisScheduler,
) : ViewModel() {

    val state: StateFlow<Settings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setIncludeSystemApps(value: Boolean) = change { it.copy(includeSystemApps = value) }

    fun setPeriodicAnalysis(value: Boolean) = change { it.copy(periodicAnalysis = value) }

    fun setPeriodHours(hours: Int) = change { it.copy(periodHours = hours) }

    fun setNotifyOnChanges(value: Boolean) = change { it.copy(notifyOnChanges = value) }

    /** Saves the change and makes the background schedule follow it. */
    private fun change(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            store.update(transform)
            scheduler.apply(store.settings.first())
        }
    }
}
