package io.github.catlinux.criba.domain

/**
 * Revisión de duplicados: por grupo siempre queda una copia conservada y solo se pueden marcar
 * copias elegibles. La conservada nunca se marca y una copia multimedia tampoco.
 */
class DuplicatesReview(
    val groups: List<DuplicateGroup> = emptyList(),
    private val storageRoot: String = "",
    private val keptByGroup: Map<String, String> = emptyMap(),
    private val deselected: Set<String> = emptySet(),
) {
    private val selections: Map<String, DuplicateSelection> = groups.associate { group ->
        val requestedKept = keptByGroup[group.sha256]
            ?.takeIf { id -> group.files.any { it.id == id } }
            ?: KeepPolicy.chooseKept(group.files).id
        group.sha256 to DuplicateSelection(group, requestedKept)
    }

    private val statuses: Map<String, ReviewStatus> = selections.values
        .flatMap { selection ->
            ReviewBuilder.build(selection.toDelete, storageRoot).map { it.file.id to it.status }
        }
        .toMap()

    private val selectableIds: Set<String> = statuses.filterValues { it == ReviewStatus.SELECTABLE }.keys

    fun selectionOf(groupHash: String): DuplicateSelection? = selections[groupHash]

    fun statusOf(fileId: String): ReviewStatus? = statuses[fileId]

    fun isMarked(fileId: String): Boolean = fileId in selectableIds && fileId !in deselected

    val markedFiles: List<ScanFile> = groups.flatMap { group ->
        selections.getValue(group.sha256).toDelete.filter { isMarked(it.id) }
    }
    val markedCount: Int get() = markedFiles.size
    val markedBytes: Long get() = markedFiles.sumOf { it.sizeBytes }
    val blockedOrBackupCount: Int get() = statuses.values.count { it != ReviewStatus.SELECTABLE }
    val reclaimableBytes: Long get() = groups.sumOf { selections.getValue(it.sha256).reclaimableBytes }

    fun withKept(groupHash: String, fileId: String): DuplicatesReview {
        val selection = selections[groupHash] ?: return this
        require(selection.group.files.any { it.id == fileId }) { "la copia debe pertenecer al grupo" }
        if (fileId == selection.keptId) return this
        return DuplicatesReview(groups, storageRoot, keptByGroup + (groupHash to fileId), deselected - fileId)
    }

    fun toggle(fileId: String): DuplicatesReview {
        if (fileId !in selectableIds) return this
        val updated = deselected.toMutableSet()
        if (!updated.add(fileId)) updated.remove(fileId)
        return DuplicatesReview(groups, storageRoot, keptByGroup, updated)
    }

    fun selectAll(): DuplicatesReview =
        DuplicatesReview(groups, storageRoot, keptByGroup, deselected - selectableIds)

    fun clear(): DuplicatesReview =
        DuplicatesReview(groups, storageRoot, keptByGroup, deselected + selectableIds)

    fun removeFiles(fileIds: Set<String>): DuplicatesReview {
        if (fileIds.isEmpty()) return this
        val remaining = groups.mapNotNull { group ->
            val files = group.files.filterNot { it.id in fileIds }
            if (files.size < 2) null else group.copy(files = files)
        }
        val remainingKept = keptByGroup.filter { (hash, id) ->
            remaining.any { group -> group.sha256 == hash && group.files.any { file -> file.id == id } }
        }
        return DuplicatesReview(remaining, storageRoot, remainingKept, deselected - fileIds)
    }

    fun confirm(): ConfirmedDeletion? =
        ConfirmedDeletion.confirm(markedFiles.mapNotNull { it.toDeletionCandidate(storageRoot) }, markedBytes)
}
