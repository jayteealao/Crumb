package com.github.jayteealao.twitter.models

import com.google.gson.annotations.SerializedName

data class TweetMeta(
    @SerializedName("result_count") val resultCount: Int,
    @SerializedName("next_token") val nextToken: String
)
