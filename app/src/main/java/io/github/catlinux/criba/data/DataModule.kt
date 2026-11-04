package io.github.catlinux.criba.data

import android.content.Context
import android.os.Environment
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.catlinux.criba.data.db.ActionLogDao
import io.github.catlinux.criba.data.db.CribaDatabase
import io.github.catlinux.criba.data.db.RoomActionLog
import io.github.catlinux.criba.data.db.RoomTrashIndex
import io.github.catlinux.criba.data.db.TrashDao
import io.github.catlinux.criba.data.trash.FileTrashEngine
import io.github.catlinux.criba.data.trash.FileTrashManager
import io.github.catlinux.criba.domain.ActionLog
import io.github.catlinux.criba.domain.ApkInspector
import io.github.catlinux.criba.domain.DeletionPolicy
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.InstalledAppsSource
import io.github.catlinux.criba.domain.StorageInfoSource
import io.github.catlinux.criba.domain.TrashIndex
import io.github.catlinux.criba.domain.TrashManager
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindStorageInfoSource(impl: StatFsStorageInfoSource): StorageInfoSource

    @Binds
    abstract fun bindFileSource(impl: MediaStoreFileSource): FileSource

    @Binds
    @Named("downloads")
    abstract fun bindDownloadsFileSource(impl: DownloadsFileSource): FileSource

    @Binds
    abstract fun bindApkInspector(impl: PackageManagerApkInspector): ApkInspector

    @Binds
    abstract fun bindInstalledAppsSource(impl: PackageManagerInstalledApps): InstalledAppsSource

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): CribaDatabase =
            Room.databaseBuilder(context, CribaDatabase::class.java, "criba.db").build()

        @Provides
        fun provideActionLogDao(database: CribaDatabase): ActionLogDao = database.actionLogDao()

        @Provides
        fun provideTrashDao(database: CribaDatabase): TrashDao = database.trashDao()

        @Provides
        fun provideActionLog(dao: ActionLogDao): ActionLog = RoomActionLog(dao)

        @Provides
        fun provideTrashIndex(dao: TrashDao): TrashIndex = RoomTrashIndex(dao)

        @Provides
        @Singleton
        fun provideTrashManager(
            @ApplicationContext context: Context,
            log: ActionLog,
            index: TrashIndex,
        ): TrashManager {
            // Android/data es otro montaje: desde Descargas el rename atómico falla con EXDEV.
            // La raíz del almacenamiento compartido sí es el mismo montaje.
            @Suppress("DEPRECATION")
            val trashRoot = File(Environment.getExternalStorageDirectory(), DeletionPolicy.TRASH_DIRECTORY_NAME)
            return FileTrashManager(FileTrashEngine(trashRoot = trashRoot, log = log, index = index))
        }

        @Provides
        @Named("storageRoot")
        @Suppress("DEPRECATION")
        fun provideStorageRoot(): String = Environment.getExternalStorageDirectory().path

        @Provides
        @Named("epochMillisClock")
        fun provideEpochMillisClock(): () -> Long = { System.currentTimeMillis() }
    }
}
