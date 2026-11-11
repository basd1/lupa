package dev.bastian.lupa.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.lupa.R
import dev.bastian.lupa.domain.CategoryCount
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.domain.RiskLevel
import dev.bastian.lupa.domain.Summary
import dev.bastian.lupa.ui.containerColor
import dev.bastian.lupa.ui.contentColor
import dev.bastian.lupa.ui.labelRes
import org.koin.androidx.compose.koinViewModel

@Composable
fun SummaryScreen(
    onShowApps: () -> Unit,
    onOpenCategory: (PermissionCategory) -> Unit,
    onExport: () -> Unit,
    viewModel: SummaryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryContent(
        state = state,
        onRefresh = viewModel::refresh,
        onRisk = { viewModel.showRisk(it); onShowApps() },
        onOldTargets = { viewModel.showOldTargets(); onShowApps() },
        onCategory = onOpenCategory,
        onExport = onExport,
    )
}

@Composable
fun SummaryContent(
    state: SummaryUiState,
    onRefresh: () -> Unit,
    onRisk: (RiskLevel) -> Unit,
    onOldTargets: () -> Unit,
    onCategory: (PermissionCategory) -> Unit,
    onExport: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.nav_summary),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_export))
            }
            IconButton(onClick = onRefresh, enabled = state.progress == null) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_rescan))
            }
        }
        state.progress?.let { progress ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (progress.total > 0) {
                    LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.scanning_progress, progress.done, progress.total), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.error?.let {
            Text(
                stringResource(R.string.scan_failed, it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        state.summary?.let { summary ->
            SummaryGrid(summary, onRisk, onOldTargets, onCategory)
        }
    }
}

@Composable
private fun SummaryGrid(
    summary: Summary,
    onRisk: (RiskLevel) -> Unit,
    onOldTargets: () -> Unit,
    onCategory: (PermissionCategory) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize().testTag("summary_grid"),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    pluralStringResource(R.plurals.summary_total, summary.total, summary.total),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("summary_total"),
                )
                Text(
                    stringResource(R.string.summary_breakdown, summary.userApps, summary.systemApps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.summary_risk_title)) }
        items(RiskLevel.entries.toList()) { level ->
            val count = summary.byRisk.getValue(level)
            Card(
                onClick = { onRisk(level) },
                colors = CardDefaults.cardColors(containerColor = level.containerColor(), contentColor = level.contentColor()),
                modifier = Modifier.testTag("risk_${level.name}"),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(level.labelRes()), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(onClick = onOldTargets, modifier = Modifier.fillMaxWidth().testTag("old_target")) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(summary.oldTargetApps.toString(), style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text(stringResource(R.string.summary_old_target_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.summary_old_target_caption), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.summary_categories_title)) }
        items(summary.categories, key = { it.category.name }) { count ->
            CategoryCard(count, onClick = { onCategory(count.category) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.summary_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CategoryCard(count: CategoryCount, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.testTag("category_${count.category.name}")) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(count.withAccess.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(count.category.labelRes()), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.summary_with_access), style = MaterialTheme.typography.bodySmall)
            if (count.requestedOnly > 0) {
                Text(
                    stringResource(R.string.summary_requested_only, count.requestedOnly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
