package io.github.catlinux.criba.domain

fun ScanFile.toDeletionCandidate(storageRoot: String): DeletionCandidate? {
    val pathRelativeToRoot = relativePath ?: return null
    if (sizeBytes < 0) return null
    val root = storageRoot.trimEnd('/', '\\')
    val relative = pathRelativeToRoot.trimStart('/', '\\')
    val path = buildString {
        append(root)
        append('/')
        if (relative.isNotEmpty()) {
            append(relative.trimEnd('/', '\\'))
            append('/')
        }
        append(displayName)
    }
    return DeletionCandidate(path, displayName, sizeBytes, modifiedEpochSeconds)
}

enum class ReviewStatus { SELECTABLE, NEEDS_BACKUP, BLOCKED }

data class ReviewItem(
    val file: ScanFile,
    val candidate: DeletionCandidate?,
    val status: ReviewStatus,
)

object ReviewBuilder {
    fun build(files: List<ScanFile>, storageRoot: String): List<ReviewItem> =
        files.map { file ->
            val candidate = file.toDeletionCandidate(storageRoot)
            val status = when (candidate?.let(DeletionPolicy::evaluate)) {
                DeletionVerdict.Allowed -> ReviewStatus.SELECTABLE
                DeletionVerdict.RequiresVerifiedBackup -> ReviewStatus.NEEDS_BACKUP
                else -> ReviewStatus.BLOCKED
            }
            ReviewItem(file, candidate, status)
        }
}

class ReviewSelection(
    items: List<ReviewItem>,
    selectedIds: Set<String> = emptySet(),
) {
    val items: List<ReviewItem> = items.toList()
    private val selectableIds = this.items.filter { it.status == ReviewStatus.SELECTABLE }.map { it.file.id }.toSet()
    val selectedIds: Set<String> = selectedIds.filter { it in selectableIds }.toSet()

    val selectedCandidates: List<DeletionCandidate> = items
        .filter { it.status == ReviewStatus.SELECTABLE && it.file.id in selectedIds }
        .mapNotNull { it.candidate }

    val selectedBytes: Long get() = selectedCandidates.sumOf { it.sizeBytes }
    val selectedCount: Int get() = selectedCandidates.size

    fun toggle(id: String): ReviewSelection {
        val item = items.firstOrNull { it.file.id == id && it.status == ReviewStatus.SELECTABLE }
            ?: return this
        val updated = selectedIds.toMutableSet()
        if (!updated.add(item.file.id)) updated.remove(item.file.id)
        return ReviewSelection(items, updated)
    }

    fun selectAllSelectable(): ReviewSelection =
        ReviewSelection(items, selectedIds + items.filter { it.status == ReviewStatus.SELECTABLE }.map { it.file.id })

    fun clear(): ReviewSelection = ReviewSelection(items)

    fun confirm(): ConfirmedDeletion? = ConfirmedDeletion.confirm(selectedCandidates, selectedBytes)
}
