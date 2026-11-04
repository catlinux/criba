package io.github.catlinux.criba.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catlinux.criba.domain.ConfirmedPurge
import io.github.catlinux.criba.domain.TrashFailure
import io.github.catlinux.criba.domain.TrashFailureItem
import io.github.catlinux.criba.domain.TrashIndex
import io.github.catlinux.criba.domain.TrashManager
import io.github.catlinux.criba.domain.TrashSelection
import io.github.catlinux.criba.domain.TrashItem
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TrashOperationMessage {
    data class Restored(
        val restoredPaths: List<String>,
        val renamedPaths: List<String>,
        val failed: List<TrashFailureItem>,
    ) : TrashOperationMessage

    data class Purged(
        val purgedCount: Int,
        val purgedBytes: Long,
        val failed: List<TrashFailureItem>,
    ) : TrashOperationMessage
}

sealed interface TrashScreenState {
    data object Loading : TrashScreenState
    data class Loaded(
        val selection: TrashSelection,
        val totalBytes: Long,
        val nowEpochMillis: Long,
        val showPurgeConfirmation: Boolean = false,
        val lastMessage: TrashOperationMessage? = null,
        val loadError: String? = null,
    ) : TrashScreenState
    data object Working : TrashScreenState
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val manager: TrashManager,
    private val index: TrashIndex,
    @param:Named("epochMillisClock") private val nowEpochMillis: () -> Long,
) : ViewModel() {
    private val _state = MutableStateFlow<TrashScreenState>(TrashScreenState.Loading)
    val state: StateFlow<TrashScreenState> = _state.asStateFlow()

    fun load() {
        if (_state.value is TrashScreenState.Working) return
        viewModelScope.launch {
            _state.value = TrashScreenState.Loading
            refresh()
        }
    }

    fun toggle(id: String) = updateSelection { it.toggle(id) }

    fun selectAll() = updateSelection { it.selectAll() }

    fun clear() = updateSelection { it.clear() }

    fun requestPurge() {
        val current = _state.value as? TrashScreenState.Loaded ?: return
        if (current.selection.selectedCount == 0) return
        _state.value = current.copy(showPurgeConfirmation = true)
    }

    fun dismissPurge() {
        val current = _state.value as? TrashScreenState.Loaded ?: return
        _state.value = current.copy(showPurgeConfirmation = false)
    }

    fun restoreSelected() {
        val current = _state.value as? TrashScreenState.Loaded ?: return
        val selected = current.selection.selectedItems
        if (selected.isEmpty()) return
        _state.value = TrashScreenState.Working
        viewModelScope.launch {
            val restored = mutableListOf<String>()
            val renamed = mutableListOf<String>()
            val failed = mutableListOf<TrashFailureItem>()
            for (item in selected) {
                try {
                    val result = manager.restore(item.id)
                    if (result.failure != null) {
                        failed += TrashFailureItem(item.originalPath, result.failure, result.detail)
                    } else {
                        val path = result.restoredPath
                        if (path != null) {
                            restored += path
                            if (result.renamed) renamed += path
                        } else {
                            failed += TrashFailureItem(item.originalPath, TrashFailure.VERIFY_FAILED, result.detail)
                        }
                    }
                } catch (e: Exception) {
                    failed += TrashFailureItem(item.originalPath, TrashFailure.IO_ERROR, e.message)
                }
            }
            refresh(TrashOperationMessage.Restored(restored, renamed, failed))
        }
    }

    fun confirmPurge() {
        val current = _state.value as? TrashScreenState.Loaded ?: return
        if (!current.showPurgeConfirmation) return
        val confirmed: ConfirmedPurge = current.selection.confirmPurge() ?: return
        _state.value = TrashScreenState.Working
        viewModelScope.launch {
            val message = try {
                val result = manager.purge(confirmed)
                TrashOperationMessage.Purged(result.purged.size, result.purgedBytes, result.failed)
            } catch (e: Exception) {
                TrashOperationMessage.Purged(
                    purgedCount = 0,
                    purgedBytes = 0,
                    failed = confirmed.items.map { TrashFailureItem(it.originalPath, TrashFailure.IO_ERROR, e.message) },
                )
            }
            refresh(message)
        }
    }

    private fun updateSelection(transform: (TrashSelection) -> TrashSelection) {
        val current = _state.value as? TrashScreenState.Loaded ?: return
        _state.value = current.copy(selection = transform(current.selection), showPurgeConfirmation = false)
    }

    private suspend fun refresh(message: TrashOperationMessage? = null) {
        try {
            val items: List<TrashItem> = index.inTrash()
            val bytes = index.inTrashBytes()
            _state.value = TrashScreenState.Loaded(
                selection = TrashSelection(items),
                totalBytes = bytes,
                nowEpochMillis = nowEpochMillis(),
                lastMessage = message,
            )
        } catch (e: Exception) {
            _state.value = TrashScreenState.Loaded(
                selection = TrashSelection(emptyList()),
                totalBytes = 0,
                nowEpochMillis = nowEpochMillis(),
                lastMessage = message,
                loadError = e.message,
            )
        }
    }
}
