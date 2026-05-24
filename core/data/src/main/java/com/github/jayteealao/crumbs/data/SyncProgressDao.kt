package com.github.jayteealao.crumbs.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncProgressDao {

    @Query("SELECT * FROM sync_progress WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): SyncProgress?

    @Query("SELECT * FROM sync_progress WHERE uid = :uid LIMIT 1")
    fun observe(uid: String): Flow<SyncProgress?>

    /**
     * `REPLACE` is correct because cursor advancement OVERWRITES the prior row
     * by definition (one row per uid). The DAO never deletes — disconnect-X
     * lives in a different lifecycle.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: SyncProgress)
}
