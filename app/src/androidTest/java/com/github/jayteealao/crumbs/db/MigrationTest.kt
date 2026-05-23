package com.github.jayteealao.crumbs.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.jayteealao.crumbs.db.MIGRATION_4_5
import com.github.jayteealao.crumbs.db.MIGRATION_5_6
import com.github.jayteealao.crumbs.db.MIGRATION_6_7
import com.github.jayteealao.crumbs.db.MIGRATION_7_8
import com.github.jayteealao.crumbs.db.MIGRATION_8_9
import com.github.jayteealao.crumbs.db.MIGRATION_9_10
import com.github.jayteealao.crumbs.db.MIGRATION_10_11
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate4To5_createsDeletedBookmarksTable() {
        helper.createDatabase(TEST_DB, 4).apply { close() }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            MIGRATION_4_5,
        )

        db.query("SELECT count(*) FROM deleted_bookmarks").use { cursor ->
            assertTrue("deleted_bookmarks table missing after migration", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.close()
    }

    @Test
    fun migrate5To6_compositePkAndDataSurvives() {
        // Create a v5 database and insert a row with the old single-column PK schema.
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO deleted_bookmarks (bookmarkId, source, deletedAt) VALUES ('abc123', 'twitter', 1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            MIGRATION_5_6,
        )

        // Verify the row survived the migration.
        db.query("SELECT bookmarkId, source, deletedAt FROM deleted_bookmarks WHERE bookmarkId = 'abc123'").use { cursor ->
            assertTrue("Row should survive migration 5→6", cursor.moveToFirst())
            assertEquals("abc123", cursor.getString(0))
            assertEquals("twitter", cursor.getString(1))
            assertEquals(1000L, cursor.getLong(2))
        }

        db.close()
    }

    @Test
    fun migrate6To7_indexesOrderColumns() {
        helper.createDatabase(TEST_DB, 6).apply { close() }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            MIGRATION_6_7,
        )

        // Verify the two `order` indexes exist after migration. SQLite reports
        // indexes via the sqlite_master master table; index_tweetEntity_order
        // and index_reddit_posts_order must both be present so feed paging
        // sorts O(log n) instead of regressing to a full-table scan.
        val expectedIndexes = setOf("index_tweetEntity_order", "index_reddit_posts_order")
        val foundIndexes = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN ('index_tweetEntity_order', 'index_reddit_posts_order')"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                foundIndexes += cursor.getString(0)
            }
        }
        assertEquals(
            "Both feed `order` indexes should exist after 6→7 migration",
            expectedIndexes,
            foundIndexes,
        )

        // Foreign-key integrity: turning on FK enforcement and running the
        // pragma check must report no violations after migrating into v7
        // (this validates that the migration did not orphan any rows from
        // earlier joins on tweet_tags/reddit_posts).
        db.execSQL("PRAGMA foreign_keys = ON")
        db.query("PRAGMA foreign_key_check").use { cursor ->
            assertTrue(
                "PRAGMA foreign_key_check should return no rows (no FK violations)",
                cursor.count == 0,
            )
        }

        db.close()
    }

    @Test
    fun migrate7To8_indexesPollIdsAndMediaKeysForeignKeys() {
        helper.createDatabase(TEST_DB, 7).apply { close() }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            MIGRATION_7_8,
        )

        val expected = setOf(
            "index_pollIds_tweetId",
            "index_mediaKeys_tweet_id",
        )
        val found = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN ('index_pollIds_tweetId', 'index_mediaKeys_tweet_id')"
        ).use { cursor ->
            while (cursor.moveToNext()) found += cursor.getString(0)
        }
        assertEquals(
            "Both FK-column indexes should exist after 7→8 migration",
            expected,
            found,
        )

        db.close()
    }

    @Test
    fun migrate8To9_addsRedditTagCrossRefTable() {
        helper.createDatabase(TEST_DB, 8).apply { close() }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            9,
            true,
            MIGRATION_8_9,
        )

        // Table exists and has the expected shape.
        db.query("PRAGMA table_info(reddit_tag_crossref)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(1)
            assertEquals(setOf("postId", "tagName"), columns)
        }

        // Both indexes were created.
        val expected = setOf(
            "index_reddit_tag_crossref_postId",
            "index_reddit_tag_crossref_tagName",
        )
        val found = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='reddit_tag_crossref'"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                // Filter out the auto-generated PK index (`sqlite_autoindex_*`).
                if (!name.startsWith("sqlite_autoindex_")) found += name
            }
        }
        assertEquals(
            "Both reddit_tag_crossref indexes should exist after 8→9",
            expected,
            found,
        )

        // Insert/select round-trip — proves the table is writable end-to-end.
        db.execSQL("INSERT INTO reddit_tag_crossref (postId, tagName) VALUES ('post-1', 'design')")
        db.query("SELECT postId, tagName FROM reddit_tag_crossref WHERE postId = 'post-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("post-1", cursor.getString(0))
            assertEquals("design", cursor.getString(1))
        }

        db.close()
    }

    @Test
    fun migrate9To10_addsPendingDeleteColumn() {
        // Seed v9 with a single tweetEntity row that pre-dates the new column.
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                """
                INSERT INTO tweetEntity
                    (id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`)
                VALUES
                    ('tweet-1', 'hi', '2026-05-21T00:00:00Z', 'u1', 'tweet-1', NULL, 'en', 0, 1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            MIGRATION_9_10,
        )

        // Column exists with the correct affinity, NOT NULL constraint, and default 0.
        db.query("PRAGMA table_info(tweetEntity)").use { cursor ->
            val schema = mutableMapOf<String, Triple<String, Int, String?>>()
            val nameIdx = cursor.getColumnIndex("name")
            val typeIdx = cursor.getColumnIndex("type")
            val notNullIdx = cursor.getColumnIndex("notnull")
            val defaultIdx = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                schema[cursor.getString(nameIdx)] = Triple(
                    cursor.getString(typeIdx),
                    cursor.getInt(notNullIdx),
                    cursor.getString(defaultIdx),
                )
            }
            val pd = schema["pending_delete"]
            assertTrue("pending_delete column missing after 9→10 migration", pd != null)
            assertEquals("pending_delete must be INTEGER", "INTEGER", pd!!.first)
            assertEquals("pending_delete must be NOT NULL", 1, pd.second)
            assertEquals("pending_delete must default to 0", "0", pd.third)
        }

        // Seed row survives the migration with pending_delete defaulted to 0.
        db.query("SELECT pending_delete FROM tweetEntity WHERE id = 'tweet-1'").use { cursor ->
            assertTrue("Seed row missing after 9→10 migration", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.close()
    }

    @Test
    fun migrate10To11_createsFtsTablesAndIndexesExistingRows() {
        // Seed v10 with one tweet and one reddit row whose text/title contain a
        // distinctive token. The migration must (a) create both FTS shadow
        // tables, (b) rebuild them so the seeded rows are searchable
        // immediately, and (c) install the content-sync triggers so future
        // INSERTs into the parent tables flow through.
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                """
                INSERT INTO tweetEntity
                    (id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`, pending_delete)
                VALUES
                    ('tweet-1', 'jetpack compose brutalist redesign', '2026-05-23T00:00:00Z', 'u1', 'tweet-1', NULL, 'en', 0, 1, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO reddit_posts
                    (id, name, title, selftext, author, subreddit, subreddit_prefixed, created_utc, url, permalink, thumbnail, num_comments, score, is_self, is_video, domain, link_flair_text, gilded, over_18, `order`)
                VALUES
                    ('post-1', 't3_post-1', 'jetpack compose tips', 'thoughts on a brutalist UI', 'redditor', 'androiddev', 'r/androiddev', 1730000000, 'https://example.com', '/r/androiddev/post-1', NULL, 5, 12, 1, 0, 'self.androiddev', NULL, 0, 0, 1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            11,
            true,
            MIGRATION_10_11,
        )

        // Both FTS virtual tables exist.
        val expectedTables = setOf("tweet_fts", "reddit_fts")
        val foundTables = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('tweet_fts', 'reddit_fts')"
        ).use { cursor ->
            while (cursor.moveToNext()) foundTables += cursor.getString(0)
        }
        assertEquals(
            "Both FTS shadow tables should exist after 10→11 migration",
            expectedTables,
            foundTables,
        )

        // MATCH query against the seeded tweet row returns it.
        db.query(
            "SELECT t.id FROM tweetEntity t JOIN tweet_fts f ON t.rowid = f.rowid WHERE tweet_fts MATCH 'compose'"
        ).use { cursor ->
            assertTrue("Seeded tweet should match 'compose' after rebuild", cursor.moveToFirst())
            assertEquals("tweet-1", cursor.getString(0))
        }

        // MATCH against reddit_fts spans both title + selftext columns.
        db.query(
            "SELECT r.id FROM reddit_posts r JOIN reddit_fts f ON r.rowid = f.rowid WHERE reddit_fts MATCH 'brutalist'"
        ).use { cursor ->
            assertTrue("Seeded reddit row should match 'brutalist' in selftext", cursor.moveToFirst())
            assertEquals("post-1", cursor.getString(0))
        }

        // AFTER INSERT trigger keeps the FTS shadow live for new parent rows.
        db.execSQL(
            """
            INSERT INTO tweetEntity
                (id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`, pending_delete)
            VALUES
                ('tweet-2', 'fresh searchable content', '2026-05-23T01:00:00Z', 'u1', 'tweet-2', NULL, 'en', 0, 2, 0)
            """.trimIndent()
        )
        db.query(
            "SELECT t.id FROM tweetEntity t JOIN tweet_fts f ON t.rowid = f.rowid WHERE tweet_fts MATCH 'searchable'"
        ).use { cursor ->
            assertTrue("AFTER INSERT trigger should propagate to tweet_fts", cursor.moveToFirst())
            assertEquals("tweet-2", cursor.getString(0))
        }

        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
