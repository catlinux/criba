package io.github.catlinux.criba.ui.review

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.catlinux.criba.R
import io.github.catlinux.criba.domain.ByteFormat
import io.github.catlinux.criba.domain.ReportCategory
import io.github.catlinux.criba.domain.ReviewItem
import io.github.catlinux.criba.domain.ReviewStatus
import io.github.catlinux.criba.domain.TrashFailure
import io.github.catlinux.criba.ui.report.ReportViewModel

@Composable
fun ReviewScreen(
    category: ReportCategory,
    reportViewModel: ReportViewModel,
    onBack: () -> Unit,
    onReturnToReport: (Set<String>) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var accessDenied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(category) {
        val categoryFiles = reportViewModel.scannedFiles.value[category]
        if (categoryFiles.isNullOrEmpty()) onBack() else viewModel.load(categoryFiles)
    }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        accessDenied = !hasTrashAccess()
    }

    val isMoving = state is ReviewState.Moving
    BackHandler(enabled = isMoving) { }
    val selection = (state as? ReviewState.Reviewing)?.selection
    val categoryName = stringResource(categoryLabel(category))

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                TextButton(onClick = onBack, enabled = !isMoving) {
                    Text(stringResource(R.string.review_back))
                }
                Text(
                    stringResource(R.string.review_title, categoryName),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasTrashAccess()) {
                    Text(stringResource(R.string.review_access_explanation))
                    Button(
                        onClick = {
                            accessDenied = false
                            settingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                        enabled = !isMoving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.review_access_settings))
                    }
                } else if (selection != null) {
                    Button(
                        onClick = viewModel::requestConfirmation,
                        enabled = selection.selectedCount > 0 && !isMoving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.review_move_button))
                    }
                }
                if (accessDenied) Text(stringResource(R.string.review_access_denied), color = MaterialTheme.colorScheme.error)
            }
        },
    ) { padding ->
        when (val current = state) {
            ReviewState.Moving -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.review_moving))
            }
            is ReviewState.Done -> DoneContent(
                state = current,
                onReturn = { onReturnToReport(current.movedOriginalPaths) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is ReviewState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            ) {
                Text(stringResource(R.string.review_error, current.detail.orEmpty()), color = MaterialTheme.colorScheme.error)
            }
            is ReviewState.Reviewing -> {
                val items = current.selection.items
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            pluralStringResource(
                                R.plurals.review_selection_summary,
                                current.selection.selectedCount,
                                current.selection.selectedCount,
                                ByteFormat.format(current.selection.selectedBytes),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = viewModel::selectAll) {
                                Text(stringResource(R.string.review_select_all))
                            }
                            TextButton(onClick = viewModel::clear) {
                                Text(stringResource(R.string.review_clear))
                            }
                        }
                        if (current.confirmationError) {
                            Text(stringResource(R.string.review_confirmation_error), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    items(items, key = { it.file.id }) { item ->
                        ReviewFileRow(item, current.selection.selectedIds.contains(item.file.id), viewModel::toggle)
                    }
                }
                if (current.showingConfirmation) {
                    ConfirmationDialog(
                        selection = current.selection,
                        onCancel = viewModel::dismissConfirmation,
                        onConfirm = viewModel::confirmMove,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewFileRow(item: ReviewItem, selected: Boolean, onToggle: (String) -> Unit) {
    val enabled = item.status == ReviewStatus.SELECTABLE
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle(item.file.id) }, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.file.displayName, style = MaterialTheme.typography.titleSmall)
                Text(item.file.relativePath ?: "", style = MaterialTheme.typography.bodySmall)
                Text(ByteFormat.format(item.file.sizeBytes), style = MaterialTheme.typography.bodySmall)
            }
        }
        when (item.status) {
            ReviewStatus.SELECTABLE -> Unit
            ReviewStatus.NEEDS_BACKUP -> Text(stringResource(R.string.review_needs_backup), style = MaterialTheme.typography.bodySmall)
            ReviewStatus.BLOCKED -> Text(stringResource(R.string.review_blocked), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConfirmationDialog(
    selection: io.github.catlinux.criba.domain.ReviewSelection,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val names = selection.selectedCandidates.take(5).joinToString("\n") { it.displayName }
    val remaining = selection.selectedCount - minOf(selection.selectedCount, 5)
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                pluralStringResource(
                    R.plurals.review_confirm_title,
                    selection.selectedCount,
                    selection.selectedCount,
                    ByteFormat.format(selection.selectedBytes),
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (names.isNotEmpty()) Text(names)
                if (remaining > 0) Text(pluralStringResource(R.plurals.review_more_files, remaining, remaining))
                Text(stringResource(R.string.review_confirmation_note))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.review_move_button)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.review_cancel)) } },
    )
}

@Composable
private fun DoneContent(
    state: ReviewState.Done,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (state.movedCount == 0) {
                stringResource(R.string.review_moved_none)
            } else {
                pluralStringResource(
                    R.plurals.review_moved_result,
                    state.movedCount,
                    state.movedCount,
                    ByteFormat.format(state.movedBytes),
                )
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.failed.isNotEmpty()) {
            Text(pluralStringResource(R.plurals.review_failed_count, state.failed.size, state.failed.size))
            state.failed.groupBy { it.failure }.forEach { (failure, failures) ->
                Text("${failures.size} · ${stringResource(failureText(failure))}")
            }
        }
        Text(stringResource(R.string.review_result_note))
        Button(onClick = onReturn) {
            Text(stringResource(R.string.review_return_report))
        }
    }
}

private fun hasTrashAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun categoryLabel(category: ReportCategory): Int = when (category) {
    ReportCategory.LARGE_FILES -> R.string.report_category_large_files
    ReportCategory.SCREENSHOTS -> R.string.report_category_screenshots
    ReportCategory.OLD_DOWNLOADS -> R.string.report_category_old_downloads
    ReportCategory.APKS -> R.string.report_category_apks
}

private fun failureText(failure: TrashFailure): Int = when (failure) {
    TrashFailure.PROTECTED -> R.string.trash_failure_protected
    TrashFailure.NOT_A_REGULAR_FILE -> R.string.trash_failure_not_regular
    TrashFailure.SYMLINK -> R.string.trash_failure_symlink
    TrashFailure.MISSING -> R.string.trash_failure_missing
    TrashFailure.CHANGED_SINCE_SCAN -> R.string.trash_failure_changed
    TrashFailure.DIFFERENT_VOLUME -> R.string.trash_failure_different_volume
    TrashFailure.TARGET_EXISTS -> R.string.trash_failure_target_exists
    TrashFailure.VERIFY_FAILED -> R.string.trash_failure_verify
    TrashFailure.OUTSIDE_TRASH -> R.string.trash_failure_outside_trash
    TrashFailure.NOT_IN_TRASH -> R.string.trash_failure_not_in_trash
    TrashFailure.TRASH_UNAVAILABLE -> R.string.trash_failure_unavailable
    TrashFailure.IO_ERROR -> R.string.trash_failure_io
}
