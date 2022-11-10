package com.github.jayteealao.crumbs.models

import com.skydoves.sandwich.ApiResponse

data class TweetResponse(
    val data: List<Tweet>,
    val meta: TweetMeta,
    val includes: TweetIncludes?
)
