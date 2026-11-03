package io.github.catlinux.criba.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catlinux.criba.domain.LargeFilesScanner
import io.github.catlinux.criba.domain.OldApksScanner
import io.github.catlinux.criba.domain.OldDownloadsScanner
import io.github.catlinux.criba.domain.StorageInfoSource
import io.github.catlinux.criba.domain.StorageSnapshot
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LargeFilesState {
    data object Idle : LargeFilesState
    data object Scanning : LargeFilesState
    data class Done(val count: Int, val totalBytes: Long) : LargeFilesState
    data object Failed : LargeFilesState
}

sealed interface DownloadsState {
    data object Idle : DownloadsState
    data object Scanning : DownloadsState
    data class Done(
        val oldFilesCount: Int,
        val oldFilesBytes: Long,
        val apkCount: Int,
        val apkBytes: Long,
    ) : DownloadsState
    data object Failed : DownloadsState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    source: StorageInfoSource,
    private val scanner: LargeFilesScanner,
    private val oldDownloadsScanner: OldDownloadsScanner,
    private val oldApksScanner: OldApksScanner,
) : ViewModel() {
    val storage: StorageSnapshot = source.read()

    private val _largeFilesState = MutableStateFlow<LargeFilesState>(LargeFilesState.Idle)
    val largeFilesState: StateFlow<LargeFilesState> = _largeFilesState.asStateFlow()

    private val _downloadsState = MutableStateFlow<DownloadsState>(DownloadsState.Idle)
    val downloadsState: StateFlow<DownloadsState> = _downloadsState.asStateFlow()

    fun scanLargeFiles() {
        viewModelScope.launch {
            _largeFilesState.value = LargeFilesState.Scanning
            _largeFilesState.value = try {
                val result = scanner.scan()
                LargeFilesState.Done(count = result.files.size, totalBytes = result.totalBytes)
            } catch (e: SecurityException) {
                LargeFilesState.Failed
            } catch (e: Exception) {
                LargeFilesState.Failed
            }
        }
    }

    fun scanDownloads() {
        viewModelScope.launch {
            _downloadsState.value = DownloadsState.Scanning
            _downloadsState.value = try {
                val oldFiles = oldDownloadsScanner.scan()
                val apks = oldApksScanner.scan()
                DownloadsState.Done(
                    oldFilesCount = oldFiles.files.size,
                    oldFilesBytes = oldFiles.totalBytes,
                    apkCount = apks.candidates.size,
                    apkBytes = apks.totalBytes,
                )
            } catch (e: Exception) {
                DownloadsState.Failed
            }
        }
    }
}
