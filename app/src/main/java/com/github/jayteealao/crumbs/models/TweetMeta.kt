package com.github.jayteealao.crumbs.models

import com.google.gson.annotations.SerializedName

data class TweetMeta(
    @SerializedName("result_count") val resultCount: Int,
    @SerializedName("next_token") val nextToken: String
)
