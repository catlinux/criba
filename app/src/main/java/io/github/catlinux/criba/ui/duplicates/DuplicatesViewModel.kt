package io.github.catlinux.criba.ui.duplicates

import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catlinux.criba.domain.ConfirmedDeletion
import io.github.catlinux.criba.domain.DeletionCandidate
import io.github.catlinux.criba.domain.DuplicateProgress
import io.github.catlinux.criba.domain.DuplicateRecheck
import io.github.catlinux.criba.domain.DuplicatesReview
import io.github.catlinux.criba.domain.DuplicatesScanner
import io.github.catlinux.criba.domain.TrashFailureItem
import io.github.catlinux.criba.domain.TrashManager
import io.github.catlinux.criba.domain.toDeletionCandidate
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DuplicatesState {
    data object Idle : DuplicatesState
    data class Scanning(val progress: DuplicateProgress?) : DuplicatesState
    data class Reviewing(
        val review: DuplicatesReview,
        val showingConfirmation: Boolean = false,
        val confirmationError: Boolean = false,
    ) : DuplicatesState

    data object Moving : DuplicatesState
    data class Done(
        val movedCount: Int,
        val movedBytes: Long,
        val failed: List<TrashFailureItem>,
        val notVerifiedCount: Int,
        val remaining: DuplicatesReview,
    ) : DuplicatesState

    data class Failed(val detail: String?) : DuplicatesState
}

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val scanner: DuplicatesScanner,
    private val recheck: DuplicateRecheck,
    private val trashManager: TrashManager,
    @param:Named("storageRoot") private val storageRoot: String,
) : ViewModel() {
    private val _state = MutableStateFlow<DuplicatesState>(DuplicatesState.Idle)
    val state: StateFlow<DuplicatesState> = _state.asStateFlow()
    private var scanJob: Job? = null

    fun startScan() {
        if (!hasStorageAccess()) return
        if (_state.value is DuplicatesState.Scanning) return
        scanJob?.cancel()
        _state.value = DuplicatesState.Scanning(progress = null)
        scanJob = viewModelScope.launch {
            try {
                val groups = scanner.scan { progress ->
                    _state.value = DuplicatesState.Scanning(progress)
                }
                // Nada marcado al empezar: el usuario elige qué copias mover.
                _state.value = DuplicatesState.Reviewing(DuplicatesReview(groups, storageRoot).clear())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DuplicatesState.Failed(e.message)
            }
        }
    }

    fun cancel() {
        scanJob?.cancel()
        scanJob = null
        _state.value = DuplicatesState.Idle
    }

    fun toggle(fileId: String) = updateReview { it.toggle(fileId) }

    fun selectAll() = updateReview { it.selectAll() }

    fun clear() = updateReview { it.clear() }

    fun withKept(groupHash: String, fileId: String) = updateReview { it.withKept(groupHash, fileId) }

    fun requestConfirmation() {
        val current = _state.value as? DuplicatesState.Reviewing ?: return
        if (current.review.markedCount == 0) return
        _state.value = current.copy(showingConfirmation = true, confirmationError = false)
    }

    fun dismissConfirmation() {
        val current = _state.value as? DuplicatesState.Reviewing ?: return
        _state.value = current.copy(showingConfirmation = false, confirmationError = false)
    }

    fun resumeReview() {
        val current = _state.value as? DuplicatesState.Done ?: return
        if (current.remaining.groups.isEmpty()) {
            _state.value = DuplicatesState.Idle
        } else {
            _state.value = DuplicatesState.Reviewing(current.remaining)
        }
    }

    fun confirmMove() {
        val current = _state.value as? DuplicatesState.Reviewing ?: return
        if (!current.showingConfirmation) return
        val review = current.review
        _state.value = DuplicatesState.Moving
        viewModelScope.launch {
            try {
                val verified = mutableListOf<DeletionCandidate>()
                val fileIdByPath = mutableMapOf<String, String>()
                var notVerified = 0

                for (group in review.groups) {
                    val selection = review.selectionOf(group.sha256) ?: continue
                    val marked = selection.toDelete.filter { review.isMarked(it.id) }
                    if (marked.isEmpty()) continue
                    val confirmedIds = recheck.confirmedDeletions(selection).map { it.id }.toSet()
                    for (file in marked) {
                        if (file.id in confirmedIds) {
                            val candidate = file.toDeletionCandidate(storageRoot)
                            if (candidate != null) {
                                verified += candidate
                                fileIdByPath[candidate.path] = file.id
                            } else {
                                notVerified++
                            }
                        } else {
                            notVerified++
                        }
                    }
                }

                if (verified.isEmpty()) {
                    _state.value = DuplicatesState.Done(
                        movedCount = 0,
                        movedBytes = 0,
                        failed = emptyList(),
                        notVerifiedCount = notVerified,
                        remaining = review,
                    )
                    return@launch
                }

                val confirmed = ConfirmedDeletion.confirm(verified, verified.sumOf { it.sizeBytes })
                if (confirmed == null) {
                    _state.value = DuplicatesState.Reviewing(review, confirmationError = true)
                    return@launch
                }

                val result = trashManager.moveToTrash(confirmed)
                val movedIds = result.moved.mapNotNull { fileIdByPath[it.originalPath] }.toSet()
                _state.value = DuplicatesState.Done(
                    movedCount = result.moved.size,
                    movedBytes = result.movedBytes,
                    failed = result.failed,
                    notVerifiedCount = notVerified,
                    remaining = review.removeFiles(movedIds),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DuplicatesState.Failed(e.message)
            }
        }
    }

    private fun hasStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun updateReview(transform: (DuplicatesReview) -> DuplicatesReview) {
        val current = _state.value as? DuplicatesState.Reviewing ?: return
        _state.value = current.copy(
            review = transform(current.review),
            showingConfirmation = false,
            confirmationError = false,
        )
    }
}
