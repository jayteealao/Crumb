package com.github.jayteealao.crumbs.models

/*
* TweetIncludes is a dataclass that holds the results if expansions in the twitter v2 api request
* */
data class TweetIncludes(
    val users: List<TwitterUser?> = emptyList(),
    val media: List<TweetMedia>?,
    val tweets: List<Tweet>?
)

fun tweetIncludes() = TweetIncludes(
    users = emptyList(),
    media = emptyList(),
    tweets = emptyList()
)

