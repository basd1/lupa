package dev.bastian.lupa.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/** What the screens see of the last scan. [progress] is non-null while a scan is running. */
data class CatalogState(
    val apps: List<InstalledApp> = emptyList(),
    val scannedAt: Long? = null,
    val progress: ScanProgress? = null,
    val failures: List<ScanFailure> = emptyList(),
    val error: String? = null,
) {
    val isScanning: Boolean get() = progress != null
}

/** The latest scan held in memory and shared by every screen. */
class AppCatalog(
    private val source: InstalledAppsSource,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val mutableState = MutableStateFlow(CatalogState())
    private val scanLock = Mutex()

    val state: StateFlow<CatalogState> = mutableState

    /** The scanned apps with the "include system apps" setting applied. */
    val visibleApps: Flow<List<InstalledApp>> = combine(mutableState, settings.settings) { catalog, current ->
        if (current.includeSystemApps) catalog.apps else catalog.apps.filterNot { it.isSystem }
    }

    /** Scans again. A scan that is already running is not repeated; returns null in that case or on failure. */
    suspend fun refresh(): ScanResult? {
        if (!scanLock.tryLock()) return null
        try {
            mutableState.update { it.copy(progress = ScanProgress(0, 0), error = null) }
            val result = try {
                source.scan { progress -> mutableState.update { it.copy(progress = progress) } }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                mutableState.update { it.copy(progress = null, error = e.message ?: e.javaClass.simpleName) }
                return null
            }
            mutableState.value = CatalogState(
                apps = result.apps,
                scannedAt = clock.now(),
                failures = result.failures,
            )
            return result
        } finally {
            scanLock.unlock()
        }
    }
}
