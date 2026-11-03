package io.github.catlinux.criba.domain

enum class ReportCategory { LARGE_FILES, SCREENSHOTS, OLD_DOWNLOADS, APKS }

data class RecoverableSummary(
    val uniqueCount: Int,
    val uniqueBytes: Long,
    val categories: Set<ReportCategory>,
)

object RecoverableCalculator {
    fun summarize(found: Map<ReportCategory, List<ScanFile>>): RecoverableSummary {
        // El mismo archivo llega con ids distintos según lo lea MediaStore o java.io.File.
        val unique = LinkedHashSet<FileIdentity>()
        for (file in found.values.flatten()) {
            unique.add(FileIdentity(file.relativePath ?: "", file.displayName, file.sizeBytes))
        }
        return RecoverableSummary(
            uniqueCount = unique.size,
            uniqueBytes = unique.sumOf { it.sizeBytes },
            categories = found.keys,
        )
    }

    private data class FileIdentity(val relativePath: String, val displayName: String, val sizeBytes: Long)
}
