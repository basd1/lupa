package dev.bastian.lupa.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.lupa.domain.AppAssessment
import dev.bastian.lupa.domain.AppCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [assessment] is null while the catalog is empty or when the app is gone since the last scan. */
data class AppDetailUiState(
    val assessment: AppAssessment? = null,
    val scanning: Boolean = true,
)

class AppDetailViewModel(
    private val packageName: String,
    private val catalog: AppCatalog,
) : ViewModel() {

    val state: StateFlow<AppDetailUiState> = catalog.state
        .map { catalogState ->
            AppDetailUiState(
                assessment = catalogState.apps.firstOrNull { it.app.packageName == packageName },
                scanning = catalogState.isScanning || catalogState.scannedAt == null,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState())

    init {
        // Opened from a notification or after process death: the catalog may not have been scanned yet.
        if (catalog.state.value.scannedAt == null) viewModelScope.launch { catalog.refresh() }
    }
}
