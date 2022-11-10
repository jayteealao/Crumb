package com.github.jayteealao.crumbs.models

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: String
)
