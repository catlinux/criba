package io.github.catlinux.criba.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.StorageInfoSource

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindStorageInfoSource(impl: StatFsStorageInfoSource): StorageInfoSource

    @Binds
    abstract fun bindFileSource(impl: MediaStoreFileSource): FileSource
}
