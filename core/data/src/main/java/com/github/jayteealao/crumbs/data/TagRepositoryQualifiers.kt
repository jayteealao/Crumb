package com.github.jayteealao.crumbs.data

import javax.inject.Qualifier

// Source-scoped TagRepository qualifiers. Injecting `TagRepository` plain
// resolves to the unqualified binding (Twitter, for legacy callers); inject
// `@TwitterTags TagRepository` or `@RedditTags TagRepository` to opt into
// source-specific tag storage. The Reddit qualified binding writes to
// `reddit_tag_crossref` so its rows are decoupled from `tweet_tags`'
// FK to `tweetEntity`, which would otherwise crash on Reddit tag save.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TwitterTags

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RedditTags
