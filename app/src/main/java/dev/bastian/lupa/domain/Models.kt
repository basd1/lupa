package dev.bastian.lupa.domain

import kotlinx.serialization.Serializable

/** How much the system protects a permission, reduced to what this app needs to tell apart. */
@Serializable
enum class ProtectionLevel { NORMAL, DANGEROUS, SIGNATURE, OTHER, UNKNOWN }

@Serializable
data class RequestedPermission(
    val name: String,
    val granted: Boolean,
    val protection: ProtectionLevel,
)

@Serializable
data class ExportedComponents(
    val activities: Int = 0,
    val services: Int = 0,
    val receivers: Int = 0,
    val providers: Int = 0,
) {
    val total: Int get() = activities + services + receivers + providers
}

/** Everything Lupa knows about one installed app at the moment of a scan. */
@Serializable
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val targetSdk: Int,
    val minSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    /** Package that installed this app (a store or the package installer); null when unknown, such as `adb install`. */
    val installer: String?,
    val permissions: List<RequestedPermission>,
    val exported: ExportedComponents,
    val declaresAccessibilityService: Boolean = false,
    val declaresDeviceAdmin: Boolean = false,
)

/** An app that could not be read during a scan; the scan goes on without it. */
data class ScanFailure(val packageName: String, val reason: String)

data class ScanProgress(val done: Int, val total: Int)

data class ScanResult(val apps: List<InstalledApp>, val failures: List<ScanFailure>)

/** Where the installed apps come from. The real one asks PackageManager; tests use a fake. */
interface InstalledAppsSource {
    /** Reads every installed app, reporting progress. An app that fails to be read is listed in the result. */
    suspend fun scan(onProgress: (ScanProgress) -> Unit = {}): ScanResult
}
