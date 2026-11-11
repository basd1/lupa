package dev.bastian.lupa.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.bastian.lupa.domain.Settings
import dev.bastian.lupa.domain.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettings(private val store: DataStore<Preferences>) : SettingsStore {
    private val includeSystem = booleanPreferencesKey("include_system_apps")
    private val periodic = booleanPreferencesKey("periodic_analysis")
    private val periodHours = intPreferencesKey("period_hours")
    private val notify = booleanPreferencesKey("notify_on_changes")

    override val settings: Flow<Settings> = store.data.map { it.toSettings() }

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[includeSystem] = next.includeSystemApps
            prefs[periodic] = next.periodicAnalysis
            prefs[periodHours] = next.periodHours
            prefs[notify] = next.notifyOnChanges
        }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            includeSystemApps = this[includeSystem] ?: defaults.includeSystemApps,
            periodicAnalysis = this[periodic] ?: defaults.periodicAnalysis,
            periodHours = this[periodHours] ?: defaults.periodHours,
            notifyOnChanges = this[notify] ?: defaults.notifyOnChanges,
        )
    }
}
