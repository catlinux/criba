package io.github.catlinux.criba.domain

object KeepPolicy {
    fun chooseKept(files: List<ScanFile>): ScanFile {
        require(files.isNotEmpty()) { "no se puede elegir una copia de una lista vacía" }
        val preferred = files.filterNot { it.isInDownloadOrScreenshots() }
        val pool = preferred.ifEmpty { files }
        return pool.sortedWith(
            compareBy<ScanFile> { it.modifiedEpochSeconds }
                .thenBy { it.relativePath ?: "" }
                .thenBy { it.displayName },
        ).first()
    }

    private fun ScanFile.isInDownloadOrScreenshots(): Boolean {
        val path = relativePath ?: return false
        return path.startsWith("Download/", ignoreCase = true) ||
            path.contains("Screenshots", ignoreCase = true)
    }
}

/** Una selección siempre conserva exactamente una copia: no se puede borrar el grupo entero. */
class DuplicateSelection(
    val group: DuplicateGroup,
    keptId: String = KeepPolicy.chooseKept(group.files).id,
) {
    val keptId: String = keptId.also {
        require(group.files.any { file -> file.id == it }) { "la copia conservada debe pertenecer al grupo" }
    }
    val keptFile: ScanFile = group.files.first { it.id == keptId }
    val toDelete: List<ScanFile> = group.files.filterNot { it.id == keptId }
    val reclaimableBytes: Long = group.sizeBytes * toDelete.size

    fun withKept(id: String): DuplicateSelection = DuplicateSelection(group, id)
}
