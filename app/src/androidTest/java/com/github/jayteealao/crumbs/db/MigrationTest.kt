package com.github.jayteealao.crumbs.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.jayteealao.crumbs.di.MIGRATION_4_5
import com.github.jayteealao.crumbs.di.MIGRATION_5_6
import com.github.jayteealao.crumbs.di.MIGRATION_6_7
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
