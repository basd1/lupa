package dev.bastian.lupa.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.lupa.R
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.ui.apps.AppRow
import dev.bastian.lupa.ui.explanationRes
import dev.bastian.lupa.ui.labelRes
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CategoryScreen(
    category: PermissionCategory,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    viewModel: CategoryViewModel = koinViewModel(parameters = { parametersOf(category) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(
            stringResource(category.labelRes()),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            stringResource(category.explanationRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.category_with_access, state.withAccess.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (state.loaded && state.withAccess.isEmpty()) {
                item { Text(stringResource(R.string.category_none), modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(state.withAccess, key = { "a:" + it.app.packageName }) { item ->
                AppRow(item, onClick = { onOpenApp(item.app.packageName) })
                HorizontalDivider()
            }
            if (state.requestedOnly.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.category_requested_only, state.requestedOnly.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(state.requestedOnly, key = { "r:" + it.app.packageName }) { item ->
                    AppRow(item, onClick = { onOpenApp(item.app.packageName) })
                    HorizontalDivider()
                }
            }
        }
    }
}
