package io.github.catlinux.criba.data.db

import io.github.catlinux.criba.domain.ActionLog
import io.github.catlinux.criba.domain.ActionRecord
import io.github.catlinux.criba.domain.ActionResult
import io.github.catlinux.criba.domain.ActionType
import io.github.catlinux.criba.domain.TrashIndex
import io.github.catlinux.criba.domain.TrashItem
import io.github.catlinux.criba.domain.TrashState

class RoomActionLog(
    private val dao: ActionLogDao,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : ActionLog {
    override suspend fun begin(type: ActionType, originalPath: String, trashId: String?, bytes: Long): Long =
        dao.insert(
            ActionEntity(
                atEpochMillis = nowEpochMillis(),
                type = type.name,
                originalPath = originalPath,
                trashId = trashId,
                bytes = bytes,
                result = ActionResult.PENDING.name,
                message = null,
            ),
        )

    override suspend fun complete(id: Long, result: ActionResult, message: String?) {
        require(result != ActionResult.PENDING) { "complete no admite PENDING" }
        dao.complete(id = id, result = result.name, message = message)
    }

    override suspend fun recent(limit: Int): List<ActionRecord> =
        dao.recent(limit).map { it.toDomain() }
}

class RoomTrashIndex(private val dao: TrashDao) : TrashIndex {
    override suspend fun add(item: TrashItem) {
        try {
            dao.insert(item.toEntity())
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw IllegalStateException("ya existe un elemento con el id ${item.id}", e)
        }
    }

    override suspend fun setState(id: String, state: TrashState) =
        dao.setState(id = id, state = state.name)

    override suspend fun get(id: String): TrashItem? = dao.get(id)?.toDomain()

    override suspend fun inTrash(): List<TrashItem> =
        dao.byState(TrashState.IN_TRASH.name).map { it.toDomain() }

    override suspend fun inTrashBytes(): Long = dao.totalBytesByState(TrashState.IN_TRASH.name)
}
