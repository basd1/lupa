package dev.bastian.lupa

import dev.bastian.lupa.domain.Clock
import dev.bastian.lupa.domain.ExportedComponents
import dev.bastian.lupa.domain.InstalledApp
import dev.bastian.lupa.domain.InstalledAppsSource
import dev.bastian.lupa.domain.ProtectionLevel
import dev.bastian.lupa.domain.RequestedPermission
import dev.bastian.lupa.domain.ScanFailure
import dev.bastian.lupa.domain.ScanProgress
import dev.bastian.lupa.domain.ScanResult
import dev.bastian.lupa.domain.Settings
import dev.bastian.lupa.domain.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import dev.bastian.lupa.domain.AnalysisOutcome
import dev.bastian.lupa.domain.ChangeNotifier
import dev.bastian.lupa.domain.Snapshot
import dev.bastian.lupa.domain.SnapshotHeader
import dev.bastian.lupa.domain.SnapshotRepository

fun app(
    packageName: String,
    label: String = packageName,
    isSystem: Boolean = false,
    targetSdk: Int = 34,
    installer: String? = "com.android.vending",
    permissions: List<RequestedPermission> = emptyList(),
    declaresAccessibilityService: Boolean = false,
    declaresDeviceAdmin: Boolean = false,
    versionCode: Long = 1,
    lastUpdateTime: Long = 1_000,
) = InstalledApp(
    packageName = packageName,
    label = label,
    versionName = "1.0",
    versionCode = versionCode,
    isSystem = isSystem,
    targetSdk = targetSdk,
    minSdk = 26,
    firstInstallTime = 1_000,
    lastUpdateTime = lastUpdateTime,
    installer = installer,
    permissions = permissions,
    exported = ExportedComponents(),
    declaresAccessibilityService = declaresAccessibilityService,
    declaresDeviceAdmin = declaresDeviceAdmin,
)

fun granted(name: String, protection: ProtectionLevel = ProtectionLevel.DANGEROUS) =
    RequestedPermission(name, granted = true, protection = protection)

fun requested(name: String, protection: ProtectionLevel = ProtectionLevel.DANGEROUS) =
    RequestedPermission(name, granted = false, protection = protection)

class FakeAppsSource(var apps: List<InstalledApp> = emptyList()) : InstalledAppsSource {
    var failures: List<ScanFailure> = emptyList()
    var error: Exception? = null
    var scans = 0

    override suspend fun scan(onProgress: (ScanProgress) -> Unit): ScanResult {
        scans++
        error?.let { throw it }
        onProgress(ScanProgress(apps.size, apps.size))
        return ScanResult(apps, failures)
    }
}

class FakeSettings(initial: Settings = Settings()) : SettingsStore {
    val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state
    override suspend fun update(transform: (Settings) -> Settings) {
        state.value = transform(state.value)
    }
}

class FakeClock(var time: Long = 10_000) : Clock {
    override fun now(): Long = time
}

class FakeSnapshots : SnapshotRepository {
    val stored = mutableListOf<Snapshot>()
    private val list = MutableStateFlow<List<SnapshotHeader>>(emptyList())
    override val headers: Flow<List<SnapshotHeader>> = list
    private var nextId = 1L

    override suspend fun save(takenAt: Long, apps: List<InstalledApp>): Long {
        val snapshot = Snapshot(nextId++, takenAt, apps)
        stored += snapshot
        stored.sortWith(compareByDescending<Snapshot> { it.takenAt }.thenByDescending { it.id })
        while (stored.size > SnapshotRepository.MAX_SNAPSHOTS) stored.removeAt(stored.lastIndex)
        list.value = stored.map { SnapshotHeader(it.id, it.takenAt, it.apps.size) }
        return snapshot.id
    }

    override suspend fun load(id: Long): Snapshot? = stored.firstOrNull { it.id == id }

    override suspend fun latest(): Snapshot? = stored.firstOrNull()
}

class FakeNotifier : ChangeNotifier {
    val outcomes = mutableListOf<AnalysisOutcome>()
    override fun notifyChanges(outcome: AnalysisOutcome) {
        outcomes += outcome
    }
}
