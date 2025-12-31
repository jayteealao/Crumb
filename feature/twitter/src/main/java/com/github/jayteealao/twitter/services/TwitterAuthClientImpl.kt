package com.github.jayteealao.twitter.services

import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.github.jayteealao.twitter.models.TokenResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

class TwitterAuthClientImpl @Inject constructor(
    private val twitterAuthService: TwitterAuthService,
    private val scope: CoroutineScope
) : TwitterAuthClient {

    // PKCE code verifier - store this to use during token exchange
    private var codeVerifier: String = generateCodeVerifier()

    // Generate code challenge from verifier using S256
    private val codeChallenge: String
        get() = generateCodeChallenge(codeVerifier)

    private val authorizationUrl: String
        get() = "https://x.com/i/oauth2/authorize?response_type=code" +
            "&client_id=QnFuclQ0SGZIS01zVlZsdm5jU0o6MTpjaQ" +
            "&redirect_uri=https://graphitenerd.xyz" +
            "&scope=offline.access%20tweet.read%20users.read%20bookmark.read%20bookmark.write" +
            "&state=state&code_challenge=$codeChallenge&code_challenge_method=S256"

    /**
     * Generate a cryptographically secure random code verifier for PKCE
     * Length: 43-128 characters from [A-Z][a-z][0-9]-._~
     */
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(32)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Generate code challenge from verifier using SHA256
     * code_challenge = BASE64URL(SHA256(ASCII(code_verifier)))
     */
    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    override fun authIntent(): Intent {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(authorizationUrl)
        }
        return Intent.createChooser(viewIntent, "Open Link with")
    }

    override suspend fun getAccessToken(authorizationCode: String): TokenResponse? {
        var result: TokenResponse? = null
        scope.launch {
            result = twitterAuthService
                .getAccessToken(
                    code = authorizationCode,
                    codeVerifier = codeVerifier
                ).onError {
                    Timber.d("$response")
                }.onException {
                    Timber.d(message())
                }.onFailure {
                    Timber.d(message())
                }.getOrNull()
            Timber.tag("access token")
            Timber.d("token actual data: ${result?.accessToken}")
            Timber.d("token expires: ${result?.expiresIn}")
            Timber.d("token expires: ${result?.tokenType}")
//            authPref.setAccessCode(result?.accessToken ?: "")
//            authPref.setRefreshCode(result?.refreshToken ?: "")
        }.join()
        return result
    }

    override suspend fun getAppOnlyAccessToken(): TokenResponse? {
        var result: TokenResponse? = null
        scope.launch {
//            val result = service.getAccessToken(pkce, authorizationCode)
//            credentials.twitterOauth2AccessToken = result.accessToken
//            credentials.twitterOauth2RefreshToken = result.refreshToken
            result = twitterAuthService.getAppOnlyAccessToken(
                    AppOnlyBody(),
                    "Basic ${Base64.encodeToString("QnFuclQ0SGZIS01zVlZsdm5jU0o6MTpjaQ:r3KjJTwKRuhNrBDJgFI0SzkCQqYlf59H3CrgfBSDASda3Lc-MM".toByteArray(), Base64.NO_WRAP + Base64.URL_SAFE)}",
                ).onError {
                    Timber.d("$response")
                }.onException {
                    Timber.d(message())
                }.onFailure {
                    Timber.d(message())
                }.getOrNull()
            Timber.tag("access token")
            Timber.d("token actual data: ${result?.accessToken}")
            Timber.d("token expires: ${result?.expiresIn}")
            Timber.d("token expires: ${result?.tokenType}")
//            authPref.setAccessCode(result?.accessToken ?: "")
//            authPref.setRefreshCode(result?.refreshToken ?: "")
        }.join()
        return result
    }

    override suspend fun refreshAccessToken(refreshToken: String): TokenResponse? {
        var result: TokenResponse? = null
        scope.launch {
            result = twitterAuthService.refreshAccessToken(refreshToken).onError {
                Timber.d("$response")
            }.onException {
                Timber.d(message())
            }.onFailure {
                Timber.d(message())
            }.getOrNull()
        }.join()
        return result
    }

    override suspend fun revokeToken(accessToken: String): TokenResponse? {
        var result: TokenResponse? = null
        scope.launch {
            result = twitterAuthService.revokeAccessToken(accessToken).onError {
                Timber.d("$response")
                Timber.d(this.errorBody.toString())
            }.onException {
                Timber.d(message())
            }.onFailure {
                Timber.d(message())
            }.getOrNull()
        }.join()
        Timber.d("revoke token: $result")
        return result
    }
}
