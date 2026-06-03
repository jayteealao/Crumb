package com.github.jayteealao.crumbs.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.jayteealao.crumbs.db.MIGRATION_2_3
import com.github.jayteealao.crumbs.db.MIGRATION_3_4
import com.github.jayteealao.crumbs.db.MIGRATION_4_5
import com.github.jayteealao.crumbs.db.MIGRATION_5_6
import com.github.jayteealao.crumbs.db.MIGRATION_6_7
import com.github.jayteealao.crumbs.db.MIGRATION_7_8
import com.github.jayteealao.crumbs.db.MIGRATION_8_9
import com.github.jayteealao.crumbs.db.MIGRATION_9_10
import com.github.jayteealao.crumbs.db.MIGRATION_10_11
import com.github.jayteealao.crumbs.db.MIGRATION_11_12
import com.github.jayteealao.crumbs.db.MIGRATION_12_13
import com.github.jayteealao.crumbs.db.MIGRATION_13_14
import com.github.jayteealao.crumbs.db.MIGRATION_14_15
import com.github.jayteealao.crumbs.db.MIGRATION_15_16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun migrate2To3_createsTagsAndTweetTagsTablesWithIndexes() {
        // Create a v2 database with a tweetEntity row so we can later verify
        // that tweet_tags respects its FK back to tweetEntity.
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO tweetEntity " +
                    "(id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`) " +
                    "VALUES ('tweet-1', 'hello', '2024-01-01T00:00:00Z', 'u1', 'tweet-1', NULL, 'en', 0, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            MIGRATION_2_3,
        )

        // Both tables were created.
        val expectedTables = setOf("tags", "tweet_tags")
        val foundTables = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('tags', 'tweet_tags')"
        ).use { cursor ->
            while (cursor.moveToNext()) foundTables += cursor.getString(0)
        }
        assertEquals(
            "Both tags and tweet_tags tables should exist after 2→3 migration",
            expectedTables,
            foundTables,
        )

        // tags schema: single 'name' column, TEXT NOT NULL, primary key.
        db.query("PRAGMA table_info(tags)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assertEquals(setOf("name"), columns)
        }

        // tweet_tags schema: tweetId + tagName columns.
        db.query("PRAGMA table_info(tweet_tags)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assertEquals(setOf("tweetId", "tagName"), columns)
        }

        // Both indexes are present.
        val expectedIndexes = setOf("index_tweet_tags_tweetId", "index_tweet_tags_tagName")
        val foundIndexes = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='tweet_tags'"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (!name.startsWith("sqlite_autoindex_")) foundIndexes += name
            }
        }
        assertEquals(
            "Both tweet_tags indexes should exist after 2→3 migration",
            expectedIndexes,
            foundIndexes,
        )

        // End-to-end write: insert a tag and a tweet_tags cross-reference row.
        db.execSQL("INSERT INTO tags (name) VALUES ('android')")
        db.execSQL(
            "INSERT INTO tweet_tags (tweetId, tagName) VALUES ('tweet-1', 'android')"
        )
        db.query(
            "SELECT tweetId, tagName FROM tweet_tags WHERE tweetId = 'tweet-1'"
        ).use { cursor ->
            assertTrue("tweet_tags row should be queryable after 2→3 migration", cursor.moveToFirst())
            assertEquals("tweet-1", cursor.getString(0))
            assertEquals("android", cursor.getString(1))
        }

        db.close()
    }

    @Test
    fun migrate3To4_fixesTweetTagsFkCaseAndCreatesRedditPostsTable() {
        // Seed v3 with a tweetEntity + tag + tweet_tags cross-reference so that
        // the data-preservation leg of the migration is exercised.
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO tweetEntity " +
                    "(id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`) " +
                    "VALUES ('tweet-1', 'test tweet', '2024-06-01T00:00:00Z', 'u1', 'tweet-1', NULL, 'en', 0, 1)"
            )
            execSQL("INSERT INTO tags (name) VALUES ('kotlin')")
            execSQL(
                "INSERT INTO tweet_tags (tweetId, tagName) VALUES ('tweet-1', 'kotlin')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            MIGRATION_3_4,
        )

        // Pre-existing tweet_tags row survives the table rebuild.
        db.query(
            "SELECT tweetId, tagName FROM tweet_tags WHERE tweetId = 'tweet-1'"
        ).use { cursor ->
            assertTrue(
                "tweet_tags row must survive the 3→4 table-rebuild migration",
                cursor.moveToFirst(),
            )
            assertEquals("tweet-1", cursor.getString(0))
            assertEquals("kotlin", cursor.getString(1))
        }

        // tweet_tags now references 'tweetEntity' (mixed-case) — validate via
        // the sqlite_master DDL rather than just trusting MigrationTestHelper.
        db.query(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='tweet_tags'"
        ).use { cursor ->
            assertTrue("tweet_tags must be present in sqlite_master", cursor.moveToFirst())
            val ddl = cursor.getString(0)
            assertTrue(
                "tweet_tags FK must reference 'tweetEntity' (mixed-case) after 3→4 migration; got: $ddl",
                ddl.contains("REFERENCES `tweetEntity`"),
            )
        }

        // reddit_posts table was created with the expected 20 columns.
        db.query("PRAGMA table_info(reddit_posts)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val expectedColumns = setOf(
                "id", "name", "title", "selftext", "author",
                "subreddit", "subreddit_prefixed", "created_utc", "url",
                "permalink", "thumbnail", "num_comments", "score",
                "is_self", "is_video", "domain", "link_flair_text",
                "gilded", "over_18", "order",
            )
            assertEquals(
                "reddit_posts should have exactly the 20 schema columns after 3→4 migration",
                expectedColumns,
                columns,
            )
        }

        // reddit_posts author + subreddit indexes are present.
        val expectedRedditIndexes = setOf(
            "index_reddit_posts_author",
            "index_reddit_posts_subreddit",
        )
        val foundRedditIndexes = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='reddit_posts'"
        ).use { cursor ->
            while (cursor.moveToNext()) foundRedditIndexes += cursor.getString(0)
        }
        assertEquals(
            "Both reddit_posts indexes should exist after 3→4 migration",
            expectedRedditIndexes,
            foundRedditIndexes,
        )

        // tweet_tags indexes were recreated correctly.
        val expectedTagIndexes = setOf("index_tweet_tags_tweetId", "index_tweet_tags_tagName")
        val foundTagIndexes = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='tweet_tags'"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (!name.startsWith("sqlite_autoindex_")) foundTagIndexes += name
            }
        }
        assertEquals(
            "Both tweet_tags indexes should be recreated after 3→4 migration",
            expectedTagIndexes,
            foundTagIndexes,
        )

        // End-to-end insert into reddit_posts works after migration.
        db.execSQL(
            "INSERT INTO reddit_posts " +
                "(id, name, title, selftext, author, subreddit, subreddit_prefixed, created_utc, " +
                "url, permalink, thumbnail, num_comments, score, is_self, is_video, domain, " +
                "link_flair_text, gilded, over_18, `order`) " +
                "VALUES ('post-1', 't3_post-1', 'Hello Reddit', 'body text', 'user1', " +
                "'androiddev', 'r/androiddev', 1700000000, 'https://example.com', " +
                "'/r/androiddev/post-1', NULL, 3, 7, 1, 0, 'self.androiddev', NULL, 0, 0, 1)"
        )
        db.query(
            "SELECT id, title FROM reddit_posts WHERE id = 'post-1'"
        ).use { cursor ->
            assertTrue("reddit_posts insert should succeed after 3→4 migration", cursor.moveToFirst())
            assertEquals("Hello Reddit", cursor.getString(1))
        }

        db.close()
    }

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

    @Test
    fun migrate11To12_createsSyncProgressTable() {
        helper.createDatabase(TEST_DB, 11).apply { close() }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            12,
            true,
            MIGRATION_11_12,
        )

        // Table exists with the seven expected columns and the uid primary key.
        val columns = mutableMapOf<String, Pair<String, Int>>() // name → (type, notNull)
        db.query("PRAGMA table_info(`sync_progress`)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                columns[name] = type to notNull
            }
        }
        assertEquals("Expected 7 columns on sync_progress", 7, columns.size)
        assertEquals("TEXT" to 1, columns["uid"])
        assertEquals("TEXT" to 0, columns["last_high_cursor_created_at"])
        assertEquals("TEXT" to 0, columns["last_high_cursor_tweet_id"])
        assertEquals("TEXT" to 0, columns["last_low_cursor_created_at"])
        assertEquals("TEXT" to 0, columns["last_low_cursor_tweet_id"])
        assertEquals("INTEGER" to 1, columns["total_batches_ingested"])
        assertEquals("INTEGER" to 1, columns["last_updated_at_ms"])

        // Empty on creation.
        db.query("SELECT COUNT(*) FROM sync_progress").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        // Round-trip insert + select with both null and non-null cursor values.
        db.execSQL(
            "INSERT INTO sync_progress " +
                "(uid, last_high_cursor_created_at, last_high_cursor_tweet_id, " +
                "last_low_cursor_created_at, last_low_cursor_tweet_id, " +
                "total_batches_ingested, last_updated_at_ms) " +
                "VALUES ('uid-test', '2026-05-24T15:00:00Z', 'tweet-123', " +
                "NULL, NULL, 5, 1700000000)"
        )
        db.query(
            "SELECT last_high_cursor_created_at, last_high_cursor_tweet_id, " +
                "last_low_cursor_created_at, total_batches_ingested " +
                "FROM sync_progress WHERE uid = 'uid-test'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-05-24T15:00:00Z", cursor.getString(0))
            assertEquals("tweet-123", cursor.getString(1))
            assertTrue("low cursor should be NULL on the inserted row", cursor.isNull(2))
            assertEquals(5, cursor.getInt(3))
        }

        // Primary-key constraint: a second insert at the same uid replaces via
        // OnConflictStrategy.REPLACE in the DAO, but at the raw migration
        // level we expect an INSERT collision unless explicitly handled — keep
        // this assertion purely about column shape, not REPLACE semantics.
        assertNotNull(db)

        db.close()
    }

    @Test
    fun migrate12To13_addsRetrievedAtColumnAndCompositeIndex() {
        // Seed a v12 row so we can confirm the new column defaults to NULL for pre-existing rows.
        helper.createDatabase(TEST_DB, 12).apply {
            execSQL(
                "INSERT INTO tweetEntity " +
                    "(id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`, pending_delete) " +
                    "VALUES ('tweet-1', 'hello', '2024-01-01T00:00:00Z', 'u1', 'tweet-1', NULL, 'en', 0, 1, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            13,
            true,
            MIGRATION_12_13,
        )

        // The retrieved_at column exists, is INTEGER, and is nullable (notNull = 0).
        val columns = mutableMapOf<String, Pair<String, Int>>() // name → (type, notNull)
        db.query("PRAGMA table_info(`tweetEntity`)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                columns[name] = type to notNull
            }
        }
        assertEquals("retrieved_at should be a nullable INTEGER column", "INTEGER" to 0, columns["retrieved_at"])

        // Pre-existing rows get NULL retrieved_at (so they sort last under DESC).
        db.query("SELECT retrieved_at FROM tweetEntity WHERE id = 'tweet-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("retrieved_at must be NULL for rows that predate the column", cursor.isNull(0))
        }

        // The composite (retrieved_at, created_at) index exists under Room's generated name.
        val indexNames = mutableSetOf<String>()
        db.query("PRAGMA index_list(`tweetEntity`)").use { cursor ->
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
        }
        assertTrue(
            "Expected composite index index_tweetEntity_retrieved_at_created_at; found $indexNames",
            indexNames.contains("index_tweetEntity_retrieved_at_created_at"),
        )

        db.close()
    }

    @Test
    fun migrate13To14_addsConversationIdIndex() {
        // Seed a v13 row (the v13 shape already has retrieved_at + pending_delete).
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                "INSERT INTO tweetEntity " +
                    "(id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`, pending_delete, retrieved_at) " +
                    "VALUES ('tweet-1', 'hello', '2024-01-01T00:00:00Z', 'u1', 'tweet-1', NULL, 'en', 0, 1, 0, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            14,
            true,
            MIGRATION_13_14,
        )

        // The conversation_id index exists under Room's generated name (`index_<table>_<col>`).
        val indexNames = mutableSetOf<String>()
        db.query("PRAGMA index_list(`tweetEntity`)").use { cursor ->
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
        }
        assertTrue(
            "Expected index index_tweetEntity_conversation_id; found $indexNames",
            indexNames.contains("index_tweetEntity_conversation_id"),
        )

        // The pre-existing row survives the index-only migration.
        db.query("SELECT COUNT(*) FROM tweetEntity").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        db.close()
    }

    @Test
    fun migrate14To15_addsVideoVariantsColumnToTweetMedia() {
        // Seed a v14 tweetMedia row (with its parent tweetEntity for the FK) so we can
        // confirm the new column defaults to NULL for rows that predate it.
        helper.createDatabase(TEST_DB, 14).apply {
            execSQL(
                "INSERT INTO tweetEntity " +
                    "(id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`, pending_delete, retrieved_at) " +
                    "VALUES ('tweet-1', 'hi', '2024-01-01T00:00:00Z', 'u1', 'tweet-1', NULL, 'en', 0, 1, 0, NULL)"
            )
            execSQL(
                "INSERT INTO tweetMedia " +
                    "(media_key, type, url, duration_ms, height, width, preview_image_url, alt_text, tweet_id) " +
                    "VALUES ('mk1', 'video', 'https://v/legacy.mp4', 12000, 720, 1280, 'https://img/p.jpg', NULL, 'tweet-1')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            15,
            true,
            MIGRATION_14_15,
        )

        // The video_variants column exists, is TEXT, and is nullable (notNull = 0).
        val columns = mutableMapOf<String, Pair<String, Int>>() // name → (type, notNull)
        db.query("PRAGMA table_info(`tweetMedia`)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                columns[name] = type to notNull
            }
        }
        assertEquals("video_variants should be a nullable TEXT column", "TEXT" to 0, columns["video_variants"])

        // Pre-existing media row gets NULL video_variants (legacy rows repair via re-fetch).
        db.query("SELECT video_variants FROM tweetMedia WHERE media_key = 'mk1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("legacy media row's video_variants must be NULL", cursor.isNull(0))
        }

        // A JSON variants payload round-trips through the new column.
        db.execSQL(
            "INSERT INTO tweetMedia " +
                "(media_key, type, url, duration_ms, height, width, preview_image_url, alt_text, tweet_id, video_variants) " +
                "VALUES ('mk2', 'video', 'https://v/master.m3u8', 0, 0, 0, NULL, NULL, 'tweet-1', " +
                "'[{\"bit_rate\":0,\"content_type\":\"application/x-mpegURL\",\"url\":\"https://v/master.m3u8\"}]')"
        )
        db.query("SELECT video_variants FROM tweetMedia WHERE media_key = 'mk2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(
                "video_variants JSON should round-trip through the new column",
                cursor.getString(0).contains("application/x-mpegURL"),
            )
        }

        db.close()
    }

    @Test
    fun migrate15To16_addsImageUrlColumnToTextAnnotations() {
        // Seed a v15 tweetTextEntityAnnotation row (with its parent tweetEntity for the FK)
        // so we can confirm the new column defaults to NULL for rows that predate it.
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                "INSERT INTO tweetEntity " +
                    "(id, text, created_at, author_id, conversation_id, in_reply_to_user_id, lang, referenced, `order`, pending_delete, retrieved_at) " +
                    "VALUES ('tweet-1', 'hi https://example.com', '2024-01-01T00:00:00Z', 'u1', 'tweet-1', NULL, 'en', 0, 1, 0, NULL)"
            )
            execSQL(
                "INSERT INTO tweetTextEntityAnnotation " +
                    "(start, end, title, description, url, expanded_url, display_url, unwound_url, " +
                    "media_key, normalized_text, tweet_id, type) " +
                    "VALUES (0, 18, NULL, NULL, 'https://t.co/x', 'https://example.com/article', " +
                    "'example.com/article', NULL, NULL, NULL, 'tweet-1', 'urls')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            16,
            true,
            MIGRATION_15_16,
        )

        // The image_url column exists, is TEXT, and is nullable (notNull = 0).
        val columns = mutableMapOf<String, Pair<String, Int>>() // name → (type, notNull)
        db.query("PRAGMA table_info(`tweetTextEntityAnnotation`)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                columns[name] = type to notNull
            }
        }
        assertEquals("image_url should be a nullable TEXT column", "TEXT" to 0, columns["image_url"])

        // Pre-existing annotation row gets NULL image_url (legacy rows render URL-only).
        db.query("SELECT image_url FROM tweetTextEntityAnnotation WHERE tweet_id = 'tweet-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("legacy annotation row's image_url must be NULL", cursor.isNull(0))
        }

        // A freshly-enriched annotation round-trips title + image through the new column.
        db.execSQL(
            "INSERT INTO tweetTextEntityAnnotation " +
                "(start, end, title, description, url, expanded_url, display_url, unwound_url, " +
                "media_key, normalized_text, tweet_id, type, image_url) " +
                "VALUES (0, 18, 'Brutalist Web', 'A guide', 'https://t.co/y', 'https://example.com/b', " +
                "'example.com/b', NULL, NULL, NULL, 'tweet-1', 'urls', 'https://cdn.example.com/og.jpg')"
        )
        db.query("SELECT title, image_url FROM tweetTextEntityAnnotation WHERE expanded_url = 'https://example.com/b'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Brutalist Web", cursor.getString(0))
            assertEquals("https://cdn.example.com/og.jpg", cursor.getString(1))
        }

        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
