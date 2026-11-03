package io.github.catlinux.criba.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catlinux.criba.domain.LargeFilesScanner
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    source: StorageInfoSource,
    private val scanner: LargeFilesScanner,
) : ViewModel() {
    val storage: StorageSnapshot = source.read()

    private val _largeFilesState = MutableStateFlow<LargeFilesState>(LargeFilesState.Idle)
    val largeFilesState: StateFlow<LargeFilesState> = _largeFilesState.asStateFlow()

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
}
