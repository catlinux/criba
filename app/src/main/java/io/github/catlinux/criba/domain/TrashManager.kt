package io.github.catlinux.criba.domain

enum class TrashFailure {
    PROTECTED,
    NOT_A_REGULAR_FILE,
    SYMLINK,
    MISSING,
    CHANGED_SINCE_SCAN,
    DIFFERENT_VOLUME,
    TARGET_EXISTS,
    VERIFY_FAILED,
    OUTSIDE_TRASH,
    NOT_IN_TRASH,
    TRASH_UNAVAILABLE,
    IO_ERROR,
}

data class TrashedFile(val trashId: String, val originalPath: String, val bytes: Long)

data class TrashFailureItem(val path: String, val failure: TrashFailure, val detail: String?)

data class MoveToTrashResult(val moved: List<TrashedFile>, val failed: List<TrashFailureItem>) {
    val movedBytes: Long get() = moved.sumOf { it.bytes }
}

data class RestoreResult(
    val restoredPath: String?,
    val renamed: Boolean,
    val failure: TrashFailure?,
    val detail: String?,
)

data class PurgeResult(val purged: List<TrashedFile>, val failed: List<TrashFailureItem>) {
    val purgedBytes: Long get() = purged.sumOf { it.bytes }
}

interface TrashManager {
    /** Nunca acepta nada que no haya pasado por la confirmación explícita del usuario. */
    suspend fun moveToTrash(confirmed: ConfirmedDeletion): MoveToTrashResult

    suspend fun restore(trashId: String): RestoreResult

    suspend fun purge(confirmed: ConfirmedPurge): PurgeResult

    suspend fun purgeExpired(nowEpochMillis: Long, retentionDays: Int = 30): PurgeResult
}
