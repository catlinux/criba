package io.github.catlinux.criba.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catlinux.criba.domain.ReviewBuilder
import io.github.catlinux.criba.domain.ReviewSelection
import io.github.catlinux.criba.domain.ScanFile
import io.github.catlinux.criba.domain.TrashFailure
import io.github.catlinux.criba.domain.TrashFailureItem
import io.github.catlinux.criba.domain.TrashManager
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReviewState {
    data class Reviewing(
        val selection: ReviewSelection,
        val showingConfirmation: Boolean = false,
        val confirmationError: Boolean = false,
    ) : ReviewState

    data object Moving : ReviewState

    data class Done(
        val movedCount: Int,
        val movedBytes: Long,
        val failed: List<TrashFailureItem>,
        val movedOriginalPaths: Set<String>,
    ) : ReviewState

    data class Failed(val detail: String?) : ReviewState
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val trashManager: TrashManager,
    @param:Named("storageRoot") private val storageRoot: String,
) : ViewModel() {
    private val _state = MutableStateFlow<ReviewState>(ReviewState.Reviewing(ReviewSelection(emptyList())))
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    fun load(files: List<ScanFile>) {
        if (_state.value is ReviewState.Moving) return
        _state.value = ReviewState.Reviewing(ReviewSelection(ReviewBuilder.build(files, storageRoot)))
    }

    fun toggle(id: String) {
        val current = _state.value as? ReviewState.Reviewing ?: return
        _state.value = current.copy(selection = current.selection.toggle(id), confirmationError = false)
    }

    fun selectAll() {
        val current = _state.value as? ReviewState.Reviewing ?: return
        _state.value = current.copy(selection = current.selection.selectAllSelectable(), confirmationError = false)
    }

    fun clear() {
        val current = _state.value as? ReviewState.Reviewing ?: return
        _state.value = current.copy(selection = current.selection.clear(), confirmationError = false)
    }

    fun requestConfirmation() {
        val current = _state.value as? ReviewState.Reviewing ?: return
        if (current.selection.selectedCount == 0) return
        _state.value = current.copy(showingConfirmation = true, confirmationError = false)
    }

    fun dismissConfirmation() {
        val current = _state.value as? ReviewState.Reviewing ?: return
        _state.value = current.copy(showingConfirmation = false, confirmationError = false)
    }

    fun confirmMove() {
        val current = _state.value as? ReviewState.Reviewing ?: return
        if (!current.showingConfirmation) return
        val confirmed = current.selection.confirm()
        if (confirmed == null) {
            _state.value = current.copy(confirmationError = true)
            return
        }

        _state.value = ReviewState.Moving
        viewModelScope.launch {
            try {
                val result = trashManager.moveToTrash(confirmed)
                _state.value = ReviewState.Done(
                    movedCount = result.moved.size,
                    movedBytes = result.movedBytes,
                    failed = result.failed,
                    movedOriginalPaths = result.moved.map { it.originalPath }.toSet(),
                )
            } catch (e: Exception) {
                _state.value = ReviewState.Failed(e.message)
            }
        }
    }
}
