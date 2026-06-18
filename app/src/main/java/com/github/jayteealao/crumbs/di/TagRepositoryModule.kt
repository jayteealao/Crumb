package com.github.jayteealao.crumbs.di

import com.github.jayteealao.crumbs.data.RedditTags
import com.github.jayteealao.crumbs.data.TagRepository
import com.github.jayteealao.crumbs.data.TwitterTags
import com.github.jayteealao.reddit.data.RedditRepository
import com.github.jayteealao.twitter.data.Repository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class TagRepositoryModule {

    // Source-scoped bindings. The unqualified TagRepository default still
    // resolves to Twitter so existing callers that did not specify a source
    // keep their old behavior; Reddit injects the qualified Reddit binding
    // to avoid the previous FK-violation when its tags routed through
    // tweet_tags.
    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: Repository): TagRepository

    @Binds
    @Singleton
    @TwitterTags
    abstract fun bindTwitterTagRepository(impl: Repository): TagRepository

    @Binds
    @Singleton
    @RedditTags
    abstract fun bindRedditTagRepository(impl: RedditRepository): TagRepository
}
