package dev.bastian.lupa.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.lupa.domain.AccessStatus
import dev.bastian.lupa.domain.AppAssessment
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.domain.hasAccess
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryUiState(
    val withAccess: List<AppAssessment> = emptyList(),
    val requestedOnly: List<AppAssessment> = emptyList(),
    val loaded: Boolean = false,
)

class CategoryViewModel(
    val category: PermissionCategory,
    private val catalog: AppCatalog,
    dispatchers: AppDispatchers,
) : ViewModel() {

    val state: StateFlow<CategoryUiState> = combine(catalog.visibleApps, catalog.state) { visible, catalogState ->
        val byRisk = compareByDescending<AppAssessment> { it.score.value }.thenBy { it.app.label.lowercase() }
        CategoryUiState(
            withAccess = visible.filter { it.hasAccess(category) }.sortedWith(byRisk),
            requestedOnly = visible.filter { it.permissions.status(category) == AccessStatus.REQUESTED }.sortedWith(byRisk),
            loaded = catalogState.scannedAt != null,
        )
    }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState())

    init {
        if (catalog.state.value.scannedAt == null) viewModelScope.launch { catalog.refresh() }
    }
}
