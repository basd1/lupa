package dev.bastian.lupa.ui.changes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.lupa.domain.AnalysisResult
import dev.bastian.lupa.domain.AnalysisRunner
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.SettingsStore
import dev.bastian.lupa.domain.SnapshotChanges
import dev.bastian.lupa.domain.SnapshotDiff
import dev.bastian.lupa.domain.SnapshotHeader
import dev.bastian.lupa.domain.SnapshotRepository
import dev.bastian.lupa.domain.visible
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangesUiState(
    /** Newest first. */
    val headers: List<SnapshotHeader> = emptyList(),
    val older: SnapshotHeader? = null,
    val newer: SnapshotHeader? = null,
    /** Null when fewer than two snapshots exist or while the comparison is being computed. */
    val changes: SnapshotChanges? = null,
    val running: Boolean = false,
    val error: String? = null,
)

class ChangesViewModel(
    private val snapshots: SnapshotRepository,
    private val runner: AnalysisRunner,
    settings: SettingsStore,
    dispatchers: AppDispatchers,
) : ViewModel() {

    private data class Selection(val olderId: Long, val newerId: Long)

    private val selection = MutableStateFlow<Selection?>(null)
    private val running = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val comparison = combine(snapshots.headers, selection, settings.settings) { headers, picked, current ->
        Triple(headers, picked, current.includeSystemApps)
    }.mapLatest { (headers, picked, includeSystem) ->
        val pair = pick(headers, picked)
        val changes = pair?.let { (older, newer) ->
            val a = snapshots.load(older.id)
            val b = snapshots.load(newer.id)
            if (a != null && b != null) SnapshotDiff.compare(a.apps.visible(includeSystem), b.apps.visible(includeSystem)) else null
        }
        ChangesUiState(headers = headers, older = pair?.first, newer = pair?.second, changes = changes)
    }.flowOn(dispatchers.default)

    val state: StateFlow<ChangesUiState> = combine(comparison, running, error) { base, isRunning, message ->
        base.copy(running = isRunning, error = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChangesUiState())

    /** The picked pair when both still exist, otherwise the two newest snapshots. */
    private fun pick(headers: List<SnapshotHeader>, picked: Selection?): Pair<SnapshotHeader, SnapshotHeader>? {
        if (headers.size < 2) return null
        val older = headers.firstOrNull { it.id == picked?.olderId }
        val newer = headers.firstOrNull { it.id == picked?.newerId }
        if (older != null && newer != null && older.id != newer.id) {
            return if (older.takenAt <= newer.takenAt) older to newer else newer to older
        }
        return headers[1] to headers[0]
    }

    fun selectOlder(header: SnapshotHeader) = select(header, older = true)

    fun selectNewer(header: SnapshotHeader) = select(header, older = false)

    private fun select(header: SnapshotHeader, older: Boolean) {
        val current = state.value
        val olderId = if (older) header.id else current.older?.id ?: return
        val newerId = if (older) current.newer?.id ?: return else header.id
        selection.update { Selection(olderId, newerId) }
    }

    fun analyzeNow() {
        if (running.value) return
        viewModelScope.launch {
            running.value = true
            error.value = null
            when (val result = runner.run()) {
                is AnalysisResult.Failed -> error.value = result.reason
                is AnalysisResult.Done -> selection.value = null // show the newest comparison
            }
            running.value = false
        }
    }
}
