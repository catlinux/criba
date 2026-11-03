package io.github.catlinux.criba.ui.report

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.catlinux.criba.R
import io.github.catlinux.criba.domain.AgeBucket
import io.github.catlinux.criba.domain.ByteFormat
import io.github.catlinux.criba.domain.RecoverableSummary
import io.github.catlinux.criba.domain.ReportCategory

@Composable
fun ReportScreen(onBack: () -> Unit, viewModel: ReportViewModel = hiltViewModel()) {
    val summary by viewModel.recoverableSummary.collectAsStateWithLifecycle()
    val largeFilesState by viewModel.largeFilesState.collectAsStateWithLifecycle()
    val downloadsState by viewModel.downloadsState.collectAsStateWithLifecycle()
    val screenshotsState by viewModel.screenshotsState.collectAsStateWithLifecycle()
    val foldersState by viewModel.foldersState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var pendingScan by rememberSaveable { mutableStateOf(PendingScan.LARGE_FILES) }

    val scanPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun runScan(target: PendingScan) {
        when (target) {
            PendingScan.LARGE_FILES -> viewModel.scanLargeFiles()
            PendingScan.SCREENSHOTS -> viewModel.scanScreenshots()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val allGranted = scanPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        permissionDenied = !allGranted
        if (allGranted) runScan(pendingScan)
    }

    fun requestScan(target: PendingScan) {
        pendingScan = target
        val missing = scanPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            permissionDenied = false
            runScan(target)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.report_back))
                }
                Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(summary)

            Button(
                onClick = { requestScan(PendingScan.LARGE_FILES) },
            ) {
                Text(stringResource(R.string.scan_button))
            }

            when (val state = largeFilesState) {
                LargeFilesState.Idle -> Unit
                LargeFilesState.Scanning -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.scan_running))
                }
                is LargeFilesState.Done ->
                    if (state.count == 0) {
                        Text(stringResource(R.string.scan_empty))
                    } else {
                        Text(
                            pluralStringResource(
                                R.plurals.scan_result,
                                state.count,
                                state.count,
                                ByteFormat.format(state.totalBytes),
                            ),
                        )
                    }
                LargeFilesState.Failed ->
                    Text(
                        stringResource(R.string.scan_error),
                        color = MaterialTheme.colorScheme.error,
                    )
            }

            Button(
                onClick = { requestScan(PendingScan.SCREENSHOTS) },
            ) {
                Text(stringResource(R.string.screenshots_button))
            }

            when (val state = screenshotsState) {
                ScreenshotsState.Idle -> Unit
                ScreenshotsState.Scanning -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.screenshots_running))
                }
                is ScreenshotsState.Done ->
                    if (state.count == 0) {
                        Text(stringResource(R.string.screenshots_empty))
                    } else {
                        state.buckets.filter { it.count > 0 }.forEach { bucket ->
                            Text(
                                pluralStringResource(
                                    screenshotsBucketText(bucket.bucket),
                                    bucket.count,
                                    bucket.count,
                                    ByteFormat.format(bucket.totalBytes),
                                ),
                            )
                        }
                        Text(
                            pluralStringResource(
                                R.plurals.screenshots_total,
                                state.count,
                                state.count,
                                ByteFormat.format(state.totalBytes),
                            ),
                        )
                    }
                ScreenshotsState.Failed ->
                    Text(
                        stringResource(R.string.screenshots_error),
                        color = MaterialTheme.colorScheme.error,
                    )
            }

            if (permissionDenied) {
                Text(
                    stringResource(R.string.scan_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(stringResource(R.string.scan_scope_note), style = MaterialTheme.typography.bodyMedium)

            DownloadsSection(
                state = downloadsState,
                onScan = viewModel::scanDownloads,
            )

            FoldersSection(
                state = foldersState,
                onFolderPicked = viewModel::scanFolders,
            )

            Text(stringResource(R.string.report_overlap_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.report_readonly_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SummaryCard(summary: RecoverableSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (summary.categories.isEmpty()) {
                Text(
                    stringResource(R.string.report_summary_none),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(
                    pluralStringResource(
                        R.plurals.report_summary_total,
                        summary.uniqueCount,
                        ByteFormat.format(summary.uniqueBytes),
                        summary.uniqueCount,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.report_summary_includes, categoryNames(summary.categories)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun categoryNames(categories: Set<ReportCategory>): String =
    ReportCategory.entries
        .filter { it in categories }
        .map { stringResource(categoryLabel(it)) }
        .joinToString(", ")

private fun categoryLabel(category: ReportCategory): Int =
    when (category) {
        ReportCategory.LARGE_FILES -> R.string.report_category_large_files
        ReportCategory.SCREENSHOTS -> R.string.report_category_screenshots
        ReportCategory.OLD_DOWNLOADS -> R.string.report_category_old_downloads
        ReportCategory.APKS -> R.string.report_category_apks
    }

@Composable
private fun DownloadsSection(state: DownloadsState, onScan: () -> Unit) {
    val context = LocalContext.current
    var accessDenied by rememberSaveable { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (hasDownloadsAccess(context)) {
            accessDenied = false
            onScan()
        } else {
            accessDenied = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        accessDenied = !granted
        if (granted) onScan()
    }

    Text(stringResource(R.string.downloads_explanation), style = MaterialTheme.typography.bodyMedium)

    Button(
        onClick = {
            when {
                hasDownloadsAccess(context) -> {
                    accessDenied = false
                    onScan()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    accessDenied = false
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
                else -> permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        },
    ) {
        Text(stringResource(R.string.downloads_button))
    }

    when (state) {
        DownloadsState.Idle -> Unit
        DownloadsState.Scanning -> {
            CircularProgressIndicator()
            Text(stringResource(R.string.downloads_running))
        }
        is DownloadsState.Done -> {
            if (state.oldFilesCount == 0) {
                Text(stringResource(R.string.downloads_old_empty))
            } else {
                Text(
                    pluralStringResource(
                        R.plurals.downloads_old_result,
                        state.oldFilesCount,
                        state.oldFilesCount,
                        ByteFormat.format(state.oldFilesBytes),
                    ),
                )
            }
            if (state.apkCount == 0) {
                Text(stringResource(R.string.downloads_apk_empty))
            } else {
                Text(
                    pluralStringResource(
                        R.plurals.downloads_apk_result,
                        state.apkCount,
                        state.apkCount,
                        ByteFormat.format(state.apkBytes),
                    ),
                )
            }
        }
        DownloadsState.Failed ->
            Text(
                stringResource(R.string.downloads_error),
                color = MaterialTheme.colorScheme.error,
            )
    }

    if (accessDenied) {
        Text(
            stringResource(R.string.downloads_access_denied),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun FoldersSection(state: FoldersState, onFolderPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onFolderPicked(uri)
    }

    Text(stringResource(R.string.folders_explanation), style = MaterialTheme.typography.bodyMedium)

    Button(onClick = { launcher.launch(null) }) {
        Text(stringResource(R.string.folders_button))
    }

    when (state) {
        FoldersState.Idle -> Unit
        FoldersState.Scanning -> {
            CircularProgressIndicator()
            Text(stringResource(R.string.folders_running))
        }
        is FoldersState.Done ->
            if (state.emptyCount == 0 && state.inactiveCount == 0) {
                Text(stringResource(R.string.folders_none))
            } else {
                if (state.emptyCount > 0) {
                    Text(pluralStringResource(R.plurals.folders_empty_count, state.emptyCount, state.emptyCount))
                }
                if (state.inactiveCount > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.folders_inactive_result,
                            state.inactiveCount,
                            state.inactiveCount,
                            ByteFormat.format(state.inactiveBytes),
                        ),
                    )
                }
            }
        FoldersState.Failed ->
            Text(
                stringResource(R.string.folders_error),
                color = MaterialTheme.colorScheme.error,
            )
    }
}

private fun hasDownloadsAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

private enum class PendingScan { LARGE_FILES, SCREENSHOTS }

private fun screenshotsBucketText(bucket: AgeBucket): Int =
    when (bucket) {
        AgeBucket.DAYS_30_TO_90 -> R.plurals.screenshots_bucket_30_90
        AgeBucket.DAYS_90_TO_365 -> R.plurals.screenshots_bucket_90_365
        AgeBucket.OVER_365 -> R.plurals.screenshots_bucket_365
    }
