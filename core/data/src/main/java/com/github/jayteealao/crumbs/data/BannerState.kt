package com.github.jayteealao.crumbs.data

import com.github.jayteealao.crumbs.models.BookmarkSource

data class BannerState(
    val source: BookmarkSource,
    val kicker: String,
    val detail: String,
    val ctaLabel: String,
)
