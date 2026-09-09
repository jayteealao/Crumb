package com.github.jayteealao.crumbs.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.github.jayteealao.crumbs.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

// Activity is required because Credential Manager renders an OS-level bottom
// sheet anchored to the foreground Activity. Interface boundary keeps the
// Activity dependency out of the VM and makes the test path trivially fakeable.
interface CredentialManagerCoordinator {
    suspend fun signInWithGoogle(activity: Activity): AuthResult
}

@Singleton
class RealCredentialManagerCoordinator
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val authGateway: AuthGateway,
    ) : CredentialManagerCoordinator {
        override suspend fun signInWithGoogle(activity: Activity): AuthResult {
            val serverClientId = BuildConfig.WEB_OAUTH_CLIENT_ID
            if (serverClientId.isEmpty()) {
                Timber.w("WEB_OAUTH_CLIENT_ID is empty; cannot start Credential Manager flow")
                return AuthResult.Unknown(IllegalStateException("Web OAuth client ID not configured"))
            }
            val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            return try {
                val response = CredentialManager.create(activity).getCredential(activity, request)
                val credential = response.credential
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                authGateway.signInWithGoogleIdToken(googleCred.idToken)
            } catch (e: CancellationException) {
                // The composable that launched this coroutine left composition
                // (e.g. a navigation event popped it) — propagate so structured
                // concurrency can clean up normally. A breadcrumb makes any
                // regression of the cancelled-sheet bug class loud instead of silent.
                Timber.w(e, "getCredential cancelled mid-flight — caller likely left composition")
                throw e
            } catch (e: NoCredentialException) {
                // No matching credential on device (or offline-ish). Surface as
                // network-class so the UI can prompt to retry.
                Timber.w(e, "No credential available for Google sign-in")
                AuthResult.NetworkError
            } catch (e: GoogleIdTokenParsingException) {
                Timber.e(e, "Failed to parse Google id token")
                AuthResult.Unknown(e)
            } catch (e: GetCredentialException) {
                Timber.e(e, "Credential Manager getCredential failed")
                AuthResult.Unknown(e)
            }
        }
    }
