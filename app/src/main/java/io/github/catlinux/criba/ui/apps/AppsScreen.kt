package io.github.catlinux.criba.ui.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.catlinux.criba.R
import io.github.catlinux.criba.domain.AppCandidate
import io.github.catlinux.criba.domain.AppReason
import io.github.catlinux.criba.domain.AppScanResult
import io.github.catlinux.criba.domain.AppUsageInfo
import io.github.catlinux.criba.domain.ByteFormat
import io.github.catlinux.criba.domain.lastUseDaysAgo

@Composable
fun AppsScreen(
    onBack: () -> Unit,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.review_back)) }
                Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        when (val current = state) {
            AppsState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            is AppsState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            ) { Text(stringResource(R.string.apps_error, current.detail.orEmpty())) }

            is AppsState.NoUsageAccess -> NoUsageAccessContent(
                apps = current.apps,
                onGrantAccess = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is AppsState.Loaded -> LoadedContent(
                result = current.result,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenAppSettings = { packageName ->
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun NoUsageAccessContent(
    apps: List<AppUsageInfo>,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.apps_no_access_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.apps_no_access_explanation))
                    Button(onClick = onGrantAccess) {
                        Text(stringResource(R.string.apps_usage_access_button))
                    }
                }
            }
            Text(stringResource(R.string.apps_no_icon_note), style = MaterialTheme.typography.bodySmall)
        }
        items(apps, key = { it.packageName }) { app ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleSmall)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.apps_no_usage_data), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LoadedContent(
    result: AppScanResult,
    onOpenAppSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                pluralStringResource(
                    R.plurals.apps_summary,
                    result.candidates.size,
                    result.candidates.size,
                    ByteFormat.format(result.totalKnownBytes),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (result.unknownSizeCount > 0) {
                Text(pluralStringResource(R.plurals.apps_summary_unknown, result.unknownSizeCount, result.unknownSizeCount))
            }
            Text(stringResource(R.string.apps_threshold_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.apps_no_icon_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.apps_readonly_note), style = MaterialTheme.typography.bodySmall)
        }
        if (result.candidates.isEmpty()) {
            item { Text(stringResource(R.string.apps_no_candidates)) }
        } else {
            items(result.candidates, key = { it.app.packageName }) { candidate ->
                CandidateCard(candidate, onOpenAppSettings)
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: AppCandidate, onOpenAppSettings: (String) -> Unit) {
    val sizes = candidate.app.sizes
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(candidate.app.label, style = MaterialTheme.typography.titleSmall)
            Text(candidate.app.packageName, style = MaterialTheme.typography.bodySmall)
            if (sizes == null) {
                Text(stringResource(R.string.apps_size_unknown), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.apps_total_size, ByteFormat.format(sizes.totalBytes)))
                Text(
                    stringResource(
                        R.string.apps_breakdown,
                        ByteFormat.format(sizes.appBytes),
                        ByteFormat.format(sizes.dataBytes),
                        ByteFormat.format(sizes.cacheBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(reasonText(candidate), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onOpenAppSettings(candidate.app.packageName) }) {
                Text(stringResource(R.string.apps_open_settings))
            }
        }
    }
}

@Composable
private fun reasonText(candidate: AppCandidate): String =
    when (candidate.reason) {
        AppReason.NO_USE_RECORDED -> stringResource(R.string.apps_no_use_recorded)
        AppReason.UNUSED_SINCE -> {
            val days = lastUseDaysAgo(System.currentTimeMillis() / 1000, candidate.app.lastUsedEpochSeconds) ?: 0
            pluralStringResource(R.plurals.apps_unused_since, days.toInt(), days)
        }
    }
