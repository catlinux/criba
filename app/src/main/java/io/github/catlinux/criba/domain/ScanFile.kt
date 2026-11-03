package io.github.catlinux.criba.domain

data class ScanFile(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    val relativePath: String?,
) {
    init {
        require(sizeBytes >= 0) { "sizeBytes no puede ser negativo" }
    }
}

data class ScanResult(val files: List<ScanFile>) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}
