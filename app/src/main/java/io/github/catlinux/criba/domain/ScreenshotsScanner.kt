package io.github.catlinux.criba.domain

enum class AgeBucket { DAYS_30_TO_90, DAYS_90_TO_365, OVER_365 }

data class ScreenshotGroup(val bucket: AgeBucket, val files: List<ScanFile>) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

data class ScreenshotsResult(val groups: List<ScreenshotGroup>) {
    val totalBytes: Long get() = groups.sumOf { it.totalBytes }
    val totalCount: Int get() = groups.sumOf { it.files.size }
}

class ScreenshotsScanner(
    private val source: FileSource,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val minAgeDays: Int = 30,
) {
    init {
        require(minAgeDays > 0) { "minAgeDays debe ser positivo" }
    }

    suspend fun scan(): ScreenshotsResult {
        // En Android 8-9 MediaStore no entrega RELATIVE_PATH, así que allí no se detectan capturas.
        val now = nowEpochSeconds()
        val minAgeSeconds = minAgeDays.toLong() * SECONDS_PER_DAY
        val grouped = source.listFiles()
            .filter { it.isScreenshot() }
            .mapNotNull { file ->
                val ageSeconds = now - file.modifiedEpochSeconds
                if (ageSeconds < minAgeSeconds) null else file to bucketFor(ageSeconds)
            }
            .groupBy({ it.second }, { it.first })

        val groups = AgeBucket.entries.map { bucket ->
            ScreenshotGroup(
                bucket = bucket,
                files = grouped[bucket].orEmpty()
                    .sortedWith(compareByDescending<ScanFile> { it.sizeBytes }.thenBy { it.displayName }),
            )
        }
        return ScreenshotsResult(groups)
    }

    private fun ScanFile.isScreenshot(): Boolean =
        relativePath?.split('/')?.any { it.equals("Screenshots", ignoreCase = true) } == true

    private fun bucketFor(ageSeconds: Long): AgeBucket =
        when {
            ageSeconds < 90 * SECONDS_PER_DAY -> AgeBucket.DAYS_30_TO_90
            ageSeconds < 365 * SECONDS_PER_DAY -> AgeBucket.DAYS_90_TO_365
            else -> AgeBucket.OVER_365
        }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
