package dev.bastian.lupa.domain

import kotlinx.coroutines.flow.Flow

/** The apps found by one analysis. All installed apps are stored; the system-apps setting is applied when reading. */
data class Snapshot(val id: Long, val takenAt: Long, val apps: List<InstalledApp>)

data class SnapshotHeader(val id: Long, val takenAt: Long, val appCount: Int)

interface SnapshotRepository {
    /** Newest first. */
    val headers: Flow<List<SnapshotHeader>>

    /** Stores a snapshot and drops the oldest ones beyond [MAX_SNAPSHOTS]. Returns the new id. */
    suspend fun save(takenAt: Long, apps: List<InstalledApp>): Long

    suspend fun load(id: Long): Snapshot?

    suspend fun latest(): Snapshot?

    companion object {
        const val MAX_SNAPSHOTS = 20
    }
}

fun List<InstalledApp>.visible(includeSystemApps: Boolean): List<InstalledApp> =
    if (includeSystemApps) this else filterNot { it.isSystem }

data class AppUpdate(val before: InstalledApp, val after: InstalledApp)

/** One permission of one app that changed between two snapshots. */
data class PermissionChange(
    val packageName: String,
    val label: String,
    val permission: String,
    /** Whether the permission is granted in the newer snapshot. */
    val granted: Boolean,
) {
    val shortName: String get() = permission.substringAfterLast('.')
}

data class SnapshotChanges(
    val newApps: List<InstalledApp>,
    val removedApps: List<InstalledApp>,
    val updatedApps: List<AppUpdate>,
    /** Permissions an app asks for now and did not before (granted or not). */
    val newPermissions: List<PermissionChange>,
    /** Permissions that were asked for but denied before and are granted now. */
    val newlyGranted: List<PermissionChange>,
    /** Permissions that were granted before and are denied now. */
    val revoked: List<PermissionChange>,
) {
    val isEmpty: Boolean
        get() = newApps.isEmpty() && removedApps.isEmpty() && updatedApps.isEmpty() &&
            newPermissions.isEmpty() && newlyGranted.isEmpty() && revoked.isEmpty()
}

/** Pure comparison of two snapshots of the installed apps. */
object SnapshotDiff {
    fun compare(older: List<InstalledApp>, newer: List<InstalledApp>): SnapshotChanges {
        val before = older.associateBy { it.packageName }
        val after = newer.associateBy { it.packageName }

        val newApps = newer.filter { it.packageName !in before }.sortedBy { it.label.lowercase() }
        val removedApps = older.filter { it.packageName !in after }.sortedBy { it.label.lowercase() }

        val updated = mutableListOf<AppUpdate>()
        val newPermissions = mutableListOf<PermissionChange>()
        val newlyGranted = mutableListOf<PermissionChange>()
        val revoked = mutableListOf<PermissionChange>()

        newer.sortedBy { it.label.lowercase() }.forEach { current ->
            val previous = before[current.packageName] ?: return@forEach
            if (previous.versionCode != current.versionCode || previous.lastUpdateTime != current.lastUpdateTime) {
                updated += AppUpdate(previous, current)
            }
            val previousByName = previous.permissions.associateBy { it.name }
            current.permissions.forEach { permission ->
                val old = previousByName[permission.name]
                val change = PermissionChange(current.packageName, current.label, permission.name, permission.granted)
                when {
                    old == null -> newPermissions += change
                    !old.granted && permission.granted -> newlyGranted += change
                    old.granted && !permission.granted -> revoked += change
                }
            }
        }
        return SnapshotChanges(newApps, removedApps, updated, newPermissions, newlyGranted, revoked)
    }
}
