package io.github.catlinux.criba.data

import android.content.Context
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
import io.github.catlinux.criba.domain.ActionLog
import io.github.catlinux.criba.domain.ApkInspector
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.InstalledAppsSource
import io.github.catlinux.criba.domain.StorageInfoSource
import io.github.catlinux.criba.domain.TrashIndex
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
    }
}
