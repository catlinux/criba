package io.github.catlinux.criba.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.LargeFilesScanner

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    fun provideLargeFilesScanner(source: FileSource): LargeFilesScanner = LargeFilesScanner(source)
}
