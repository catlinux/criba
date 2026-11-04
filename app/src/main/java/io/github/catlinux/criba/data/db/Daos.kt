package io.github.catlinux.criba.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Solo inserta y actualiza el resultado: el registro es auditable y no se puede borrar. */
@Dao
interface ActionLogDao {
    @Insert
    suspend fun insert(entity: ActionEntity): Long

    @Query("UPDATE action_log SET result = :result, message = :message WHERE id = :id")
    suspend fun complete(id: Long, result: String, message: String?)

    @Query("SELECT * FROM action_log ORDER BY atEpochMillis DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ActionEntity>
}

@Dao
interface TrashDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TrashEntity)

    @Query("UPDATE trash_item SET state = :state WHERE id = :id")
    suspend fun setState(id: String, state: String)

    @Query("SELECT * FROM trash_item WHERE id = :id")
    suspend fun get(id: String): TrashEntity?

    @Query("SELECT * FROM trash_item WHERE state = :state ORDER BY trashedAtEpochMillis ASC, id ASC")
    suspend fun byState(state: String): List<TrashEntity>

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM trash_item WHERE state = :state")
    suspend fun totalBytesByState(state: String): Long
}
