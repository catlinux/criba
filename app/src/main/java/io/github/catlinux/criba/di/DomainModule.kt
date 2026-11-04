package io.github.catlinux.criba.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.catlinux.criba.domain.ApkInspector
import io.github.catlinux.criba.domain.AppSource
import io.github.catlinux.criba.domain.ContentHasher
import io.github.catlinux.criba.domain.DuplicateRecheck
import io.github.catlinux.criba.domain.DuplicatesScanner
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.InstalledAppsSource
import io.github.catlinux.criba.domain.LargeFilesScanner
import io.github.catlinux.criba.domain.OldApksScanner
import io.github.catlinux.criba.domain.OldDownloadsScanner
import io.github.catlinux.criba.domain.ScreenshotsScanner
import io.github.catlinux.criba.domain.UnusedAppsScanner
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

    @Provides
    fun provideScreenshotsScanner(source: FileSource): ScreenshotsScanner = ScreenshotsScanner(source)

    @Provides
    fun provideUnusedAppsScanner(
        source: AppSource,
        @ApplicationContext context: Context,
    ): UnusedAppsScanner = UnusedAppsScanner(source, context.packageName)

    @Provides
    fun provideDuplicatesScanner(
        source: FileSource,
        @Named("downloads") downloads: FileSource,
        hasher: ContentHasher,
    ): DuplicatesScanner =
        // MediaStore no muestra documentos ni zips de Descargas; el escáner deduplica lo que ven ambas fuentes.
        DuplicatesScanner({ source.listFiles() + downloads.listFiles() }, hasher)

    @Provides
    fun provideDuplicateRecheck(hasher: ContentHasher): DuplicateRecheck = DuplicateRecheck(hasher)
}
