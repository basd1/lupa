package dev.bastian.lupa.domain

import kotlinx.coroutines.flow.Flow

data class Settings(
    /** System apps are left out of the list, the summary, the snapshots and the exports unless this is on. */
    val includeSystemApps: Boolean = false,
    val periodicAnalysis: Boolean = false,
    val periodHours: Int = 24,
    val notifyOnChanges: Boolean = true,
) {
    companion object {
        val PERIOD_OPTIONS_HOURS = listOf(12, 24, 48, 168)
    }
}

interface SettingsStore {
    val settings: Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
}
