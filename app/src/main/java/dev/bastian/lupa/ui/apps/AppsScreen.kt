package dev.bastian.lupa.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.bastian.lupa.R
import dev.bastian.lupa.data.AppIcon
import dev.bastian.lupa.domain.AppAssessment
import dev.bastian.lupa.domain.RiskScore
import dev.bastian.lupa.ui.containerColor
import dev.bastian.lupa.ui.contentColor
import dev.bastian.lupa.ui.labelRes
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun AppsScreen(onOpenApp: (String) -> Unit, viewModel: AppsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppsContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onKindChange = viewModel::onKindChange,
        onRefresh = viewModel::refresh,
        onOpenApp = onOpenApp,
    )
}

@Composable
fun AppsContent(
    state: AppsUiState,
    onQueryChange: (String) -> Unit,
    onKindChange: (AppKind) -> Unit,
    onRefresh: () -> Unit,
    onOpenApp: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_apps),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = state.progress == null) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_rescan))
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear_search))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.systemAppsIncluded) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { onKindChange(kind) },
                        label = { Text(stringResource(kind.labelRes())) },
                    )
                }
            }
        }
        val progress = state.progress
        if (progress != null) {
            ScanProgressBar(progress.done, progress.total)
        }
        Text(
            text = pluralStringResource(R.plurals.apps_count, state.apps.size, state.apps.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        state.error?.let {
            Text(
                text = stringResource(R.string.scan_failed, it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.failedApps > 0) {
            Text(
                text = pluralStringResource(R.plurals.apps_unreadable, state.failedApps, state.failedApps),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.apps.isEmpty() && state.progress == null && state.error == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.no_apps_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.apps, key = { it.app.packageName }) { item ->
                    AppRow(item, onClick = { onOpenApp(item.app.packageName) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ScanProgressBar(done: Int, total: Int) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (total > 0) {
            LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = stringResource(R.string.scanning_progress, done, total),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AppRow(item: AppAssessment, onClick: () -> Unit, imageLoader: ImageLoader = koinInject()) {
    val app = item.app
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = AppIcon(app.packageName, app.lastUpdateTime),
            imageLoader = imageLoader,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CategoryChips(item)
        }
        ScoreBadge(item.score)
    }
}

@Composable
private fun CategoryChips(item: AppAssessment) {
    val active = item.permissions.categories.filter { it.isActive }.map { it.category }
    if (active.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        active.take(MAX_CHIPS).forEach { category ->
            Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    stringResource(category.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (active.size > MAX_CHIPS) {
            Text(
                stringResource(R.string.chips_more, active.size - MAX_CHIPS),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun ScoreBadge(score: RiskScore) {
    Surface(shape = MaterialTheme.shapes.small, color = score.level.containerColor(), contentColor = score.level.contentColor()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.value.toString(), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(score.level.labelRes()), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private const val MAX_CHIPS = 3

private fun AppKind.labelRes(): Int = when (this) {
    AppKind.ALL -> R.string.kind_all
    AppKind.USER -> R.string.kind_user
    AppKind.SYSTEM -> R.string.kind_system
}
