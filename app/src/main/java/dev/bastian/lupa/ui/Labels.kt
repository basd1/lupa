package dev.bastian.lupa.ui

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.bastian.lupa.R
import dev.bastian.lupa.domain.AccessStatus
import dev.bastian.lupa.domain.Combination
import dev.bastian.lupa.domain.InstallOrigin
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.domain.ProtectionLevel
import dev.bastian.lupa.domain.RiskLevel
import dev.bastian.lupa.domain.ScoreReason

@StringRes
fun PermissionCategory.labelRes(): Int = when (this) {
    PermissionCategory.LOCATION -> R.string.category_location
    PermissionCategory.BACKGROUND_LOCATION -> R.string.category_background_location
    PermissionCategory.CAMERA -> R.string.category_camera
    PermissionCategory.MICROPHONE -> R.string.category_microphone
    PermissionCategory.CONTACTS -> R.string.category_contacts
    PermissionCategory.CALENDAR -> R.string.category_calendar
    PermissionCategory.SMS -> R.string.category_sms
    PermissionCategory.PHONE -> R.string.category_phone
    PermissionCategory.STORAGE_MEDIA -> R.string.category_storage_media
    PermissionCategory.BODY_SENSORS -> R.string.category_body_sensors
    PermissionCategory.ACTIVITY -> R.string.category_activity
    PermissionCategory.NEARBY_DEVICES -> R.string.category_nearby_devices
    PermissionCategory.NOTIFICATIONS -> R.string.category_notifications
    PermissionCategory.OVERLAY -> R.string.category_overlay
    PermissionCategory.ACCESSIBILITY -> R.string.category_accessibility
    PermissionCategory.INSTALL_PACKAGES -> R.string.category_install_packages
    PermissionCategory.ALL_FILES -> R.string.category_all_files
    PermissionCategory.DEVICE_ADMIN -> R.string.category_device_admin
    PermissionCategory.USAGE_ACCESS -> R.string.category_usage_access
}

@StringRes
fun PermissionCategory.explanationRes(): Int = when (this) {
    PermissionCategory.LOCATION -> R.string.explain_location
    PermissionCategory.BACKGROUND_LOCATION -> R.string.explain_background_location
    PermissionCategory.CAMERA -> R.string.explain_camera
    PermissionCategory.MICROPHONE -> R.string.explain_microphone
    PermissionCategory.CONTACTS -> R.string.explain_contacts
    PermissionCategory.CALENDAR -> R.string.explain_calendar
    PermissionCategory.SMS -> R.string.explain_sms
    PermissionCategory.PHONE -> R.string.explain_phone
    PermissionCategory.STORAGE_MEDIA -> R.string.explain_storage_media
    PermissionCategory.BODY_SENSORS -> R.string.explain_body_sensors
    PermissionCategory.ACTIVITY -> R.string.explain_activity
    PermissionCategory.NEARBY_DEVICES -> R.string.explain_nearby_devices
    PermissionCategory.NOTIFICATIONS -> R.string.explain_notifications
    PermissionCategory.OVERLAY -> R.string.explain_overlay
    PermissionCategory.ACCESSIBILITY -> R.string.explain_accessibility
    PermissionCategory.INSTALL_PACKAGES -> R.string.explain_install_packages
    PermissionCategory.ALL_FILES -> R.string.explain_all_files
    PermissionCategory.DEVICE_ADMIN -> R.string.explain_device_admin
    PermissionCategory.USAGE_ACCESS -> R.string.explain_usage_access
}

@StringRes
fun AccessStatus.labelRes(): Int = when (this) {
    AccessStatus.GRANTED -> R.string.status_granted
    AccessStatus.REQUESTED -> R.string.status_requested
    AccessStatus.DECLARED -> R.string.status_declared
}

@StringRes
fun InstallOrigin.labelRes(): Int = when (this) {
    InstallOrigin.PREINSTALLED -> R.string.origin_preinstalled
    InstallOrigin.STORE -> R.string.origin_store
    InstallOrigin.SIDELOADED -> R.string.origin_sideloaded
    InstallOrigin.UNKNOWN -> R.string.origin_unknown
    InstallOrigin.OTHER -> R.string.origin_other
}

@StringRes
fun RiskLevel.labelRes(): Int = when (this) {
    RiskLevel.LOW -> R.string.risk_low
    RiskLevel.MEDIUM -> R.string.risk_medium
    RiskLevel.HIGH -> R.string.risk_high
}

@StringRes
fun ProtectionLevel.labelRes(): Int = when (this) {
    ProtectionLevel.NORMAL -> R.string.protection_normal
    ProtectionLevel.DANGEROUS -> R.string.protection_dangerous
    ProtectionLevel.SIGNATURE -> R.string.protection_signature
    ProtectionLevel.OTHER -> R.string.protection_other
    ProtectionLevel.UNKNOWN -> R.string.protection_unknown
}

@StringRes
fun Combination.labelRes(): Int = when (this) {
    Combination.MICROPHONE_AND_BACKGROUND_LOCATION -> R.string.reason_together_mic_bg_location
    Combination.OVERLAY_AND_ACCESSIBILITY -> R.string.reason_together_overlay_accessibility
    Combination.SMS_AND_CONTACTS -> R.string.reason_together_sms_contacts
}

@Composable
fun ScoreReason.text(): String = when (this) {
    is ScoreReason.Granted -> stringResource(R.string.reason_granted, stringResource(category.labelRes()))
    is ScoreReason.RequestedOnly -> stringResource(R.string.reason_requested_only, stringResource(category.labelRes()))
    is ScoreReason.OldTargetSdk -> stringResource(R.string.reason_old_target_sdk, targetSdk)
    is ScoreReason.UnverifiedOrigin -> stringResource(R.string.reason_unverified_origin, stringResource(origin.labelRes()))
    is ScoreReason.Together -> stringResource(combination.labelRes())
}

@Composable
fun RiskLevel.containerColor(): Color = when (this) {
    RiskLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer
    RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
    RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
}

@Composable
fun RiskLevel.contentColor(): Color = when (this) {
    RiskLevel.LOW -> MaterialTheme.colorScheme.onSecondaryContainer
    RiskLevel.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
    RiskLevel.HIGH -> MaterialTheme.colorScheme.onErrorContainer
}
