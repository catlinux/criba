package io.github.catlinux.criba.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.catlinux.criba.R
import io.github.catlinux.criba.domain.ByteFormat

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val storage = viewModel.storage
    val largeFilesState by viewModel.largeFilesState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val allGranted = scanPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        permissionDenied = !allGranted
        if (allGranted) viewModel.scanLargeFiles()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(
                    R.string.home_free,
                    ByteFormat.format(storage.freeBytes),
                    ByteFormat.format(storage.totalBytes),
                ),
            )
            Text(stringResource(R.string.home_used, ByteFormat.format(storage.usedBytes)))
            Text(stringResource(R.string.home_note), style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = {
                    val missing = scanPermissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) {
                        permissionDenied = false
                        viewModel.scanLargeFiles()
                    } else {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                },
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
                            stringResource(
                                R.string.scan_result,
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

            if (permissionDenied) {
                Text(
                    stringResource(R.string.scan_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(stringResource(R.string.scan_scope_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
