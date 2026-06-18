package com.github.jayteealao.crumbs.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.twitter.data.Prefs
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the account-deletion flow.
 *
 * The callable returns `{ ok: true }`, then the client:
 * 1. Clears all locally-stored tokens via [Prefs.clearAllTokens].
 * 2. Signs out of Firebase Auth via [AuthGateway.signOut].
 * 3. Emits [DeleteAccountState.Done] so the composable navigates to the
 *    login screen.
 *
 * Lives in the app module because it sign-out coordinates [AuthGateway], which
 * the feature modules do not (and must not) depend on.
 */
@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val functions: FirebaseFunctions,
    private val authPrefs: Prefs,
    private val authGateway: AuthGateway,
) : ViewModel() {

    private val _state = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val state: StateFlow<DeleteAccountState> = _state.asStateFlow()

    fun deleteAccount() {
        if (_state.value is DeleteAccountState.InProgress) return
        _state.value = DeleteAccountState.InProgress
        viewModelScope.launch {
            runCatching {
                val result = functions
                    .getHttpsCallable("deleteAccount")
                    .call(emptyMap<String, Any>())
                    .await()
                val payload = result.data as? Map<*, *>
                val ok = payload?.get("ok") as? Boolean ?: false
                if (!ok) {
                    throw IllegalStateException(
                        payload?.get("reason") as? String ?: "delete_failed",
                    )
                }
                // Server confirmed deletion — clear local state.
                authPrefs.clearAllTokens()
                authGateway.signOut()
            }.onSuccess {
                Timber.d("DeleteAccountViewModel: account deleted successfully")
                _state.value = DeleteAccountState.Done
            }.onFailure { err ->
                Timber.e(err, "DeleteAccountViewModel: account deletion failed")
                _state.value = DeleteAccountState.Error(err.message ?: "delete_failed")
            }
        }
    }

    fun resetError() {
        if (_state.value is DeleteAccountState.Error) {
            _state.value = DeleteAccountState.Idle
        }
    }
}

sealed interface DeleteAccountState {
    data object Idle : DeleteAccountState
    data object InProgress : DeleteAccountState
    data object Done : DeleteAccountState
    data class Error(val reason: String) : DeleteAccountState
}
