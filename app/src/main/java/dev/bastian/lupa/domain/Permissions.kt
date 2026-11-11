package dev.bastian.lupa.domain

import kotlinx.serialization.Serializable

/**
 * Readable groups for the permissions that matter for privacy. The first block are runtime ("dangerous")
 * permissions; the second are special accesses that Android manages outside the normal permission dialog.
 */
@Serializable
enum class PermissionCategory {
    LOCATION,
    BACKGROUND_LOCATION,
    CAMERA,
    MICROPHONE,
    CONTACTS,
    CALENDAR,
    SMS,
    PHONE,
    STORAGE_MEDIA,
    BODY_SENSORS,
    ACTIVITY,
    NEARBY_DEVICES,
    NOTIFICATIONS,

    OVERLAY,
    ACCESSIBILITY,
    INSTALL_PACKAGES,
    ALL_FILES,
    DEVICE_ADMIN,
    USAGE_ACCESS,
    ;

    val isSpecialAccess: Boolean get() = ordinal >= OVERLAY.ordinal
}

object PermissionCatalog {
    private const val PREFIX = "android.permission."

    private val byPermission: Map<String, PermissionCategory> = buildMap {
        fun add(category: PermissionCategory, vararg names: String) = names.forEach { put(PREFIX + it, category) }
        add(PermissionCategory.LOCATION, "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION")
        add(PermissionCategory.BACKGROUND_LOCATION, "ACCESS_BACKGROUND_LOCATION")
        add(PermissionCategory.CAMERA, "CAMERA")
        add(PermissionCategory.MICROPHONE, "RECORD_AUDIO")
        add(PermissionCategory.CONTACTS, "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS")
        add(PermissionCategory.CALENDAR, "READ_CALENDAR", "WRITE_CALENDAR")
        add(PermissionCategory.SMS, "SEND_SMS", "RECEIVE_SMS", "READ_SMS", "RECEIVE_WAP_PUSH", "RECEIVE_MMS")
        add(
            PermissionCategory.PHONE,
            "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE", "ANSWER_PHONE_CALLS", "ADD_VOICEMAIL",
            "USE_SIP", "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS", "ACCEPT_HANDOVER",
        )
        add(
            PermissionCategory.STORAGE_MEDIA,
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO",
            "READ_MEDIA_AUDIO", "READ_MEDIA_VISUAL_USER_SELECTED", "ACCESS_MEDIA_LOCATION",
        )
        add(PermissionCategory.BODY_SENSORS, "BODY_SENSORS", "BODY_SENSORS_BACKGROUND")
        add(PermissionCategory.ACTIVITY, "ACTIVITY_RECOGNITION")
        add(
            PermissionCategory.NEARBY_DEVICES,
            "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE", "NEARBY_WIFI_DEVICES", "UWB_RANGING",
        )
        add(PermissionCategory.NOTIFICATIONS, "POST_NOTIFICATIONS")
        add(PermissionCategory.OVERLAY, "SYSTEM_ALERT_WINDOW")
        add(PermissionCategory.INSTALL_PACKAGES, "REQUEST_INSTALL_PACKAGES")
        add(PermissionCategory.ALL_FILES, "MANAGE_EXTERNAL_STORAGE")
        add(PermissionCategory.USAGE_ACCESS, "PACKAGE_USAGE_STATS")
    }

    /** The category of a permission, or null when it is not one Lupa groups (most "normal" permissions). */
    fun categoryOf(permission: String): PermissionCategory? = byPermission[permission]
}

enum class AccessStatus {
    /** At least one permission of the category is granted. */
    GRANTED,

    /** Asked for in the manifest but not granted at the moment. */
    REQUESTED,

    /** Accessibility service or device admin: there is no grant to read, the app declares the component. */
    DECLARED,
}

data class CategoryAccess(
    val category: PermissionCategory,
    val permissions: List<RequestedPermission>,
    val status: AccessStatus,
) {
    /** True when the app can use this capability right now, as far as the manifest and the grants tell. */
    val isActive: Boolean get() = status != AccessStatus.REQUESTED
}

data class AppPermissions(
    /** Only the categories the app is involved with, in the order of [PermissionCategory]. */
    val categories: List<CategoryAccess>,
    /** Requested permissions that belong to no category. */
    val other: List<RequestedPermission>,
) {
    fun status(category: PermissionCategory): AccessStatus? = categories.firstOrNull { it.category == category }?.status

    fun isActive(category: PermissionCategory): Boolean = categories.any { it.category == category && it.isActive }
}

/** Groups the permissions of [app] by category. */
fun categorize(app: InstalledApp): AppPermissions {
    val grouped = app.permissions.groupBy { PermissionCatalog.categoryOf(it.name) }
    val categories = PermissionCategory.entries.mapNotNull { category ->
        val declared = when (category) {
            PermissionCategory.ACCESSIBILITY -> app.declaresAccessibilityService
            PermissionCategory.DEVICE_ADMIN -> app.declaresDeviceAdmin
            else -> false
        }
        val permissions = grouped[category].orEmpty()
        when {
            declared -> CategoryAccess(category, permissions, AccessStatus.DECLARED)
            permissions.any { it.granted } -> CategoryAccess(category, permissions, AccessStatus.GRANTED)
            permissions.isNotEmpty() -> CategoryAccess(category, permissions, AccessStatus.REQUESTED)
            else -> null
        }
    }
    return AppPermissions(categories, other = grouped[null].orEmpty())
}
