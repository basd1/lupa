package dev.bastian.lupa.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.InstalledApp
import dev.bastian.lupa.domain.ScanProgress
import dev.bastian.lupa.domain.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppKind { ALL, USER, SYSTEM }

data class AppsUiState(
    val query: String = "",
    val kind: AppKind = AppKind.ALL,
    /** The filter chips only make sense when system apps are part of the analysis. */
    val systemAppsIncluded: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),
    /** Apps in the analysis before search and filters, to tell "no apps yet" from "nothing matches". */
    val totalApps: Int = 0,
    val progress: ScanProgress? = null,
    val failedApps: Int = 0,
    val error: String? = null,
)

class AppsViewModel(
    private val catalog: AppCatalog,
    settings: SettingsStore,
    dispatchers: AppDispatchers,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val kind = MutableStateFlow(AppKind.ALL)

    val state: StateFlow<AppsUiState> = combine(
        catalog.visibleApps,
        catalog.state,
        settings.settings,
        query,
        kind,
    ) { visible, catalogState, current, text, selectedKind ->
        val effectiveKind = if (current.includeSystemApps) selectedKind else AppKind.ALL
        AppsUiState(
            query = text,
            kind = effectiveKind,
            systemAppsIncluded = current.includeSystemApps,
            apps = visible.filterApps(text, effectiveKind),
            totalApps = visible.size,
            progress = catalogState.progress,
            failedApps = catalogState.failures.size,
            error = catalogState.error,
        )
    }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        if (catalog.state.value.scannedAt == null) refresh()
    }

    fun onQueryChange(text: String) = query.update { text }

    fun onKindChange(value: AppKind) = kind.update { value }

    fun refresh() {
        viewModelScope.launch { catalog.refresh() }
    }
}

internal fun List<InstalledApp>.filterApps(query: String, kind: AppKind): List<InstalledApp> {
    val needle = query.trim()
    return filter { app ->
        val kindMatches = when (kind) {
            AppKind.ALL -> true
            AppKind.USER -> !app.isSystem
            AppKind.SYSTEM -> app.isSystem
        }
        kindMatches && (needle.isEmpty() ||
            app.label.contains(needle, ignoreCase = true) ||
            app.packageName.contains(needle, ignoreCase = true))
    }.sortedBy { it.label.lowercase() }
}
