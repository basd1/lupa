package dev.bastian.lupa.ui.detail

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.bastian.lupa.R
import dev.bastian.lupa.data.AppIcon
import dev.bastian.lupa.domain.AppAssessment
import dev.bastian.lupa.domain.CategoryAccess
import dev.bastian.lupa.domain.RiskScore
import dev.bastian.lupa.ui.containerColor
import dev.bastian.lupa.ui.contentColor
import dev.bastian.lupa.ui.explanationRes
import dev.bastian.lupa.ui.labelRes
import dev.bastian.lupa.ui.text
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.text.DateFormat
import java.util.Date

@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = koinViewModel(parameters = { parametersOf(packageName) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppDetailContent(state, onBack)
}

@Composable
fun AppDetailContent(state: AppDetailUiState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        val assessment = state.assessment
        when {
            assessment != null -> DetailBody(assessment)
            state.scanning -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.detail_not_found))
            }
        }
    }
}

@Composable
private fun DetailBody(assessment: AppAssessment, imageLoader: ImageLoader = koinInject()) {
    val app = assessment.app
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding().testTag("detail_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = AppIcon(app.packageName, app.lastUpdateTime),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                )
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SectionTitle(stringResource(R.string.detail_score)) }
        item { ScoreCard(assessment.score) }
        item { SectionTitle(stringResource(R.string.detail_general)) }
        item {
            Column {
                Fact(stringResource(R.string.detail_version), "${app.versionName ?: "?"} (${app.versionCode})")
                Fact(
                    stringResource(R.string.detail_type),
                    stringResource(if (app.isSystem) R.string.detail_type_system else R.string.detail_type_user),
                )
                Fact(stringResource(R.string.detail_target_sdk), app.targetSdk.toString())
                Fact(stringResource(R.string.detail_min_sdk), app.minSdk.toString())
                Fact(stringResource(R.string.detail_first_install), dateFormat.format(Date(app.firstInstallTime)))
                Fact(stringResource(R.string.detail_last_update), dateFormat.format(Date(app.lastUpdateTime)))
                Fact(stringResource(R.string.detail_origin), stringResource(assessment.origin.labelRes()))
                app.installer?.let { Fact(stringResource(R.string.detail_installer), it) }
            }
        }
        item { SectionTitle(stringResource(R.string.detail_permissions)) }
        if (assessment.permissions.categories.isEmpty()) {
            item { Text(stringResource(R.string.detail_no_permissions), style = MaterialTheme.typography.bodyMedium) }
        }
        items(assessment.permissions.categories.size) { index ->
            CategoryCard(assessment.permissions.categories[index])
        }
        if (assessment.permissions.other.isNotEmpty()) {
            item { OtherPermissions(assessment) }
        }
        item { SectionTitle(stringResource(R.string.detail_components)) }
        item {
            val exported = app.exported
            Text(
                stringResource(
                    R.string.detail_components_summary,
                    exported.total, exported.activities, exported.services, exported.receivers, exported.providers,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.detail_open_settings)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScoreCard(score: RiskScore) {
    Surface(color = score.level.containerColor(), contentColor = score.level.contentColor(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp).testTag("score_card"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.score_label, score.value) + " · " + stringResource(score.level.labelRes()),
                style = MaterialTheme.typography.titleMedium,
            )
            score.items.forEach { item ->
                Row {
                    Text(item.reason.text(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("+${item.points}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (score.uncapped > score.value) {
                Text(stringResource(R.string.score_capped, score.uncapped), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = score.level.contentColor().copy(alpha = 0.3f))
            Text(stringResource(R.string.score_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CategoryCard(access: CategoryAccess) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(access.category.labelRes()),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(access.status.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (access.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(access.category.explanationRes()), style = MaterialTheme.typography.bodySmall)
            access.permissions.forEach { permission ->
                Text(
                    "${permission.name.substringAfterLast('.')} · ${stringResource(permission.protection.labelRes())}" +
                        if (permission.granted) " ✓" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OtherPermissions(assessment: AppAssessment) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(
            stringResource(R.string.detail_other_permissions, assessment.permissions.other.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
        )
        if (expanded) {
            assessment.permissions.other.forEach { permission ->
                Text(
                    permission.name + if (permission.granted) " ✓" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
