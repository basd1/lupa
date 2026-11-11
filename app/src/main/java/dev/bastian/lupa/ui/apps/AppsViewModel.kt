package dev.bastian.lupa.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.lupa.domain.AppAssessment
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.AppFilter
import dev.bastian.lupa.domain.AppFilterStore
import dev.bastian.lupa.domain.AppKind
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.domain.RiskLevel
import dev.bastian.lupa.domain.ScanProgress
import dev.bastian.lupa.domain.SettingsStore
import dev.bastian.lupa.domain.SortOrder
import dev.bastian.lupa.domain.filtered
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppsUiState(
    val filter: AppFilter = AppFilter(),
    /** The kind chips only make sense when system apps are part of the analysis. */
    val systemAppsIncluded: Boolean = false,
    val apps: List<AppAssessment> = emptyList(),
    /** Apps in the analysis before search and filters, to tell "no apps yet" from "nothing matches". */
    val totalApps: Int = 0,
    val progress: ScanProgress? = null,
    val failedApps: Int = 0,
    val error: String? = null,
)

class AppsViewModel(
    private val catalog: AppCatalog,
    private val filterStore: AppFilterStore,
    settings: SettingsStore,
    dispatchers: AppDispatchers,
) : ViewModel() {

    val state: StateFlow<AppsUiState> = combine(
        catalog.visibleApps,
        catalog.state,
        settings.settings,
        filterStore.filter,
    ) { visible, catalogState, current, filter ->
        // System apps that are left out of the analysis cannot be filtered in.
        val effective = if (current.includeSystemApps) filter else filter.copy(kind = AppKind.ALL)
        AppsUiState(
            filter = effective,
            systemAppsIncluded = current.includeSystemApps,
            apps = visible.filtered(effective),
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

    fun onQueryChange(text: String) = filterStore.update { it.copy(query = text) }

    fun onKindChange(value: AppKind) = filterStore.update { it.copy(kind = value) }

    fun onRiskChange(value: RiskLevel?) = filterStore.update { it.copy(risk = value) }

    fun onCategoryChange(value: PermissionCategory?) = filterStore.update { it.copy(category = value) }

    fun onOldTargetChange(value: Boolean) = filterStore.update { it.copy(oldTargetOnly = value) }

    fun onSortChange(value: SortOrder) = filterStore.update { it.copy(sort = value) }

    fun clearFilters() = filterStore.update { AppFilter(sort = it.sort) }

    fun refresh() {
        viewModelScope.launch { catalog.refresh() }
    }
}
