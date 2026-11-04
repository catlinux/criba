package io.github.catlinux.criba.domain

/** Implementación en memoria del registro para tests y pasos futuros de la UI. */
class FakeActionLog(
    private val nowEpochMillis: () -> Long = { 0L },
) : ActionLog {
    private val records = mutableListOf<ActionRecord>()
    private var nextId = 1L

    override suspend fun begin(type: ActionType, originalPath: String, trashId: String?, bytes: Long): Long {
        val id = nextId++
        records += ActionRecord(
            id = id,
            atEpochMillis = nowEpochMillis(),
            type = type,
            originalPath = originalPath,
            trashId = trashId,
            bytes = bytes,
            result = ActionResult.PENDING,
            message = null,
        )
        return id
    }

    override suspend fun complete(id: Long, result: ActionResult, message: String?) {
        require(result != ActionResult.PENDING) { "complete no admite PENDING" }
        val index = records.indexOfFirst { it.id == id }
        require(index >= 0) { "no existe el registro $id" }
        records[index] = records[index].copy(result = result, message = message)
    }

    override suspend fun recent(limit: Int): List<ActionRecord> =
        records
            .sortedWith(compareByDescending<ActionRecord> { it.atEpochMillis }.thenByDescending { it.id })
            .take(limit)
}
