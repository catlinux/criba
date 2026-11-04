package io.github.catlinux.criba.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.catlinux.criba.domain.ActionRecord
import io.github.catlinux.criba.domain.ActionResult
import io.github.catlinux.criba.domain.ActionType
import io.github.catlinux.criba.domain.TrashItem
import io.github.catlinux.criba.domain.TrashState

@Entity(tableName = "action_log", indices = [Index(value = ["atEpochMillis"])])
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atEpochMillis: Long,
    val type: String,
    val originalPath: String,
    val trashId: String?,
    val bytes: Long,
    val result: String,
    val message: String?,
)

@Entity(tableName = "trash_item", indices = [Index(value = ["state"])])
data class TrashEntity(
    @PrimaryKey val id: String,
    val originalPath: String,
    val trashPath: String,
    val bytes: Long,
    val sha256: String?,
    val trashedAtEpochMillis: Long,
    val state: String,
)

fun ActionEntity.toDomain(): ActionRecord = ActionRecord(
    id = id,
    atEpochMillis = atEpochMillis,
    type = ActionType.valueOf(type),
    originalPath = originalPath,
    trashId = trashId,
    bytes = bytes,
    result = ActionResult.valueOf(result),
    message = message,
)

fun ActionRecord.toEntity(): ActionEntity = ActionEntity(
    id = id,
    atEpochMillis = atEpochMillis,
    type = type.name,
    originalPath = originalPath,
    trashId = trashId,
    bytes = bytes,
    result = result.name,
    message = message,
)

fun TrashEntity.toDomain(): TrashItem = TrashItem(
    id = id,
    originalPath = originalPath,
    trashPath = trashPath,
    bytes = bytes,
    sha256 = sha256,
    trashedAtEpochMillis = trashedAtEpochMillis,
    state = TrashState.valueOf(state),
)

fun TrashItem.toEntity(): TrashEntity = TrashEntity(
    id = id,
    originalPath = originalPath,
    trashPath = trashPath,
    bytes = bytes,
    sha256 = sha256,
    trashedAtEpochMillis = trashedAtEpochMillis,
    state = state.name,
)
