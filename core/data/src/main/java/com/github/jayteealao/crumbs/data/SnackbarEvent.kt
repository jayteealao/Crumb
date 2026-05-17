package com.github.jayteealao.crumbs.data

sealed interface SnackbarEvent {
    data class UndoableDelete(val id: String, val source: String) : SnackbarEvent
}
