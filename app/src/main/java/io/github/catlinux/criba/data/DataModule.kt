package io.github.catlinux.criba.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.catlinux.criba.domain.ApkInspector
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.InstalledAppsSource
import io.github.catlinux.criba.domain.StorageInfoSource
import javax.inject.Named

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
}
