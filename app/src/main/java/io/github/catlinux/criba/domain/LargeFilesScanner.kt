package io.github.catlinux.criba.domain

class LargeFilesScanner(
    private val source: FileSource,
    private val minSizeBytes: Long = DEFAULT_MIN_SIZE_BYTES,
) {
    init {
        require(minSizeBytes > 0) { "minSizeBytes debe ser positivo" }
    }

    suspend fun scan(): ScanResult {
        val files = source.listFiles()
            .filter { it.sizeBytes >= minSizeBytes }
            .sortedWith(compareByDescending<ScanFile> { it.sizeBytes }.thenBy { it.displayName })
        return ScanResult(files)
    }

    companion object {
        const val DEFAULT_MIN_SIZE_BYTES: Long = 100_000_000L
    }
}
