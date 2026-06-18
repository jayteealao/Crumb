package com.github.jayteealao.crumbs.data

import android.app.Application
import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.twitter.models.TwitterUserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks the SAVED-header count contract: the reactive count flow excludes tombstoned and
 * `referenced` rows, mirrors the tag-filtered query, and — the load-bearing invariant — equals
 * the row count of the matching paging query for every path, so the header can never disagree
 * with the visible feed. Also pins that the feed surfaces the SQLite `rowid` as `dbRowId`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Bare Application (not CrumbApplication) so this pure Room test skips Hilt / Firebase init.
@Config(sdk = [34], application = Application::class)
class TweetDaoCountTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TweetDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tweetDao()
        dao.insertTwitterUser(
            TwitterUserEntity(
                id = "u1",
                name = "Tester",
                username = "tester",
                profileImageUrl = null,
                verified = false,
                verifiedType = null,
                description = null,
                mentionedIn = null,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tweet(
        id: String,
        referenced: Boolean = false,
        conversationId: String = id,
        retrievedAt: Long? = 1_000L,
    ) = TweetEntity(
        id = id,
        text = "text-$id",
        createdAt = "2026-01-01T00:00:00.000Z",
        authorId = "u1",
        conversationId = conversationId,
        inReplyToUserId = null,
        lang = "en",
        referenced = referenced,
        retrievedAt = retrievedAt,
    )

    @Test
    fun countAll_excludesTombstonedAndReferenced_andMatchesPagingRowCount() = runTest {
        dao.insertTweet(tweet("t1"))
        dao.insertTweet(tweet("t2"))
        dao.insertTweet(tweet("t3"))
        dao.insertTweet(tweet("t4")) // tagged below
        dao.insertTweet(tweet("t5")) // tombstoned below
        dao.insertTweet(tweet("t6", referenced = true)) // referenced → excluded from the feed

        dao.insertTag(TagEntity("design"))
        dao.insertTweetTag(TweetTagCrossRef("t4", "design"))
        db.deletedBookmarkDao().insert(DeletedBookmark("t5", "twitter", 123L))

        // ALL count drops the tombstoned (t5) and referenced (t6) rows → t1..t4 = 4.
        assertEquals(4, dao.countTombstoneAware("ALL").first())
        // count == row count of the matching paging query (the lockstep invariant).
        assertEquals(
            loadAllIds(dao.getTweetsTombstoneAware("ALL")).size,
            dao.countTombstoneAware("ALL").first(),
        )

        // Tag-filtered count is the tagged subset, and again equals the paging row count.
        assertEquals(1, dao.countByTagsTombstoneAware(listOf("design"), "ALL").first())
        assertEquals(
            loadAllIds(dao.getTweetsByTagsTombstoneAware(listOf("design"), "ALL")).size,
            dao.countByTagsTombstoneAware(listOf("design"), "ALL").first(),
        )
    }

    @Test
    fun feedSurfacesSqliteRowId() = runTest {
        dao.insertTweet(tweet("a"))
        dao.insertTweet(tweet("b"))
        dao.insertTweet(tweet("c"))

        val items = loadAll(dao.getTweetsTombstoneAware("ALL"))
        assertTrue("expected rows from the feed", items.isNotEmpty())
        items.forEach { td ->
            assertTrue("dbRowId must be a real (positive) rowid for ${td.tweet.id}", td.dbRowId > 0L)
            assertEquals(
                "dbRowId must equal the SQLite rowid for ${td.tweet.id}",
                actualRowId(td.tweet.id),
                td.dbRowId,
            )
        }
    }

    private fun actualRowId(id: String): Long =
        db.openHelper.writableDatabase
            .query("SELECT rowid FROM tweetEntity WHERE id = ?", arrayOf<Any?>(id))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }

    private suspend fun loadAll(source: PagingSource<Int, TweetData>): List<TweetData> {
        val page = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        return page.data
    }

    private suspend fun loadAllIds(source: PagingSource<Int, TweetData>): List<String> =
        loadAll(source).map { it.tweet.id }
}
