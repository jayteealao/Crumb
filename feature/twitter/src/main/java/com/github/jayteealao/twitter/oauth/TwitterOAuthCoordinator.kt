package com.github.jayteealao.twitter.oauth

import android.app.Activity
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.github.jayteealao.twitter.BuildConfig
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the X OAuth 2.1 PKCE handshake from the Android client:
 *   1. generate code_verifier locally,
 *   2. ping `warmUp` to wake the Cloud Function instance,
 *   3. call `mintOAuthState` with `code_verifier` → receives signed state JWT,
 *   4. launch Chrome Custom Tabs at the X authorize URL,
 *   5. consume the deep-link redirect (`crumbs://graphitenerd.xyz/x-oauth-*`).
 *
 * The verifier is *never* placed on the redirect URL — it lives only inside
 * the HMAC-signed state JWT minted server-side. See RFC 9700 / OAuth 2.1.
 */
@Singleton
class TwitterOAuthCoordinator @Inject constructor(
    private val functions: FirebaseFunctions,
) {
    private val _results = MutableSharedFlow<OAuthResult>(replay = 0, extraBufferCapacity = 1)
    val results: SharedFlow<OAuthResult> = _results.asSharedFlow()

    suspend fun launchAuthorize(activity: Activity) {
        val verifier = generateCodeVerifier()
        val challenge = sha256Base64Url(verifier)

        // Fire-and-forget warmup — keeps the oauthCallback Cloud Run instance
        // hot while the user is in the Custom Tab. Errors are swallowed.
        runCatching {
            functions.getHttpsCallable("warmUp").call().await()
        }.onFailure { Timber.w(it, "warmUp ping failed") }

        val state = try {
            val data = mapOf("code_verifier" to verifier)
            val result = functions.getHttpsCallable("mintOAuthState").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val payload = result.data as? Map<String, Any?>
            payload?.get("state") as? String
                ?: throw IllegalStateException("mintOAuthState returned no state")
        } catch (e: FirebaseFunctionsException) {
            Timber.e(e, "mintOAuthState failed: ${e.code}")
            _results.tryEmit(OAuthResult.Failure("mint_state_failed"))
            return
        }

        val authorizeUrl = buildAuthorizeUrl(state, challenge)
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        intent.launchUrl(activity, Uri.parse(authorizeUrl))
    }

    /**
     * Called from the NavHost when the deep-link receipt fires. Parses the
     * URI and emits the result on the shared flow. Safe to call repeatedly.
     */
    fun handleDeepLink(uri: Uri) {
        when (uri.path) {
            PATH_COMPLETE -> _results.tryEmit(OAuthResult.Success)
            PATH_ERROR -> {
                val reason = uri.getQueryParameter("reason") ?: "unknown"
                _results.tryEmit(OAuthResult.Failure(reason))
            }
            else -> Timber.w("TwitterOAuthCoordinator: unknown deep-link path ${uri.path}")
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun sha256Base64Url(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun buildAuthorizeUrl(state: String, challenge: String): String {
        val clientId = BuildConfig.TWITTER_CLIENT_ID
        return "https://x.com/i/oauth2/authorize" +
            "?response_type=code" +
            "&client_id=" + Uri.encode(clientId) +
            "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
            "&scope=" + Uri.encode("tweet.read bookmark.read users.read offline.access") +
            "&state=" + Uri.encode(state) +
            "&code_challenge=" + Uri.encode(challenge) +
            "&code_challenge_method=S256"
    }

    companion object {
        private const val VERIFIER_BYTES = 32
        private const val REDIRECT_URI =
            "https://europe-west2-crumbs-a4fdb.cloudfunctions.net/oauthCallback"
        const val PATH_COMPLETE = "/x-oauth-complete"
        const val PATH_ERROR = "/x-oauth-error"
        const val SCHEME_HOST = "crumbs://graphitenerd.xyz"
        const val DEEP_LINK_COMPLETE = "$SCHEME_HOST$PATH_COMPLETE"
        const val DEEP_LINK_ERROR = "$SCHEME_HOST$PATH_ERROR"
    }
}

sealed class OAuthResult {
    object Success : OAuthResult()
    data class Failure(val reason: String) : OAuthResult()
}
