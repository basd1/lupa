package dev.bastian.lupa.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.bastian.lupa.domain.AppKind
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.domain.RiskLevel
import dev.bastian.lupa.domain.SortOrder
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
        actions = AppsActions(
            onQueryChange = viewModel::onQueryChange,
            onKindChange = viewModel::onKindChange,
            onRiskChange = viewModel::onRiskChange,
            onCategoryChange = viewModel::onCategoryChange,
            onOldTargetChange = viewModel::onOldTargetChange,
            onSortChange = viewModel::onSortChange,
            onClearFilters = viewModel::clearFilters,
            onRefresh = viewModel::refresh,
            onOpenApp = onOpenApp,
        ),
    )
}

class AppsActions(
    val onQueryChange: (String) -> Unit = {},
    val onKindChange: (AppKind) -> Unit = {},
    val onRiskChange: (RiskLevel?) -> Unit = {},
    val onCategoryChange: (PermissionCategory?) -> Unit = {},
    val onOldTargetChange: (Boolean) -> Unit = {},
    val onSortChange: (SortOrder) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenApp: (String) -> Unit = {},
)

@Composable
fun AppsContent(state: AppsUiState, actions: AppsActions) {
    val filter = state.filter
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
            SortMenu(filter.sort, actions.onSortChange)
            IconButton(onClick = actions.onRefresh, enabled = state.progress == null) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_rescan))
            }
        }
        OutlinedTextField(
            value = filter.query,
            onValueChange = actions.onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { actions.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear_search))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FilterRow(state, actions)
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
                    AppRow(item, onClick = { actions.onOpenApp(item.app.packageName) })
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
private fun FilterRow(state: AppsUiState, actions: AppsActions) {
    val filter = state.filter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.systemAppsIncluded) {
            AppKind.entries.forEach { kind ->
                FilterChip(
                    selected = filter.kind == kind,
                    onClick = { actions.onKindChange(kind) },
                    label = { Text(stringResource(kind.labelRes())) },
                )
            }
        }
        RiskLevel.entries.forEach { level ->
            FilterChip(
                selected = filter.risk == level,
                onClick = { actions.onRiskChange(if (filter.risk == level) null else level) },
                label = { Text(stringResource(level.labelRes())) },
            )
        }
        CategoryFilter(filter.category, actions.onCategoryChange)
        FilterChip(
            selected = filter.oldTargetOnly,
            onClick = { actions.onOldTargetChange(!filter.oldTargetOnly) },
            label = { Text(stringResource(R.string.filter_old_target)) },
        )
        if (filter.hasNarrowing) {
            TextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.filter_clear)) }
        }
    }
}

@Composable
private fun CategoryFilter(selected: PermissionCategory?, onChange: (PermissionCategory?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(stringResource(selected?.labelRes() ?: R.string.filter_category)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_any_category)) },
                onClick = { onChange(null); open = false },
            )
            PermissionCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.labelRes())) },
                    onClick = { onChange(category); open = false },
                )
            }
        }
    }
}

@Composable
private fun SortMenu(sort: SortOrder, onChange: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.action_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes())) },
                    leadingIcon = { if (order == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { onChange(order); open = false },
                )
            }
        }
    }
}

private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.RISK -> R.string.sort_risk
    SortOrder.NAME -> R.string.sort_name
    SortOrder.LAST_UPDATED -> R.string.sort_updated
}

@Composable
internal fun AppRow(item: AppAssessment, onClick: () -> Unit, imageLoader: ImageLoader = koinInject()) {
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
                    modifier = Modifier.widthIn(max = 96.dp).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (active.size > MAX_CHIPS) {
            Text(
                stringResource(R.string.chips_more, active.size - MAX_CHIPS),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
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
