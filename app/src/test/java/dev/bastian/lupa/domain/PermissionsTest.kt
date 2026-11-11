package dev.bastian.lupa.domain

import dev.bastian.lupa.app
import dev.bastian.lupa.granted
import dev.bastian.lupa.requested
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PermissionsTest {
    private fun p(name: String) = "android.permission.$name"

    @Test
    fun dangerousPermissionsMapToTheirCategories() {
        mapOf(
            "ACCESS_FINE_LOCATION" to PermissionCategory.LOCATION,
            "ACCESS_BACKGROUND_LOCATION" to PermissionCategory.BACKGROUND_LOCATION,
            "CAMERA" to PermissionCategory.CAMERA,
            "RECORD_AUDIO" to PermissionCategory.MICROPHONE,
            "READ_CONTACTS" to PermissionCategory.CONTACTS,
            "WRITE_CALENDAR" to PermissionCategory.CALENDAR,
            "SEND_SMS" to PermissionCategory.SMS,
            "READ_CALL_LOG" to PermissionCategory.PHONE,
            "READ_MEDIA_IMAGES" to PermissionCategory.STORAGE_MEDIA,
            "BODY_SENSORS" to PermissionCategory.BODY_SENSORS,
            "ACTIVITY_RECOGNITION" to PermissionCategory.ACTIVITY,
            "BLUETOOTH_CONNECT" to PermissionCategory.NEARBY_DEVICES,
            "POST_NOTIFICATIONS" to PermissionCategory.NOTIFICATIONS,
            "SYSTEM_ALERT_WINDOW" to PermissionCategory.OVERLAY,
            "REQUEST_INSTALL_PACKAGES" to PermissionCategory.INSTALL_PACKAGES,
            "MANAGE_EXTERNAL_STORAGE" to PermissionCategory.ALL_FILES,
            "PACKAGE_USAGE_STATS" to PermissionCategory.USAGE_ACCESS,
        ).forEach { (name, category) ->
            assertEquals(category, PermissionCatalog.categoryOf(p(name)), name)
        }
    }

    @Test
    fun unknownAndNormalPermissionsHaveNoCategory() {
        assertNull(PermissionCatalog.categoryOf(p("INTERNET")))
        assertNull(PermissionCatalog.categoryOf("com.example.CUSTOM"))
        // Same suffix, different namespace: must not be mistaken for the platform permission.
        assertNull(PermissionCatalog.categoryOf("com.example.permission.CAMERA"))
    }

    @Test
    fun backgroundLocationIsSeparateFromForegroundLocation() {
        val permissions = categorize(
            app("a", permissions = listOf(granted(p("ACCESS_FINE_LOCATION")), requested(p("ACCESS_BACKGROUND_LOCATION")))),
        )
        assertEquals(AccessStatus.GRANTED, permissions.status(PermissionCategory.LOCATION))
        assertEquals(AccessStatus.REQUESTED, permissions.status(PermissionCategory.BACKGROUND_LOCATION))
        assertTrue(permissions.isActive(PermissionCategory.LOCATION))
        assertFalse(permissions.isActive(PermissionCategory.BACKGROUND_LOCATION))
    }

    @Test
    fun appWithoutPermissionsHasNoCategories() {
        val permissions = categorize(app("a"))
        assertTrue(permissions.categories.isEmpty())
        assertTrue(permissions.other.isEmpty())
    }

    @Test
    fun uncategorizedPermissionsGoToOtherAndKeepTheirState() {
        val permissions = categorize(
            app("a", permissions = listOf(granted(p("INTERNET")), requested("com.example.CUSTOM"), granted(p("CAMERA")))),
        )
        assertEquals(listOf(p("INTERNET"), "com.example.CUSTOM"), permissions.other.map { it.name })
        assertEquals(listOf(PermissionCategory.CAMERA), permissions.categories.map { it.category })
    }

    @Test
    fun accessibilityAndDeviceAdminComeFromDeclaredComponents() {
        val permissions = categorize(app("a", declaresAccessibilityService = true, declaresDeviceAdmin = true))
        assertEquals(AccessStatus.DECLARED, permissions.status(PermissionCategory.ACCESSIBILITY))
        assertEquals(AccessStatus.DECLARED, permissions.status(PermissionCategory.DEVICE_ADMIN))
        assertTrue(permissions.isActive(PermissionCategory.ACCESSIBILITY))
    }

    @Test
    fun categoriesComeInTheOrderOfTheEnum() {
        val permissions = categorize(
            app("a", permissions = listOf(granted(p("POST_NOTIFICATIONS")), granted(p("CAMERA")), granted(p("ACCESS_FINE_LOCATION")))),
        )
        assertEquals(
            listOf(PermissionCategory.LOCATION, PermissionCategory.CAMERA, PermissionCategory.NOTIFICATIONS),
            permissions.categories.map { it.category },
        )
    }

    @Test
    fun installOriginIsClassifiedFromTheInstaller() {
        assertEquals(InstallOrigin.PREINSTALLED, InstallOrigins.of(app("a", isSystem = true, installer = null)))
        assertEquals(InstallOrigin.STORE, InstallOrigins.of(app("a", installer = "com.android.vending")))
        assertEquals(InstallOrigin.SIDELOADED, InstallOrigins.of(app("a", installer = "com.google.android.packageinstaller")))
        assertEquals(InstallOrigin.UNKNOWN, InstallOrigins.of(app("a", installer = null)))
        assertEquals(InstallOrigin.OTHER, InstallOrigins.of(app("a", installer = "com.some.store")))
    }

    @Test
    fun specialAccessCategoriesAreFlaggedAsSuch() {
        assertTrue(PermissionCategory.OVERLAY.isSpecialAccess)
        assertTrue(PermissionCategory.USAGE_ACCESS.isSpecialAccess)
        assertFalse(PermissionCategory.NOTIFICATIONS.isSpecialAccess)
    }
}
