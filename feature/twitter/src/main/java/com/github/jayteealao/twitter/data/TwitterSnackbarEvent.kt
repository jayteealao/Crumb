package com.github.jayteealao.twitter.data

/**
 * Snackbar surface for the bookmarks list. Emitted by [Repository] after
 * `triggerPoll` returns `{ok: false}` so the VM can show debounce + in-progress
 * + error states without bleeding callable details into Compose.
 *
 * Named distinctly from core's `SnackbarEvent` (the undo-delete bus) so the two
 * event domains no longer collide on a shared type name at call sites.
 */
sealed class TwitterSnackbarEvent {
    data class Debounced(val retryAfterSeconds: Int?) : TwitterSnackbarEvent()
    object InProgress : TwitterSnackbarEvent()
    data class GenericFailure(val reason: String) : TwitterSnackbarEvent()
}
