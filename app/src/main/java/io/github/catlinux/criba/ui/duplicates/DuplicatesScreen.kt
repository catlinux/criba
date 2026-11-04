package io.github.catlinux.criba.ui.duplicates

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.catlinux.criba.domain.ByteFormat
import io.github.catlinux.criba.domain.DuplicateGroup
import io.github.catlinux.criba.domain.DuplicateSelection
import io.github.catlinux.criba.domain.DuplicatesReview
import io.github.catlinux.criba.domain.ReviewStatus
import io.github.catlinux.criba.domain.ScanFile
import io.github.catlinux.criba.domain.TrashFailure
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dateFormatter: DateTimeFormatter
    get() = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasStorageAccess()) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasAccess = hasStorageAccess()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = hasStorageAccess() }

    val moving = state is DuplicatesState.Moving
    val reviewing = state as? DuplicatesState.Reviewing

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                TextButton(onClick = onBack, enabled = !moving) {
                    Text(stringResource(R.string.review_back))
                }
                Text(stringResource(R.string.dup_title), style = MaterialTheme.typography.headlineSmall)
            }
        },
        bottomBar = {
            if (reviewing != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.dup_marked_summary,
                            reviewing.review.markedCount,
                            reviewing.review.markedCount,
                            ByteFormat.format(reviewing.review.markedBytes),
                        ),
                    )
                    if (hasAccess) {
                        Button(
                            onClick = viewModel::requestConfirmation,
                            enabled = reviewing.review.markedCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.dup_move_button))
                        }
                    } else {
                        Text(stringResource(R.string.review_access_explanation))
                        Button(
                            onClick = {
                                settingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.review_access_settings))
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (val current = state) {
            DuplicatesState.Idle -> IdleContent(
                hasAccess = hasAccess,
                onScan = viewModel::startScan,
                onOpenSettings = {
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is DuplicatesState.Scanning -> ScanningContent(
                done = current.progress?.filesDone ?: 0,
                total = current.progress?.filesTotal ?: 0,
                onCancel = viewModel::cancel,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is DuplicatesState.Reviewing -> ReviewContent(
                review = current.review,
                confirmationError = current.confirmationError,
                onToggle = viewModel::toggle,
                onKeep = viewModel::withKept,
                onSelectAll = viewModel::selectAll,
                onClear = viewModel::clear,
                onRescan = viewModel::startScan,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            DuplicatesState.Moving -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.dup_moving))
            }

            is DuplicatesState.Done -> DoneContent(
                state = current,
                onResume = viewModel::resumeReview,
                onScanAgain = viewModel::startScan,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is DuplicatesState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.dup_error, current.detail.orEmpty()))
                Button(onClick = viewModel::startScan) {
                    Text(stringResource(R.string.dup_scan_button))
                }
            }
        }

        if (reviewing != null && reviewing.showingConfirmation) {
            ConfirmationDialog(
                count = reviewing.review.markedCount,
                totalBytes = reviewing.review.markedBytes,
                onCancel = viewModel::dismissConfirmation,
                onConfirm = viewModel::confirmMove,
            )
        }
    }
}

@Composable
private fun IdleContent(
    hasAccess: Boolean,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.dup_intro), style = MaterialTheme.typography.bodyMedium)
        if (hasAccess) {
            Button(onClick = onScan) {
                Text(stringResource(R.string.dup_scan_button))
            }
        } else {
            Text(stringResource(R.string.review_access_explanation))
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.review_access_settings))
            }
        }
    }
}

@Composable
private fun ScanningContent(
    done: Int,
    total: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.dup_scanning, done, total))
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.dup_scanning_initial))
        }
        Button(onClick = onCancel) {
            Text(stringResource(R.string.dup_cancel))
        }
    }
}

@Composable
private fun ReviewContent(
    review: DuplicatesReview,
    confirmationError: Boolean,
    onToggle: (String) -> Unit,
    onKeep: (String, String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (review.groups.isEmpty()) {
        Column(
            modifier = modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.dup_no_duplicates))
            Button(onClick = onRescan) {
                Text(stringResource(R.string.dup_scan_button))
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                pluralStringResource(
                    R.plurals.dup_summary,
                    review.groups.size,
                    review.groups.size,
                    ByteFormat.format(review.reclaimableBytes),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.dup_space_note), style = MaterialTheme.typography.bodySmall)
            if (confirmationError) {
                Text(stringResource(R.string.review_confirmation_error), color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.dup_select_all))
                }
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.dup_clear))
                }
            }
        }
        items(review.groups, key = { it.sha256 }) { group ->
            val selection = review.selectionOf(group.sha256) ?: return@items
            GroupCard(
                group = group,
                selection = selection,
                review = review,
                onToggle = onToggle,
                onKeep = onKeep,
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: DuplicateGroup,
    selection: DuplicateSelection,
    review: DuplicatesReview,
    onToggle: (String) -> Unit,
    onKeep: (String, String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(group.files.first().displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                "${ByteFormat.format(group.sizeBytes)} · " +
                    pluralStringResource(R.plurals.dup_copies, group.files.size, group.files.size),
                style = MaterialTheme.typography.bodySmall,
            )
            group.files.forEach { file ->
                CopyRow(
                    file = file,
                    kept = file.id == selection.keptId,
                    status = review.statusOf(file.id),
                    marked = review.isMarked(file.id),
                    onToggle = onToggle,
                    onKeep = { onKeep(group.sha256, file.id) },
                )
            }
        }
    }
}

@Composable
private fun CopyRow(
    file: ScanFile,
    kept: Boolean,
    status: ReviewStatus?,
    marked: Boolean,
    onToggle: (String) -> Unit,
    onKeep: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${file.relativePath ?: ""}${file.displayName}")
                Text(
                    dateFormatter.format(Instant.ofEpochSecond(file.modifiedEpochSeconds)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                kept -> Text(stringResource(R.string.dup_kept), style = MaterialTheme.typography.bodySmall)
                status == ReviewStatus.SELECTABLE ->
                    Checkbox(checked = marked, onCheckedChange = { onToggle(file.id) })
                status == ReviewStatus.NEEDS_BACKUP ->
                    Text(stringResource(R.string.dup_needs_backup), style = MaterialTheme.typography.bodySmall)
                else -> Text(stringResource(R.string.dup_blocked), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!kept) {
            TextButton(onClick = onKeep) {
                Text(stringResource(R.string.dup_keep_this))
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    count: Int,
    totalBytes: Long,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                pluralStringResource(
                    R.plurals.dup_confirm_title,
                    count,
                    count,
                    ByteFormat.format(totalBytes),
                ),
            )
        },
        text = { Text(stringResource(R.string.dup_confirm_note)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.dup_move_button)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.review_cancel)) }
        },
    )
}

@Composable
private fun DoneContent(
    state: DuplicatesState.Done,
    onResume: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (state.movedCount == 0) {
                stringResource(R.string.dup_done_none)
            } else {
                pluralStringResource(
                    R.plurals.dup_done,
                    state.movedCount,
                    state.movedCount,
                    ByteFormat.format(state.movedBytes),
                )
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.notVerifiedCount > 0) {
            Text(
                pluralStringResource(
                    R.plurals.dup_not_verified,
                    state.notVerifiedCount,
                    state.notVerifiedCount,
                ),
            )
        }
        if (state.failed.isNotEmpty()) {
            Text(pluralStringResource(R.plurals.dup_failed_count, state.failed.size, state.failed.size))
            state.failed.groupBy { it.failure }.forEach { (failure, items) ->
                Text("${items.size} · ${stringResource(trashFailureText(failure))}")
            }
        }
        Text(stringResource(R.string.dup_result_note))
        if (state.remaining.groups.isNotEmpty()) {
            Button(onClick = onResume) {
                Text(stringResource(R.string.dup_continue))
            }
        } else {
            Button(onClick = onScanAgain) {
                Text(stringResource(R.string.dup_scan_button))
            }
        }
    }
}

private fun hasStorageAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun trashFailureText(failure: TrashFailure): Int = when (failure) {
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
