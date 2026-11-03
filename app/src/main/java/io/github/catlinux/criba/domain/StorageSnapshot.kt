package io.github.catlinux.criba.domain

data class StorageSnapshot(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
}

fun interface StorageInfoSource {
    fun read(): StorageSnapshot
}
