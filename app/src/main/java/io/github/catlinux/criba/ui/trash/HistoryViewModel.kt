package io.github.catlinux.criba.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catlinux.criba.domain.ActionLog
import io.github.catlinux.criba.domain.ActionRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Loaded(val records: List<ActionRecord>) : HistoryState
    data class Failed(val detail: String?) : HistoryState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val actionLog: ActionLog,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = HistoryState.Loading
            _state.value = try {
                HistoryState.Loaded(actionLog.recent(200))
            } catch (e: Exception) {
                HistoryState.Failed(e.message)
            }
        }
    }
}
