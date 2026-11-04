package io.github.catlinux.criba.data.trash

import io.github.catlinux.criba.domain.ConfirmedDeletion
import io.github.catlinux.criba.domain.ConfirmedPurge
import io.github.catlinux.criba.domain.MoveToTrashResult
import io.github.catlinux.criba.domain.PurgeResult
import io.github.catlinux.criba.domain.RestoreResult
import io.github.catlinux.criba.domain.TrashManager

/** Fachada pública de la papelera; la UI solo ve esta interfaz, nunca el motor interno. */
class FileTrashManager internal constructor(
    private val engine: FileTrashEngine,
) : TrashManager {
    override suspend fun moveToTrash(confirmed: ConfirmedDeletion): MoveToTrashResult =
        engine.moveToTrash(confirmed.items)

    override suspend fun restore(trashId: String): RestoreResult = engine.restore(trashId)

    override suspend fun purge(confirmed: ConfirmedPurge): PurgeResult = engine.purgeItems(confirmed.items)

    override suspend fun purgeExpired(nowEpochMillis: Long, retentionDays: Int): PurgeResult =
        engine.purgeExpired(nowEpochMillis, retentionDays)
}
