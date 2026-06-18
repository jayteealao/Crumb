package com.github.jayteealao.crumbs.data

import androidx.room.Entity

@Entity(tableName = "deleted_bookmarks", primaryKeys = ["bookmarkId", "source"])
data class DeletedBookmark(
    val bookmarkId: String,
    val source: String,
    val deletedAt: Long,
)
