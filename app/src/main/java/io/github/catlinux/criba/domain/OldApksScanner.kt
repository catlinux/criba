package io.github.catlinux.criba.domain

data class ApkInfo(val packageName: String, val versionCode: Long)

fun interface ApkInspector {
    fun inspect(file: ScanFile): ApkInfo?
}

fun interface InstalledAppsSource {
    fun installedVersionCode(packageName: String): Long?
}

enum class ApkReason { ALREADY_INSTALLED, OLD }

data class ApkCandidate(val file: ScanFile, val reason: ApkReason)

data class ApkScanResult(val candidates: List<ApkCandidate>) {
    val totalBytes: Long get() = candidates.sumOf { it.file.sizeBytes }
}

class OldApksScanner(
    private val source: FileSource,
    private val inspector: ApkInspector,
    private val installed: InstalledAppsSource,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val minAgeDays: Int = 30,
) {
    init {
        require(minAgeDays > 0) { "minAgeDays debe ser positivo" }
    }

    suspend fun scan(): ApkScanResult {
        val minAgeSeconds = minAgeDays.toLong() * SECONDS_PER_DAY
        val now = nowEpochSeconds()
        val candidates = source.listFiles()
            .filter { it.displayName.endsWith(".apk", ignoreCase = true) }
            .mapNotNull { file ->
                val info = inspector.inspect(file)
                val installedVersion = info?.let { installed.installedVersionCode(it.packageName) }
                val age = now - file.modifiedEpochSeconds
                val reason = when {
                    info != null && installedVersion != null && installedVersion >= info.versionCode ->
                        ApkReason.ALREADY_INSTALLED
                    age >= minAgeSeconds -> ApkReason.OLD
                    else -> null
                }
                reason?.let { ApkCandidate(file = file, reason = it) }
            }
            .sortedWith(compareByDescending<ApkCandidate> { it.file.sizeBytes }.thenBy { it.file.displayName })
        return ApkScanResult(candidates)
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
