package com.github.jayteealao.crumbs.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedBookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: DeletedBookmark)

    @Query("DELETE FROM deleted_bookmarks WHERE bookmarkId = :id AND source = :source")
    suspend fun delete(id: String, source: String)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id AND source = :source)")
    suspend fun exists(id: String, source: String): Boolean

    @Query("SELECT bookmarkId FROM deleted_bookmarks WHERE source = :source")
    suspend fun getAllIdsSnapshotForSource(source: String): List<String>

    @Query("SELECT bookmarkId FROM deleted_bookmarks")
    fun getAllIds(): Flow<List<String>>

    // Used by the debug seed path to snapshot + restore tombstones around
    // clearAllTables(), so permanently-deleted bookmarks do not reappear.
    @Query("SELECT * FROM deleted_bookmarks")
    suspend fun getAllDeleted(): List<DeletedBookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tombstones: List<DeletedBookmark>)
}
