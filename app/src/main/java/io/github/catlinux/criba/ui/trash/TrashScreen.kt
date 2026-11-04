package io.github.catlinux.criba.ui.trash

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
import androidx.compose.material3.Card
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
import io.github.catlinux.criba.domain.TrashFailure
import io.github.catlinux.criba.domain.TrashItem
import io.github.catlinux.criba.domain.TrashRetention

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var accessDenied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        accessDenied = !hasTrashAccess()
    }
    val working = state is TrashScreenState.Working
    BackHandler(enabled = working) { }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                TextButton(onClick = onBack, enabled = !working) {
                    Text(stringResource(R.string.review_back))
                }
                Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.headlineSmall)
            }
        },
        bottomBar = {
            val loaded = state as? TrashScreenState.Loaded
            if (loaded != null && !working) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasTrashAccess()) {
                        Text(stringResource(R.string.trash_access_explanation))
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
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.review_access_settings))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = viewModel::selectAll, enabled = loaded.selection.items.isNotEmpty()) {
                                Text(stringResource(R.string.trash_select_all))
                            }
                            TextButton(onClick = viewModel::clear, enabled = loaded.selection.selectedCount > 0) {
                                Text(stringResource(R.string.trash_clear))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::restoreSelected,
                                enabled = loaded.selection.selectedCount > 0,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.trash_restore))
                            }
                            Button(
                                onClick = viewModel::requestPurge,
                                enabled = loaded.selection.selectedCount > 0,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.trash_purge))
                            }
                        }
                    }
                    if (accessDenied) Text(stringResource(R.string.trash_access_denied), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) { padding ->
        when (val current = state) {
            TrashScreenState.Loading -> CenteredProgress(Modifier.fillMaxSize().padding(padding))
            TrashScreenState.Working -> CenteredProgress(
                Modifier.fillMaxSize().padding(padding),
                stringResource(R.string.trash_working),
            )
            is TrashScreenState.Loaded -> {
                if (current.loadError != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.trash_load_error, current.loadError), color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.trash_uninstall_note))
                    }
                } else if (current.selection.items.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.trash_empty), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.trash_space_note))
                        Text(stringResource(R.string.trash_uninstall_note))
                        current.lastMessage?.let { TrashOperationMessageContent(it) }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                pluralStringResource(
                                    R.plurals.trash_summary,
                                    current.selection.items.size,
                                    current.selection.items.size,
                                    ByteFormat.format(current.totalBytes),
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(stringResource(R.string.trash_space_note))
                            Text(stringResource(R.string.trash_uninstall_note))
                            current.lastMessage?.let { TrashOperationMessageContent(it) }
                        }
                        items(current.selection.items, key = { it.id }) { item ->
                            TrashItemRow(
                                item = item,
                                nowEpochMillis = current.nowEpochMillis,
                                selected = item.id in current.selection.selectedIds,
                                onToggle = viewModel::toggle,
                            )
                        }
                    }
                }

                if (current.showPurgeConfirmation) {
                    PurgeConfirmationDialog(
                        selection = current.selection,
                        onCancel = viewModel::dismissPurge,
                        onConfirm = viewModel::confirmPurge,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredProgress(modifier: Modifier, label: String? = null) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (label != null) Text(label)
    }
}

@Composable
private fun TrashItemRow(
    item: TrashItem,
    nowEpochMillis: Long,
    selected: Boolean,
    onToggle: (String) -> Unit,
) {
    val daysLeft = TrashRetention.daysLeft(item, nowEpochMillis)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle(item.id) })
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(item.originalPath.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall)
                Text(item.originalPath.substringBeforeLast('/', ""), style = MaterialTheme.typography.bodySmall)
                Text(ByteFormat.format(item.bytes), style = MaterialTheme.typography.bodySmall)
                Text(
                    if (daysLeft == 0) stringResource(R.string.trash_due_next_start)
                    else pluralStringResource(R.plurals.trash_days_left, daysLeft, daysLeft),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PurgeConfirmationDialog(
    selection: io.github.catlinux.criba.domain.TrashSelection,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                pluralStringResource(
                    R.plurals.trash_purge_confirmation,
                    selection.selectedCount,
                    selection.selectedCount,
                    ByteFormat.format(selection.selectedBytes),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.trash_purge_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.review_cancel)) }
        },
    )
}

@Composable
private fun TrashOperationMessageContent(message: TrashOperationMessage) {
    when (message) {
        is TrashOperationMessage.Restored -> {
            Text(pluralStringResource(R.plurals.trash_restored_result, message.restoredPaths.size, message.restoredPaths.size))
            if (message.renamedPaths.isNotEmpty()) {
                Text(stringResource(R.string.trash_restored_renamed, message.renamedPaths.joinToString()))
            }
            FailureSummary(message.failed)
        }
        is TrashOperationMessage.Purged -> {
            if (message.purgedCount == 0) {
                Text(stringResource(R.string.trash_purged_none))
            } else {
                Text(
                    pluralStringResource(
                        R.plurals.trash_purged_result,
                        message.purgedCount,
                        message.purgedCount,
                        ByteFormat.format(message.purgedBytes),
                    ),
                )
            }
            FailureSummary(message.failed)
        }
    }
}

@Composable
private fun FailureSummary(failed: List<io.github.catlinux.criba.domain.TrashFailureItem>) {
    if (failed.isEmpty()) return
    Text(pluralStringResource(R.plurals.trash_failed_result, failed.size, failed.size))
    failed.groupBy { it.failure }.forEach { (failure, items) ->
        Text("${items.size} · ${stringResource(trashFailureText(failure))}")
    }
}

private fun hasTrashAccess(): Boolean =
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
