package com.github.jayteealao.crumbs.data

import com.github.jayteealao.crumbs.models.BookmarkSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletedBookmarkRepository @Inject constructor(
    private val dao: DeletedBookmarkDao,
    private val snackbarBus: SnackbarBus,
) {

    suspend fun softDelete(id: String, source: BookmarkSource) {
        dao.insert(DeletedBookmark(id, source.name.lowercase(), System.currentTimeMillis()))
        snackbarBus.emit(SnackbarEvent.UndoableDelete(id, source))
    }

    suspend fun undoDelete(id: String, source: BookmarkSource) {
        dao.delete(id, source.name.lowercase())
    }

    suspend fun isDeleted(id: String, source: BookmarkSource): Boolean =
        dao.exists(id, source.name.lowercase())

    /**
     * One-shot snapshot of all tombstoned bookmark ids for a given source. Sync paths fetch
     * this once before iterating remote pages and gate inserts on `Set.contains` instead of
     * issuing a per-row DAO query (N+1 elimination + dispatcher-safe).
     */
    suspend fun deletedIdsSnapshot(source: BookmarkSource): Set<String> =
        dao.getAllIdsSnapshotForSource(source.name.lowercase()).toSet()

    fun deletedIds(): Flow<List<String>> = dao.getAllIds()
}
