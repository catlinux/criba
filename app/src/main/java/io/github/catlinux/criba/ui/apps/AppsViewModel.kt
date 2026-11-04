package io.github.catlinux.criba.ui.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.catlinux.criba.domain.AppScanResult
import io.github.catlinux.criba.domain.AppSource
import io.github.catlinux.criba.domain.AppUsageInfo
import io.github.catlinux.criba.domain.UnusedAppsScanner
import io.github.catlinux.criba.domain.UsageAccess
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppsState {
    data object Loading : AppsState
    data class NoUsageAccess(val apps: List<AppUsageInfo>) : AppsState
    data class Loaded(val result: AppScanResult, val allApps: List<AppUsageInfo>) : AppsState
    data class Failed(val detail: String?) : AppsState
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val scanner: UnusedAppsScanner,
    private val usageAccess: UsageAccess,
    private val source: AppSource,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow<AppsState>(AppsState.Loading)
    val state: StateFlow<AppsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = AppsState.Loading
            _state.value = try {
                if (usageAccess.isGranted()) {
                    AppsState.Loaded(result = scanner.scan(), allApps = source.listApps())
                } else {
                    val apps = source.listApps()
                        .filterNot { it.packageName == context.packageName }
                        .filterNot { it.isSystem && !it.isUpdatedSystem }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                    AppsState.NoUsageAccess(apps)
                }
            } catch (e: Exception) {
                AppsState.Failed(e.message)
            }
        }
    }
}
