package dev.bastian.lupa.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.lupa.R
import dev.bastian.lupa.domain.Settings
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Bumped after the permission dialog closes so the "blocked" note is evaluated again.
    var permissionChecks by remember { mutableIntStateOf(0) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Periodic analysis works with or without the permission; only the notification depends on it.
        permissionChecks++
        viewModel.setPeriodicAnalysis(true)
    }
    val notificationsBlocked = remember(permissionChecks, settings.periodicAnalysis) {
        settings.periodicAnalysis && !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall)
        SwitchRow(
            title = stringResource(R.string.setting_include_system),
            description = stringResource(R.string.setting_include_system_description),
            checked = settings.includeSystemApps,
            onCheckedChange = viewModel::setIncludeSystemApps,
        )
        SwitchRow(
            title = stringResource(R.string.setting_periodic),
            description = stringResource(R.string.setting_periodic_description),
            checked = settings.periodicAnalysis,
            onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !NotificationManagerCompat.from(context).areNotificationsEnabled()
                ) {
                    // Asked only now, because the user just turned on the feature that needs it.
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setPeriodicAnalysis(enabled)
                }
            },
        )
        if (settings.periodicAnalysis) {
            PeriodChips(settings, viewModel::setPeriodHours)
            SwitchRow(
                title = stringResource(R.string.setting_notify),
                description = stringResource(R.string.setting_notify_description),
                checked = settings.notifyOnChanges,
                onCheckedChange = viewModel::setNotifyOnChanges,
            )
            if (notificationsBlocked) {
                Text(
                    stringResource(R.string.setting_notifications_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PeriodChips(settings: Settings, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.setting_period), style = MaterialTheme.typography.bodyMedium)
        Settings.PERIOD_OPTIONS_HOURS.forEach { hours ->
            FilterChip(
                selected = settings.periodHours == hours,
                onClick = { onChange(hours) },
                label = {
                    Text(
                        if (hours % 24 == 0) stringResource(R.string.setting_period_days, hours / 24)
                        else stringResource(R.string.setting_period_hours, hours),
                    )
                },
            )
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
