package io.github.catlinux.criba.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.catlinux.criba.R
import io.github.catlinux.criba.domain.ActionRecord
import io.github.catlinux.criba.domain.ActionResult
import io.github.catlinux.criba.domain.ActionType
import io.github.catlinux.criba.domain.ByteFormat
import io.github.catlinux.criba.domain.TrashFailure
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.review_back)) }
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        when (val current = state) {
            HistoryState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            is HistoryState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            ) { Text(stringResource(R.string.history_error, current.detail.orEmpty())) }
            is HistoryState.Loaded -> if (current.records.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    Text(stringResource(R.string.history_empty))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current.records, key = { it.id }) { record -> HistoryRecordCard(record) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(record: ActionRecord) {
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    val messageFailure = record.message?.let { message ->
        TrashFailure.entries.firstOrNull { it.name == message }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(actionTypeText(record.type)), style = MaterialTheme.typography.titleSmall)
            Text(record.originalPath.substringAfterLast('/'))
            Text(record.originalPath, style = MaterialTheme.typography.bodySmall)
            Text(ByteFormat.format(record.bytes), style = MaterialTheme.typography.bodySmall)
            Text(formatter.format(Instant.ofEpochMilli(record.atEpochMillis)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(actionResultText(record.result)), style = MaterialTheme.typography.bodySmall)
            if (record.message != null) {
                Text(
                    if (messageFailure != null) stringResource(trashFailureText(messageFailure)) else record.message,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun actionTypeText(type: ActionType): Int = when (type) {
    ActionType.MOVED_TO_TRASH -> R.string.history_action_moved
    ActionType.RESTORED -> R.string.history_action_restored
    ActionType.PURGED -> R.string.history_action_purged
}

private fun actionResultText(result: ActionResult): Int = when (result) {
    ActionResult.PENDING -> R.string.history_result_pending
    ActionResult.SUCCESS -> R.string.history_result_success
    ActionResult.FAILED -> R.string.history_result_failed
}

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
