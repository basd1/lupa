package dev.bastian.lupa.ui.changes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.lupa.R
import dev.bastian.lupa.domain.PermissionChange
import dev.bastian.lupa.domain.SnapshotHeader
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ChangesScreen(onOpenApp: (String) -> Unit, viewModel: ChangesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangesContent(
        state = state,
        onAnalyze = viewModel::analyzeNow,
        onSelectOlder = viewModel::selectOlder,
        onSelectNewer = viewModel::selectNewer,
        onOpenApp = onOpenApp,
    )
}

@Composable
fun ChangesContent(
    state: ChangesUiState,
    onAnalyze: () -> Unit,
    onSelectOlder: (SnapshotHeader) -> Unit,
    onSelectNewer: (SnapshotHeader) -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val changes = state.changes
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("changes_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.nav_changes), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onAnalyze, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.running) R.string.analysis_running else R.string.action_analyze))
                }
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {
                    Text(stringResource(R.string.analysis_failed, it), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (state.headers.size < 2) {
            item {
                Text(
                    stringResource(R.string.changes_need_two),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("need_two"),
                )
            }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnapshotPicker(stringResource(R.string.changes_from), state.older, state.headers, dateFormat, onSelectOlder, Modifier.weight(1f))
                SnapshotPicker(stringResource(R.string.changes_to), state.newer, state.headers, dateFormat, onSelectNewer, Modifier.weight(1f))
            }
        }
        if (changes == null) return@LazyColumn
        if (changes.isEmpty) {
            item { Text(stringResource(R.string.changes_none), modifier = Modifier.testTag("no_changes")) }
        }
        if (changes.newApps.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.changes_new_apps, changes.newApps.size)) }
            items(changes.newApps.size) { i ->
                val app = changes.newApps[i]
                ChangeRow(app.label, app.packageName) { onOpenApp(app.packageName) }
            }
        }
        if (changes.removedApps.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.changes_removed_apps, changes.removedApps.size)) }
            items(changes.removedApps.size) { i ->
                val app = changes.removedApps[i]
                ChangeRow(app.label, app.packageName, onClick = null)
            }
        }
        if (changes.updatedApps.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.changes_updated_apps, changes.updatedApps.size)) }
            items(changes.updatedApps.size) { i ->
                val update = changes.updatedApps[i]
                ChangeRow(
                    update.after.label,
                    stringResource(
                        R.string.changes_version_change,
                        update.before.versionName ?: update.before.versionCode.toString(),
                        update.after.versionName ?: update.after.versionCode.toString(),
                    ),
                ) { onOpenApp(update.after.packageName) }
            }
        }
        permissionSection(R.string.changes_new_permissions, changes.newPermissions, onOpenApp)
        permissionSection(R.string.changes_newly_granted, changes.newlyGranted, onOpenApp)
        permissionSection(R.string.changes_revoked, changes.revoked, onOpenApp)
    }
}

private fun LazyListScope.permissionSection(
    titleRes: Int,
    list: List<PermissionChange>,
    onOpenApp: (String) -> Unit,
) {
    if (list.isEmpty()) return
    item { SectionTitle(stringResource(titleRes, list.size)) }
    items(list.size) { i ->
        val change = list[i]
        // A permission that is new and already granted is marked, since that is the case that matters.
        val mark = if (titleRes == R.string.changes_new_permissions && change.granted) " ✓" else ""
        ChangeRow(change.label, change.shortName + mark) { onOpenApp(change.packageName) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ChangeRow(title: String, subtitle: String, onClick: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SnapshotPicker(
    label: String,
    selected: SnapshotHeader?,
    options: List<SnapshotHeader>,
    dateFormat: DateFormat,
    onSelect: (SnapshotHeader) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = true,
            onClick = { open = true },
            label = {
                Column {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Text(selected?.let { dateFormat.format(Date(it.takenAt)) }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("picker_$label"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { header ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.changes_snapshot, dateFormat.format(Date(header.takenAt)), header.appCount)) },
                    onClick = { onSelect(header); open = false },
                )
            }
        }
    }
}
