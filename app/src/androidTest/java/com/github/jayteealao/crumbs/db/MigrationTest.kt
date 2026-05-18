package com.github.jayteealao.crumbs.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.jayteealao.crumbs.di.MIGRATION_4_5
import com.github.jayteealao.crumbs.di.MIGRATION_5_6
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
