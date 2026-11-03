package io.github.catlinux.criba.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.catlinux.criba.R
import io.github.catlinux.criba.domain.ByteFormat

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val storage = viewModel.storage
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
        }
    }
}
