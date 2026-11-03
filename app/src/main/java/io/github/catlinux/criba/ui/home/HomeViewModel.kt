package io.github.catlinux.criba.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catlinux.criba.domain.StorageInfoSource
import io.github.catlinux.criba.domain.StorageSnapshot
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    source: StorageInfoSource,
) : ViewModel() {
    val storage: StorageSnapshot = source.read()
}
