package com.github.jayteealao.twitter.data

/**
 * Snackbar surface for the bookmarks list. Emitted by [Repository] after
 * `triggerPoll` returns `{ok: false}` so the VM can show debounce + in-progress
 * + error states without bleeding callable details into Compose.
 */
sealed class SnackbarEvent {
    data class Debounced(val retryAfterSeconds: Int?) : SnackbarEvent()
    object InProgress : SnackbarEvent()
    data class GenericFailure(val reason: String) : SnackbarEvent()
}
