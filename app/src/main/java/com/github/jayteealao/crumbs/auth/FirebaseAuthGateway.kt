package com.github.jayteealao.crumbs.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.github.jayteealao.crumbs.sync.MediaBackfillWorker
import com.github.jayteealao.twitter.data.TwitterSyncEnqueuer
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class FirebaseAuthGateway @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationContext private val appContext: Context,
    // Lazy because TwitterSyncEnqueuerImpl injects AuthGateway — without
    // Lazy this would be a circular construction cycle at Hilt graph init.
    private val twitterSyncEnqueuer: Lazy<TwitterSyncEnqueuer>,
) : AuthGateway {

    // AuthStateListener is registered for the process lifetime. The gateway is
    // a Singleton, so there is no teardown — by design.
    private val _currentUser = MutableStateFlow<CurrentUser?>(auth.currentUser?.toCurrentUser())
    override val currentUser: StateFlow<CurrentUser?> = _currentUser.asStateFlow()

    // Called once by CrumbApplication.onCreate() — not in init — so that the
    // side effect (listener registration) is deferred until after construction.
    // This makes the class testable without needing to capture the listener.
    override fun initialize() {
        auth.addAuthStateListener { fa ->
            val prior = _currentUser.value?.uid
            val next = fa.currentUser?.toCurrentUser()
            _currentUser.value = next
            // Fresh sign-in (null→uid or uid swap) kicks off the cold-start
            // sync. WorkManager's KEEP policy makes redundant enqueues a no-op,
            // so this is safe even on initial restore.
            val nextUid = next?.uid
            if (!nextUid.isNullOrEmpty() && nextUid != prior) {
                runCatching { twitterSyncEnqueuer.get().enqueueColdStart() }
                    .onFailure { Timber.w(it, "sign-in sync enqueue failed") }
                // One-time legacy media backfill (self-guarded on its run-once flag).
                runCatching { MediaBackfillWorker.enqueueOnce(appContext) }
                    .onFailure { Timber.w(it, "media backfill enqueue failed") }
            }
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        return runAuthCall(allowCollision = true) {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        }.let { result ->
            // CollisionRequiresEmail must carry the pending Google id token so
            // the caller can finish the link after E/P sign-in.
            if (result is AuthResult.CollisionRequiresEmail) {
                AuthResult.CollisionRequiresEmail(idToken)
            } else {
                result
            }
        }
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): AuthResult {
        return runAuthCall(allowCollision = false) {
            auth.signInWithEmailAndPassword(email, password).await()
        }
    }

    override suspend fun linkGoogleToCurrentUser(idToken: String): AuthResult {
        val user = auth.currentUser
            ?: return AuthResult.Unknown(IllegalStateException("No signed-in user to link"))
        return runAuthCall(allowCollision = false) {
            user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        // Drop any cached Credential Manager state so the next sign-in re-asks
        // for account selection instead of silently returning the same account.
        runCatching {
            CredentialManager.create(appContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }.onFailure { Timber.w(it, "clearCredentialState failed") }
    }

    private inline fun runAuthCall(
        allowCollision: Boolean,
        block: () -> Any?,
    ): AuthResult {
        return try {
            block()
            AuthResult.Success
        } catch (e: FirebaseAuthUserCollisionException) {
            if (allowCollision) {
                // Placeholder; signInWithGoogleIdToken substitutes the real token.
                AuthResult.CollisionRequiresEmail("")
            } else {
                AuthResult.Unknown(e)
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.InvalidCredentials
        } catch (e: FirebaseNetworkException) {
            AuthResult.NetworkError
        } catch (e: Exception) {
            Timber.e(e, "Auth call failed")
            AuthResult.Unknown(e)
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toCurrentUser(): CurrentUser =
        CurrentUser(uid = uid, email = email)
}
