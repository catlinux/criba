package io.github.catlinux.criba.domain

data class FolderSummary(
    val path: String,
    val name: String,
    val fileCount: Int,
    val totalBytes: Long,
    val newestModifiedEpochSeconds: Long?,
) {
    init {
        require(fileCount >= 0) { "fileCount no puede ser negativo" }
        require(totalBytes >= 0) { "totalBytes no puede ser negativo" }
    }
}

fun interface FolderSource {
    suspend fun listTopLevelFolders(): List<FolderSummary>
}

enum class FolderReason { EMPTY, INACTIVE }

data class FolderCandidate(val folder: FolderSummary, val reason: FolderReason)

data class FolderScanResult(val candidates: List<FolderCandidate>) {
    val totalBytes: Long get() = candidates.sumOf { it.folder.totalBytes }
    val emptyCount: Int get() = candidates.count { it.reason == FolderReason.EMPTY }
    val inactiveCount: Int get() = candidates.count { it.reason == FolderReason.INACTIVE }
}

class UnusedFoldersScanner(
    private val source: FolderSource,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val minAgeDays: Int = 365,
) {
    init {
        require(minAgeDays > 0) { "minAgeDays debe ser positivo" }
    }

    suspend fun scan(): FolderScanResult {
        val now = nowEpochSeconds()
        val minAgeSeconds = minAgeDays.toLong() * SECONDS_PER_DAY
        val candidates = source.listTopLevelFolders()
            .mapNotNull { folder ->
                val reason = when {
                    folder.fileCount == 0 -> FolderReason.EMPTY
                    folder.newestModifiedEpochSeconds != null &&
                        now - folder.newestModifiedEpochSeconds >= minAgeSeconds -> FolderReason.INACTIVE
                    else -> null
                }
                reason?.let { FolderCandidate(folder = folder, reason = it) }
            }
            .sortedWith(compareByDescending<FolderCandidate> { it.folder.totalBytes }.thenBy { it.folder.name })
        return FolderScanResult(candidates)
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
