package io.github.catlinux.criba.domain

enum class ActionType { MOVED_TO_TRASH, RESTORED, PURGED }

enum class ActionResult { PENDING, SUCCESS, FAILED }

data class ActionRecord(
    val id: Long,
    val atEpochMillis: Long,
    val type: ActionType,
    val originalPath: String,
    val trashId: String?,
    val bytes: Long,
    val result: ActionResult,
    val message: String?,
)

interface ActionLog {
    /** Registra la intención antes de actuar y devuelve el id con el que completarla. */
    suspend fun begin(type: ActionType, originalPath: String, trashId: String?, bytes: Long): Long

    suspend fun complete(id: Long, result: ActionResult, message: String? = null)

    suspend fun recent(limit: Int = 200): List<ActionRecord>
}
