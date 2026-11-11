package dev.bastian.lupa.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.AppFilter
import dev.bastian.lupa.domain.AppFilterStore
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.domain.RiskLevel
import dev.bastian.lupa.domain.ScanProgress
import dev.bastian.lupa.domain.Summary
import dev.bastian.lupa.domain.SummaryCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SummaryUiState(
    /** Null until the first scan has produced something to count. */
    val summary: Summary? = null,
    val progress: ScanProgress? = null,
    val error: String? = null,
)

class SummaryViewModel(
    private val catalog: AppCatalog,
    private val filterStore: AppFilterStore,
    dispatchers: AppDispatchers,
) : ViewModel() {

    val state: StateFlow<SummaryUiState> = combine(catalog.visibleApps, catalog.state) { visible, catalogState ->
        SummaryUiState(
            summary = if (catalogState.scannedAt == null) null else SummaryCalculator.of(visible),
            progress = catalogState.progress,
            error = catalogState.error,
        )
    }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    init {
        if (catalog.state.value.scannedAt == null) refresh()
    }

    fun refresh() {
        viewModelScope.launch { catalog.refresh() }
    }

    /** Narrows the Apps list to one risk level. */
    fun showRisk(level: RiskLevel) = filterStore.show(AppFilter(risk = level))

    fun showOldTargets() = filterStore.show(AppFilter(oldTargetOnly = true))

    fun showCategory(category: PermissionCategory) = filterStore.show(AppFilter(category = category))
}
