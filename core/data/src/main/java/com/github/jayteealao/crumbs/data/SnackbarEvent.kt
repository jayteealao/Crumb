package com.github.jayteealao.crumbs.data

import com.github.jayteealao.crumbs.models.BookmarkSource

sealed interface SnackbarEvent {
    data class UndoableDelete(val id: String, val source: BookmarkSource) : SnackbarEvent
}
