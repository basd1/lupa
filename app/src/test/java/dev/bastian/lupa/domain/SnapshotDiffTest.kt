package dev.bastian.lupa.domain

import dev.bastian.lupa.app
import dev.bastian.lupa.granted
import dev.bastian.lupa.requested
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotDiffTest {
    private val camera = "android.permission.CAMERA"
    private val mic = "android.permission.RECORD_AUDIO"

    @Test
    fun identicalSnapshotsHaveNoChanges() {
        val apps = listOf(app("a", permissions = listOf(granted(camera))), app("b"))
        assertTrue(SnapshotDiff.compare(apps, apps).isEmpty)
    }

    @Test
    fun anAppOnlyInTheNewerSnapshotIsNew() {
        val changes = SnapshotDiff.compare(listOf(app("a")), listOf(app("a"), app("b", "Bee")))
        assertEquals(listOf("b"), changes.newApps.map { it.packageName })
        assertTrue(changes.removedApps.isEmpty())
        assertTrue(changes.updatedApps.isEmpty())
    }

    @Test
    fun anAppOnlyInTheOlderSnapshotIsRemoved() {
        val changes = SnapshotDiff.compare(listOf(app("a"), app("b")), listOf(app("a")))
        assertEquals(listOf("b"), changes.removedApps.map { it.packageName })
        assertTrue(changes.newApps.isEmpty())
    }

    @Test
    fun aNewVersionOrANewUpdateTimeMakesTheAppUpdated() {
        val older = listOf(app("a", versionCode = 1), app("b", lastUpdateTime = 5), app("c"))
        val newer = listOf(app("a", versionCode = 2), app("b", lastUpdateTime = 9), app("c"))
        val changes = SnapshotDiff.compare(older, newer)
        assertEquals(listOf("a", "b"), changes.updatedApps.map { it.after.packageName })
        assertEquals(1L, changes.updatedApps.first().before.versionCode)
        assertEquals(2L, changes.updatedApps.first().after.versionCode)
    }

    @Test
    fun aPermissionAskedForOnlyNowIsNewWhetherGrantedOrNot() {
        val older = listOf(app("a"))
        val newer = listOf(app("a", permissions = listOf(granted(camera), requested(mic))))
        val changes = SnapshotDiff.compare(older, newer)
        assertEquals(listOf(camera to true, mic to false), changes.newPermissions.map { it.permission to it.granted })
        assertTrue(changes.newlyGranted.isEmpty())
        assertTrue(changes.revoked.isEmpty())
    }

    @Test
    fun aPermissionGrantedAfterBeingDeniedIsNewlyGranted() {
        val older = listOf(app("a", permissions = listOf(requested(camera))))
        val newer = listOf(app("a", permissions = listOf(granted(camera))))
        val changes = SnapshotDiff.compare(older, newer)
        assertEquals(listOf(camera), changes.newlyGranted.map { it.permission })
        assertEquals("CAMERA", changes.newlyGranted.single().shortName)
        assertTrue(changes.newPermissions.isEmpty())
        assertTrue(changes.revoked.isEmpty())
    }

    @Test
    fun aPermissionDeniedAfterBeingGrantedIsRevoked() {
        val older = listOf(app("a", permissions = listOf(granted(camera), granted(mic))))
        val newer = listOf(app("a", permissions = listOf(requested(camera), granted(mic))))
        val changes = SnapshotDiff.compare(older, newer)
        assertEquals(listOf(camera), changes.revoked.map { it.permission })
        assertTrue(changes.newlyGranted.isEmpty())
    }

    @Test
    fun permissionsOfNewOrRemovedAppsAreNotReportedAsPermissionChanges() {
        val changes = SnapshotDiff.compare(
            listOf(app("gone", permissions = listOf(granted(camera)))),
            listOf(app("fresh", permissions = listOf(granted(mic)))),
        )
        assertTrue(changes.newPermissions.isEmpty())
        assertTrue(changes.newlyGranted.isEmpty())
        assertTrue(changes.revoked.isEmpty())
        assertEquals(1, changes.newApps.size)
        assertEquals(1, changes.removedApps.size)
    }

    @Test
    fun aPermissionNoLongerAskedForIsNotACategoryOfChange() {
        val older = listOf(app("a", permissions = listOf(granted(camera), granted(mic))))
        val newer = listOf(app("a", permissions = listOf(granted(camera))))
        assertTrue(SnapshotDiff.compare(older, newer).isEmpty)
    }

    @Test
    fun allSixKindsCanHappenAtOnceAndStayApart() {
        val older = listOf(
            app("gone"),
            app("up", versionCode = 1),
            app("asked", permissions = listOf(requested(camera))),
            app("lost", permissions = listOf(granted(camera))),
            app("extra"),
        )
        val newer = listOf(
            app("fresh"),
            app("up", versionCode = 2),
            app("asked", permissions = listOf(granted(camera))),
            app("lost", permissions = listOf(requested(camera))),
            app("extra", permissions = listOf(granted(mic))),
        )
        val changes = SnapshotDiff.compare(older, newer)
        assertEquals(listOf("fresh"), changes.newApps.map { it.packageName })
        assertEquals(listOf("gone"), changes.removedApps.map { it.packageName })
        assertEquals(listOf("up"), changes.updatedApps.map { it.after.packageName })
        assertEquals(listOf("extra"), changes.newPermissions.map { it.packageName })
        assertEquals(listOf("asked"), changes.newlyGranted.map { it.packageName })
        assertEquals(listOf("lost"), changes.revoked.map { it.packageName })
    }

    @Test
    fun visibleHidesSystemAppsUnlessIncluded() {
        val apps = listOf(app("user"), app("sys", isSystem = true))
        assertEquals(listOf("user"), apps.visible(includeSystemApps = false).map { it.packageName })
        assertEquals(2, apps.visible(includeSystemApps = true).size)
    }
}
