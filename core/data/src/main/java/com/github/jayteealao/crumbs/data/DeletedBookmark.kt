package com.github.jayteealao.crumbs.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_bookmarks")
data class DeletedBookmark(
    @PrimaryKey val bookmarkId: String,
    val source: String,
    val deletedAt: Long,
)
