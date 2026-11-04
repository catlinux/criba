package io.github.catlinux.criba.domain

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

fun interface ContentHasher {
    /** null cuando el archivo no se puede leer: nunca se supone que sea igual a otro. */
    suspend fun sha256(file: ScanFile): String?
}

data class DuplicateGroup(
    val sha256: String,
    val files: List<ScanFile>,
    val sizeBytes: Long,
) {
    init {
        require(files.size >= 2) { "un grupo de duplicados necesita al menos dos archivos" }
    }

    val reclaimableBytes: Long get() = sizeBytes * (files.size - 1)
}

data class DuplicateProgress(val filesDone: Int, val filesTotal: Int)

class DuplicatesScanner(
    private val source: FileSource,
    private val hasher: ContentHasher,
) {
    suspend fun scan(onProgress: (DuplicateProgress) -> Unit = {}): List<DuplicateGroup> {
        val files = source.listFiles()
            .filter { it.sizeBytes > 0 }
            .distinctBy { Triple(it.relativePath ?: "", it.displayName, it.sizeBytes) }
        val groupsBySize = files.groupBy { it.sizeBytes }.filterValues { it.size >= 2 }
        val filesTotal = groupsBySize.values.sumOf { it.size }
        var filesDone = 0
        val groups = mutableListOf<DuplicateGroup>()

        for ((sizeBytes, sameSize) in groupsBySize) {
            val byHash = mutableMapOf<String, MutableList<ScanFile>>()
            for (file in sameSize) {
                coroutineContext.ensureActive()
                val hash = hasher.sha256(file)
                filesDone++
                onProgress(DuplicateProgress(filesDone = filesDone, filesTotal = filesTotal))
                if (hash != null) byHash.getOrPut(hash) { mutableListOf() }.add(file)
                coroutineContext.ensureActive()
            }
            for ((hash, matches) in byHash) {
                if (matches.size < 2) continue
                groups += DuplicateGroup(
                    sha256 = hash,
                    files = matches.sortedWith(
                        compareBy<ScanFile> { it.relativePath ?: "" }.thenBy { it.displayName },
                    ),
                    sizeBytes = sizeBytes,
                )
            }
        }

        return groups.sortedWith(
            compareByDescending<DuplicateGroup> { it.reclaimableBytes }.thenBy { it.sha256 },
        )
    }
}
