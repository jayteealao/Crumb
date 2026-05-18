package com.github.jayteealao.crumbs.di

import com.github.jayteealao.crumbs.data.TagRepository
import com.github.jayteealao.twitter.data.Repository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class TagRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: Repository): TagRepository
}
