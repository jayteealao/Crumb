package com.github.jayteealao.crumbs.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletedBookmarkRepository @Inject constructor(
    private val dao: DeletedBookmarkDao,
) {

    private val _events = MutableSharedFlow<SnackbarEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<SnackbarEvent> = _events.asSharedFlow()

    suspend fun softDelete(id: String, source: String) {
        dao.insert(DeletedBookmark(id, source, System.currentTimeMillis()))
        _events.tryEmit(SnackbarEvent.UndoableDelete(id, source))
    }

    suspend fun undoDelete(id: String) {
        dao.delete(id)
    }

    fun isDeleted(id: String): Boolean = dao.existsBlocking(id)

    fun deletedIds(): Flow<List<String>> = dao.getAllIds()
}
