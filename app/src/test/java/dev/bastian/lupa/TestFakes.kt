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
