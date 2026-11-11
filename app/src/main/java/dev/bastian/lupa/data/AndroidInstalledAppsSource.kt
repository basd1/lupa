package dev.bastian.lupa.data

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Trace
import android.view.accessibility.AccessibilityManager
import androidx.core.content.pm.PackageInfoCompat
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.ExportedComponents
import dev.bastian.lupa.domain.InstalledApp
import dev.bastian.lupa.domain.InstalledAppsSource
import dev.bastian.lupa.domain.ProtectionLevel
import dev.bastian.lupa.domain.RequestedPermission
import dev.bastian.lupa.domain.ScanFailure
import dev.bastian.lupa.domain.ScanProgress
import dev.bastian.lupa.domain.ScanResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Reads the installed apps from PackageManager. The package names are listed first and each app is then read on
 * its own, so one app that throws (a package removed mid-scan, a broken manifest) costs that app and nothing else.
 */
class AndroidInstalledAppsSource(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : InstalledAppsSource {

    private val pm: PackageManager = context.packageManager
    private val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    override suspend fun scan(onProgress: (ScanProgress) -> Unit): ScanResult = withContext(dispatchers.io) {
        // The whole scan runs on this thread without suspending, so one trace section covers it; the benchmarks time it.
        Trace.beginSection(TRACE_SECTION)
        try {
            scanAll(onProgress)
        } finally {
            Trace.endSection()
        }
    }

    private suspend fun scanAll(onProgress: (ScanProgress) -> Unit): ScanResult {
        val names = installedPackageNames()
        val accessibilityPackages = accessibilityServicePackages()
        // The same permission is declared by many apps; its protection level is asked once.
        val protectionCache = HashMap<String, ProtectionLevel>()
        val apps = ArrayList<InstalledApp>(names.size)
        val failures = ArrayList<ScanFailure>()
        onProgress(ScanProgress(0, names.size))
        names.forEachIndexed { index, name ->
            currentCoroutineContext().ensureActive()
            try {
                apps += readApp(name, accessibilityPackages, protectionCache)
            } catch (e: Exception) {
                // Deliberate: an app that cannot be read is reported in the result instead of failing the scan.
                failures += ScanFailure(name, e.message ?: e.javaClass.simpleName)
            }
            if ((index + 1) % PROGRESS_STEP == 0 || index + 1 == names.size) {
                onProgress(ScanProgress(index + 1, names.size))
            }
        }
        return ScanResult(apps, failures)
    }

    private fun installedPackageNames(): List<String> {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        return packages.map { it.packageName }
    }

    private fun accessibilityServicePackages(): Set<String> {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.installedAccessibilityServiceList.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }.toSet()
    }

    private fun readApp(
        name: String,
        accessibilityPackages: Set<String>,
        protectionCache: MutableMap<String, ProtectionLevel>,
    ): InstalledApp {
        val info = packageInfo(name)
        val app = info.applicationInfo ?: error("No application info")
        val requested = info.requestedPermissions.orEmpty()
        val flags = info.requestedPermissionsFlags
        val permissions = requested.mapIndexed { index, permission ->
            val flagGranted = flags != null && index < flags.size &&
                flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            RequestedPermission(
                name = permission,
                granted = isGranted(permission, app.uid, name, flagGranted),
                protection = protectionCache.getOrPut(permission) { protectionOf(permission) },
            )
        }
        return InstalledApp(
            packageName = name,
            label = pm.getApplicationLabel(app).toString(),
            versionName = info.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            targetSdk = app.targetSdkVersion,
            minSdk = app.minSdkVersion,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            installer = installerOf(name),
            permissions = permissions,
            exported = ExportedComponents(
                activities = info.activities.countExported(),
                services = info.services.countExported(),
                receivers = info.receivers.countExported(),
                providers = info.providers.orEmpty().count { it.exported },
            ),
            declaresAccessibilityService = name in accessibilityPackages,
            declaresDeviceAdmin = info.receivers.orEmpty().any { it.permission == BIND_DEVICE_ADMIN },
        )
    }

    private fun packageInfo(name: String): PackageInfo {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(name, flags)
        }
    }

    private fun installerOf(name: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(name).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(name)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        // The app disappeared between listing and reading; an unknown source is the honest answer.
        null
    }

    /**
     * Permissions that Android manages as app operations (overlay, install packages, all files, usage access)
     * do not show up reliably in the granted flags, so their current mode is read instead.
     */
    private fun isGranted(permission: String, uid: Int, packageName: String, flagGranted: Boolean): Boolean {
        if (permission !in APP_OP_PERMISSIONS) return flagGranted
        val op = AppOpsManager.permissionToOp(permission) ?: return flagGranted
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(op, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(op, uid, packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: SecurityException) {
            // Reading another app's operation mode can be refused on some builds; fall back to the flag.
            flagGranted
        }
    }

    private fun protectionOf(permission: String): ProtectionLevel = try {
        val info = pm.getPermissionInfo(permission, 0)
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.protection
        } else {
            @Suppress("DEPRECATION")
            info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        }
        when (base) {
            PermissionInfo.PROTECTION_NORMAL -> ProtectionLevel.NORMAL
            PermissionInfo.PROTECTION_DANGEROUS -> ProtectionLevel.DANGEROUS
            PermissionInfo.PROTECTION_SIGNATURE -> ProtectionLevel.SIGNATURE
            else -> ProtectionLevel.OTHER
        }
    } catch (e: PackageManager.NameNotFoundException) {
        // A permission defined by an app that is not installed: nothing to ask.
        ProtectionLevel.UNKNOWN
    }

    private fun Array<ActivityInfo>?.countExported(): Int = this.orEmpty().count { it.exported }

    @JvmName("countExportedServices")
    private fun Array<android.content.pm.ServiceInfo>?.countExported(): Int = this.orEmpty().count { it.exported }

    private companion object {
        const val PROGRESS_STEP = 10
        const val TRACE_SECTION = "lupa.scan"
        const val BIND_DEVICE_ADMIN = "android.permission.BIND_DEVICE_ADMIN"
        val APP_OP_PERMISSIONS = setOf(
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.PACKAGE_USAGE_STATS",
        )
    }
}
