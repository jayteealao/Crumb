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

    @Query("DELETE FROM deleted_bookmarks WHERE bookmarkId = :id")
    suspend fun delete(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id)")
    fun existsBlocking(id: String): Boolean

    @Query("SELECT bookmarkId FROM deleted_bookmarks")
    fun getAllIds(): Flow<List<String>>
}
