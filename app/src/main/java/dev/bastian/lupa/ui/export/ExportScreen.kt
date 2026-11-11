package dev.bastian.lupa.ui.export

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.lupa.R
import dev.bastian.lupa.domain.ExportFormat
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.export_share_title)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(state.format.mimeType),
    ) { uri: Uri? -> viewModel.saveTo(uri?.toString()) }

    LaunchedEffect(state.pendingShare) {
        val pending = state.pendingShare ?: return@LaunchedEffect
        val uri = pending.uri.toUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = pending.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // Some targets only honour the grant when the URI is also in the ClipData.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, shareTitle))
        viewModel.shareHandled()
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.export_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { format ->
                    FilterChip(
                        selected = state.format == format,
                        onClick = { viewModel.selectFormat(format) },
                        label = { Text(format.name) },
                        modifier = Modifier.testTag("format_${format.name}"),
                    )
                }
            }
            Text(
                stringResource(formatDescription(state.format)),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { saveLauncher.launch(viewModel.fileName()) },
                enabled = state.status != ExportStatus.Working,
                modifier = Modifier.fillMaxWidth().testTag("save"),
            ) { Text(stringResource(R.string.export_save)) }
            OutlinedButton(
                onClick = viewModel::share,
                enabled = state.status != ExportStatus.Working,
                modifier = Modifier.fillMaxWidth().testTag("share"),
            ) { Text(stringResource(R.string.export_share)) }
            when (val status = state.status) {
                ExportStatus.Working -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ExportStatus.Saved -> Text(stringResource(R.string.export_saved), modifier = Modifier.testTag("saved"))
                is ExportStatus.Failed -> Text(
                    stringResource(R.string.export_failed, status.reason),
                    color = MaterialTheme.colorScheme.error,
                )
                ExportStatus.Idle -> Unit
            }
        }
    }
}

private fun formatDescription(format: ExportFormat): Int = when (format) {
    ExportFormat.JSON -> R.string.export_json_description
    ExportFormat.CSV -> R.string.export_csv_description
    ExportFormat.HTML -> R.string.export_html_description
}
