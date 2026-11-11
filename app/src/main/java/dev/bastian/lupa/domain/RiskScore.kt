package dev.bastian.lupa.domain

import dev.bastian.lupa.domain.PermissionCategory.ACCESSIBILITY
import dev.bastian.lupa.domain.PermissionCategory.ACTIVITY
import dev.bastian.lupa.domain.PermissionCategory.ALL_FILES
import dev.bastian.lupa.domain.PermissionCategory.BACKGROUND_LOCATION
import dev.bastian.lupa.domain.PermissionCategory.BODY_SENSORS
import dev.bastian.lupa.domain.PermissionCategory.CALENDAR
import dev.bastian.lupa.domain.PermissionCategory.CAMERA
import dev.bastian.lupa.domain.PermissionCategory.CONTACTS
import dev.bastian.lupa.domain.PermissionCategory.DEVICE_ADMIN
import dev.bastian.lupa.domain.PermissionCategory.INSTALL_PACKAGES
import dev.bastian.lupa.domain.PermissionCategory.LOCATION
import dev.bastian.lupa.domain.PermissionCategory.MICROPHONE
import dev.bastian.lupa.domain.PermissionCategory.NEARBY_DEVICES
import dev.bastian.lupa.domain.PermissionCategory.NOTIFICATIONS
import dev.bastian.lupa.domain.PermissionCategory.OVERLAY
import dev.bastian.lupa.domain.PermissionCategory.PHONE
import dev.bastian.lupa.domain.PermissionCategory.SMS
import dev.bastian.lupa.domain.PermissionCategory.STORAGE_MEDIA
import dev.bastian.lupa.domain.PermissionCategory.USAGE_ACCESS

/** Where an app came from, as far as the installer package name tells. */
enum class InstallOrigin {
    /** Part of the system image. */
    PREINSTALLED,

    /** A known app store. */
    STORE,

    /** The system package installer or a file manager: an APK opened by hand. */
    SIDELOADED,

    /** No installer recorded, which is what `adb install` leaves. */
    UNKNOWN,

    /** Some other package installed it (an unknown store, an MDM, another app). */
    OTHER,
}

object InstallOrigins {
    private val stores = setOf(
        "com.android.vending", // Google Play
        "com.sec.android.app.samsungapps", // Galaxy Store
        "com.amazon.venezia", // Amazon Appstore
        "com.huawei.appmarket", // AppGallery
        "com.xiaomi.mipicks", // Xiaomi GetApps
        "com.heytap.market", // OPPO App Market
        "com.bbk.appstore", // vivo App Store
        "org.fdroid.fdroid", // F-Droid
        "com.aurora.store", // Aurora Store
    )
    private val packageInstallers = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.google.android.apps.nbu.files",
        "com.android.documentsui",
        "com.android.shell",
    )

    fun of(app: InstalledApp): InstallOrigin = when {
        app.isSystem -> InstallOrigin.PREINSTALLED
        app.installer == null -> InstallOrigin.UNKNOWN
        app.installer in stores -> InstallOrigin.STORE
        app.installer in packageInstallers -> InstallOrigin.SIDELOADED
        else -> InstallOrigin.OTHER
    }

    /** True for origins that give no assurance about where the app came from. */
    fun isUnverified(origin: InstallOrigin): Boolean =
        origin == InstallOrigin.UNKNOWN || origin == InstallOrigin.SIDELOADED
}

enum class RiskLevel { LOW, MEDIUM, HIGH }

/** A sensitive pair of granted accesses that is worse than the sum of its parts. */
enum class Combination(val first: PermissionCategory, val second: PermissionCategory) {
    MICROPHONE_AND_BACKGROUND_LOCATION(MICROPHONE, BACKGROUND_LOCATION),
    OVERLAY_AND_ACCESSIBILITY(OVERLAY, ACCESSIBILITY),
    SMS_AND_CONTACTS(SMS, CONTACTS),
}

/** Why points were added to a score. */
sealed interface ScoreReason {
    data class Granted(val category: PermissionCategory) : ScoreReason
    data class RequestedOnly(val category: PermissionCategory) : ScoreReason
    data class OldTargetSdk(val targetSdk: Int) : ScoreReason
    data class UnverifiedOrigin(val origin: InstallOrigin) : ScoreReason
    data class Together(val combination: Combination) : ScoreReason
}

data class ScoreItem(val reason: ScoreReason, val points: Int)

data class RiskScore(
    /** 0 to 100. */
    val value: Int,
    val level: RiskLevel,
    val items: List<ScoreItem>,
    /** The sum before the cap at 100, to show when the cap hid something. */
    val uncapped: Int,
)

/**
 * Every number the score uses lives here, and the README documents this file. The score is a rough indicator
 * of how much an app can see and do, not a verdict on whether it is trustworthy.
 */
object RiskWeights {
    const val MAX = 100

    /** Points for a category the app has been granted (or declares, for accessibility and device admin). */
    val categoryGranted: Map<PermissionCategory, Int> = mapOf(
        BACKGROUND_LOCATION to 20,
        ACCESSIBILITY to 20,
        SMS to 15,
        OVERLAY to 12,
        DEVICE_ADMIN to 10,
        LOCATION to 8,
        MICROPHONE to 8,
        INSTALL_PACKAGES to 8,
        ALL_FILES to 8,
        CAMERA to 6,
        CONTACTS to 6,
        PHONE to 6,
        USAGE_ACCESS to 5,
        BODY_SENSORS to 4,
        CALENDAR to 3,
        STORAGE_MEDIA to 3,
        ACTIVITY to 2,
        NEARBY_DEVICES to 2,
        NOTIFICATIONS to 1,
    )

    /** A permission that is asked for in the manifest but not granted counts a quarter of the granted weight. */
    const val REQUESTED_ONLY_DIVISOR = 4

    /** Apps that target an old API level skip parts of the modern permission model. */
    fun oldTargetSdkPoints(targetSdk: Int): Int = when {
        targetSdk < 23 -> 15 // permissions are granted at install time, no runtime prompt
        targetSdk < 29 -> 8 // before scoped storage and background location limits
        targetSdk < 31 -> 4
        else -> 0
    }

    const val UNVERIFIED_ORIGIN_POINTS = 10

    val combinationPoints: Map<Combination, Int> = mapOf(
        Combination.MICROPHONE_AND_BACKGROUND_LOCATION to 10,
        Combination.OVERLAY_AND_ACCESSIBILITY to 15,
        Combination.SMS_AND_CONTACTS to 6,
    )

    const val MEDIUM_FROM = 20
    const val HIGH_FROM = 50

    fun levelOf(value: Int): RiskLevel = when {
        value >= HIGH_FROM -> RiskLevel.HIGH
        value >= MEDIUM_FROM -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }
}

/** Pure scoring: the same app always gets the same score, and the items add up to it (before the cap). */
object RiskScorer {
    fun score(app: InstalledApp, permissions: AppPermissions = categorize(app)): RiskScore {
        val items = mutableListOf<ScoreItem>()
        permissions.categories.forEach { access ->
            val weight = RiskWeights.categoryGranted.getValue(access.category)
            if (access.isActive) {
                items += ScoreItem(ScoreReason.Granted(access.category), weight)
            } else {
                val points = maxOf(1, weight / RiskWeights.REQUESTED_ONLY_DIVISOR)
                items += ScoreItem(ScoreReason.RequestedOnly(access.category), points)
            }
        }
        val sdkPoints = RiskWeights.oldTargetSdkPoints(app.targetSdk)
        if (sdkPoints > 0) items += ScoreItem(ScoreReason.OldTargetSdk(app.targetSdk), sdkPoints)
        val origin = InstallOrigins.of(app)
        if (InstallOrigins.isUnverified(origin)) {
            items += ScoreItem(ScoreReason.UnverifiedOrigin(origin), RiskWeights.UNVERIFIED_ORIGIN_POINTS)
        }
        RiskWeights.combinationPoints.forEach { (combination, points) ->
            if (permissions.isActive(combination.first) && permissions.isActive(combination.second)) {
                items += ScoreItem(ScoreReason.Together(combination), points)
            }
        }
        val uncapped = items.sumOf { it.points }
        val value = uncapped.coerceIn(0, RiskWeights.MAX)
        return RiskScore(value, RiskWeights.levelOf(value), items.sortedByDescending { it.points }, uncapped)
    }
}

/** An app together with everything derived from it, computed once per scan. */
data class AppAssessment(
    val app: InstalledApp,
    val permissions: AppPermissions,
    val score: RiskScore,
    val origin: InstallOrigin,
) {
    companion object {
        fun of(app: InstalledApp): AppAssessment {
            val permissions = categorize(app)
            return AppAssessment(app, permissions, RiskScorer.score(app, permissions), InstallOrigins.of(app))
        }
    }
}
