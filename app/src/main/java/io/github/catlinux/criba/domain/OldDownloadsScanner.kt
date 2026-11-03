package io.github.catlinux.criba.domain

class OldDownloadsScanner(
    private val source: FileSource,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val minAgeDays: Int = 90,
) {
    init {
        require(minAgeDays > 0) { "minAgeDays debe ser positivo" }
    }

    suspend fun scan(): ScanResult {
        val minAgeSeconds = minAgeDays.toLong() * SECONDS_PER_DAY
        val now = nowEpochSeconds()
        val files = source.listFiles()
            .filter { !it.displayName.endsWith(".apk", ignoreCase = true) }
            .filter { now - it.modifiedEpochSeconds >= minAgeSeconds }
            .sortedWith(compareByDescending<ScanFile> { it.sizeBytes }.thenBy { it.displayName })
        return ScanResult(files)
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
