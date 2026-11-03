package io.github.catlinux.criba.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.catlinux.criba.domain.ApkInspector
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.InstalledAppsSource
import io.github.catlinux.criba.domain.LargeFilesScanner
import io.github.catlinux.criba.domain.OldApksScanner
import io.github.catlinux.criba.domain.OldDownloadsScanner
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun provideLargeFilesScanner(source: FileSource): LargeFilesScanner = LargeFilesScanner(source)

    @Provides
    fun provideOldDownloadsScanner(@Named("downloads") source: FileSource): OldDownloadsScanner =
        OldDownloadsScanner(source)

    @Provides
    fun provideOldApksScanner(
        @Named("downloads") source: FileSource,
        inspector: ApkInspector,
        installed: InstalledAppsSource,
    ): OldApksScanner = OldApksScanner(source, inspector, installed)
}
