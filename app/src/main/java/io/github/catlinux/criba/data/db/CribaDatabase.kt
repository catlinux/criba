package io.github.catlinux.criba.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ActionEntity::class, TrashEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CribaDatabase : RoomDatabase() {
    abstract fun actionLogDao(): ActionLogDao

    abstract fun trashDao(): TrashDao
}
