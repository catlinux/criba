package io.github.catlinux.criba.domain

data class AppSizes(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    init {
        require(appBytes >= 0) { "appBytes no puede ser negativo" }
        require(dataBytes >= 0) { "dataBytes no puede ser negativo" }
        require(cacheBytes >= 0) { "cacheBytes no puede ser negativo" }
    }

    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

data class AppUsageInfo(
    val packageName: String,
    val label: String,
    val sizes: AppSizes?,
    val lastUsedEpochSeconds: Long?,
    val firstInstallEpochSeconds: Long,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean = false,
    val isProtectedRole: Boolean = false,
)

fun interface AppSource {
    suspend fun listApps(): List<AppUsageInfo>
}

enum class AppReason { UNUSED_SINCE, NO_USE_RECORDED }

data class AppCandidate(val app: AppUsageInfo, val reason: AppReason)

data class AppScanResult(
    val candidates: List<AppCandidate>,
    val totalKnownBytes: Long,
    val unknownSizeCount: Int,
)

class UnusedAppsScanner(
    private val source: AppSource,
    private val ownPackageName: String,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val minUnusedDays: Int = 90,
) {
    init {
        require(minUnusedDays > 0) { "minUnusedDays debe ser positivo" }
        require(ownPackageName.isNotEmpty()) { "ownPackageName no puede estar vacío" }
    }

    suspend fun scan(): AppScanResult {
        val now = nowEpochSeconds()
        val minUnusedSeconds = minUnusedDays.toLong() * SECONDS_PER_DAY
        val candidates = source.listApps()
            .asSequence()
            .filterNot { it.packageName == ownPackageName }
            .filterNot { it.isSystem && !it.isUpdatedSystem }
            .filterNot { it.isProtectedRole }
            .mapNotNull { app ->
                val reason = if (app.lastUsedEpochSeconds != null) {
                    if (now - app.lastUsedEpochSeconds >= minUnusedSeconds) AppReason.UNUSED_SINCE else null
                } else {
                    if (now - app.firstInstallEpochSeconds >= minUnusedSeconds) AppReason.NO_USE_RECORDED else null
                }
                reason?.let { AppCandidate(app, it) }
            }
            .sortedWith(
                compareBy<AppCandidate> { it.app.sizes == null }
                    .thenByDescending { it.app.sizes?.totalBytes ?: 0L }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
                    .thenBy { it.app.packageName },
            )
            .toList()

        return AppScanResult(
            candidates = candidates,
            totalKnownBytes = candidates.sumOf { it.app.sizes?.totalBytes ?: 0L },
            unknownSizeCount = candidates.count { it.app.sizes == null },
        )
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
