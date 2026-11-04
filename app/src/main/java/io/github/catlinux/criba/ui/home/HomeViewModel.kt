package io.github.catlinux.criba.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catlinux.criba.domain.StorageInfoSource
import io.github.catlinux.criba.domain.StorageSnapshot
import io.github.catlinux.criba.domain.TrashIndex
import io.github.catlinux.criba.domain.TrashManager
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    source: StorageInfoSource,
    private val trashManager: TrashManager,
    private val trashIndex: TrashIndex,
    @param:Named("epochMillisClock") private val nowEpochMillis: () -> Long,
) : ViewModel() {
    val storage: StorageSnapshot = source.read()

    private val _trashBytes = MutableStateFlow(0L)
    val trashBytes: StateFlow<Long> = _trashBytes.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                trashManager.purgeExpired(nowEpochMillis(), retentionDays = 30)
            } catch (e: Exception) {
                // El arranque y la pantalla inicial deben seguir disponibles si la papelera falla.
            }
            refreshTrashBytesNow()
        }
    }

    fun refreshTrashBytes() {
        viewModelScope.launch { refreshTrashBytesNow() }
    }

    private suspend fun refreshTrashBytesNow() {
        try {
            _trashBytes.value = trashIndex.inTrashBytes()
        } catch (e: Exception) {
            _trashBytes.value = 0L
        }
    }
}
