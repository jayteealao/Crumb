package com.github.jayteealao.crumbs.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * AppDatabase migrations live in this file rather than embedded in
 * `DatabaseModule.kt` so the DI module stays focused on Hilt wiring. Add the
 * next migration here in ordered declaration form, then include it in
 * [ALL_MIGRATIONS] below. [MigrationTest] covers each one in
 * `androidTest`.
 */

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tags` (
                `name` TEXT NOT NULL,
                PRIMARY KEY(`name`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tweet_tags` (
                `tweetId` TEXT NOT NULL,
                `tagName` TEXT NOT NULL,
                PRIMARY KEY(`tweetId`, `tagName`),
                FOREIGN KEY(`tweetId`) REFERENCES `tweetentity`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`tagName`) REFERENCES `tags`(`name`) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweet_tags_tweetId` ON `tweet_tags` (`tweetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweet_tags_tagName` ON `tweet_tags` (`tagName`)")
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fix tweet_tags table foreign key reference case issue.
        db.execSQL(
            """
            CREATE TEMPORARY TABLE `tweet_tags_backup` (
                `tweetId` TEXT NOT NULL,
                `tagName` TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("INSERT INTO `tweet_tags_backup` SELECT `tweetId`, `tagName` FROM `tweet_tags`")
        db.execSQL("DROP TABLE `tweet_tags`")
        db.execSQL(
            """
            CREATE TABLE `tweet_tags` (
                `tweetId` TEXT NOT NULL,
                `tagName` TEXT NOT NULL,
                PRIMARY KEY(`tweetId`, `tagName`),
                FOREIGN KEY(`tweetId`) REFERENCES `tweetEntity`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`tagName`) REFERENCES `tags`(`name`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO `tweet_tags` SELECT `tweetId`, `tagName` FROM `tweet_tags_backup`")
        db.execSQL("DROP TABLE `tweet_tags_backup`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweet_tags_tweetId` ON `tweet_tags` (`tweetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweet_tags_tagName` ON `tweet_tags` (`tagName`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reddit_posts` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `selftext` TEXT NOT NULL,
                `author` TEXT NOT NULL,
                `subreddit` TEXT NOT NULL,
                `subreddit_prefixed` TEXT NOT NULL,
                `created_utc` INTEGER NOT NULL,
                `url` TEXT NOT NULL,
                `permalink` TEXT NOT NULL,
                `thumbnail` TEXT,
                `num_comments` INTEGER NOT NULL,
                `score` INTEGER NOT NULL,
                `is_self` INTEGER NOT NULL,
                `is_video` INTEGER NOT NULL,
                `domain` TEXT NOT NULL,
                `link_flair_text` TEXT,
                `gilded` INTEGER NOT NULL,
                `over_18` INTEGER NOT NULL,
                `order` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_posts_author` ON `reddit_posts` (`author`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_posts_subreddit` ON `reddit_posts` (`subreddit`)")
    }
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deleted_bookmarks` (
                `bookmarkId` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL,
                PRIMARY KEY(`bookmarkId`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v5 → v6: widen `deleted_bookmarks` PK from `bookmarkId` alone to the
 * composite `(bookmarkId, source)`. SQLite cannot ALTER PRIMARY KEY, so the
 * table is recreated; INSERT OR IGNORE collapses any pre-existing duplicates
 * (which the old single-column PK already prevented).
 *
 * NOTE on atomicity: Room runs each Migration.migrate(db) callback inside
 * an outer transaction (`SupportSQLiteDatabase.beginTransaction()` /
 * `endTransaction()` are managed by RoomOpenHelper). The four execSQL calls
 * below therefore commit or rollback together — a torn process cannot leave
 * the DB with `deleted_bookmarks_new` populated but the original table
 * still present.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_bookmarks_new` (" +
                "`bookmarkId` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`bookmarkId`, `source`))"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `deleted_bookmarks_new` (`bookmarkId`, `source`, `deletedAt`) " +
                "SELECT `bookmarkId`, `source`, `deletedAt` FROM `deleted_bookmarks`"
        )
        db.execSQL("DROP TABLE `deleted_bookmarks`")
        db.execSQL("ALTER TABLE `deleted_bookmarks_new` RENAME TO `deleted_bookmarks`")
    }
}

/**
 * v6 → v7: index `order` on tweetEntity and reddit_posts.
 *
 * The feed paging queries ORDER BY `order` DESC, but neither table had an
 * index on that column — Room degraded to a full-table scan on every page
 * boundary as the bookmark count grew.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweetEntity_order` ON `tweetEntity` (`order`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_posts_order` ON `reddit_posts` (`order`)")
    }
}

/**
 * v7 → v8: index FK columns on pollIds and mediaKeys. Without these the
 * parent tweetEntity cascade scans the whole child table on every update.
 */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pollIds_tweetId` ON `pollIds` (`tweetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mediaKeys_tweet_id` ON `mediaKeys` (`tweet_id`)")
    }
}

/**
 * v8 → v9: add Reddit-side tag cross-reference table. Routing Reddit tags
 * through `tweet_tags` (which carries an FK to `tweetEntity.id`) crashed
 * with SQLITE_CONSTRAINT_FOREIGNKEY; this table is source-scoped and FK-free.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reddit_tag_crossref` (" +
                "`postId` TEXT NOT NULL, " +
                "`tagName` TEXT NOT NULL, " +
                "PRIMARY KEY(`postId`, `tagName`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_tag_crossref_postId` ON `reddit_tag_crossref` (`postId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_tag_crossref_tagName` ON `reddit_tag_crossref` (`tagName`)")
    }
}

/**
 * v9 → v10: surface the server-side `pending_delete` flag on `tweetEntity`.
 * `daily-poll` writes this on tweet docs that disappear from a fresh X
 * bookmarks page; the device renders strikethrough + swipe affordances when
 * the column is true. INTEGER NOT NULL DEFAULT 0 maps Kotlin Boolean → SQLite
 * with the safe default for every existing row.
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tweetEntity` ADD COLUMN `pending_delete` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v10 → v11: add `tweet_fts` and `reddit_fts` virtual tables (FTS4, unicode61
 * tokenizer) backed by `tweetEntity.text` and `reddit_posts.title` +
 * `reddit_posts.selftext`. The content-sync triggers mirror what Room's
 * generated `createAllTables` would emit on a fresh install — they keep the
 * FTS shadow tables aligned with the parent rows on every INSERT/UPDATE/DELETE
 * (Room only writes those triggers at fresh-DB creation time, so a migration
 * has to author them itself). The closing `INSERT INTO <fts>(<fts>) VALUES
 * ('rebuild')` repopulates the indices from the existing parent rows in one
 * pass; subsequent writes ride the triggers.
 *
 * The FTS row identifier is SQLite's implicit `rowid` on the parent table, not
 * the entities' String PK — `@Fts4(contentEntity = ...)` relies on this
 * linkage. DAO joins use `JOIN <fts> ON parent.rowid = <fts>.rowid`.
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // tweet_fts — single `text` column from tweetEntity.text
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `tweet_fts` USING FTS4(" +
                "`text` TEXT NOT NULL, content=`tweetEntity`, tokenize=unicode61)"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_tweet_fts_BEFORE_UPDATE` " +
                "BEFORE UPDATE ON `tweetEntity` " +
                "BEGIN DELETE FROM `tweet_fts` WHERE `docid`=OLD.`rowid`; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_tweet_fts_BEFORE_DELETE` " +
                "BEFORE DELETE ON `tweetEntity` " +
                "BEGIN DELETE FROM `tweet_fts` WHERE `docid`=OLD.`rowid`; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_tweet_fts_AFTER_UPDATE` " +
                "AFTER UPDATE ON `tweetEntity` " +
                "BEGIN INSERT INTO `tweet_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_tweet_fts_AFTER_INSERT` " +
                "AFTER INSERT ON `tweetEntity` " +
                "BEGIN INSERT INTO `tweet_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END"
        )
        // WARNING: FTS4 'rebuild' re-tokenizes ALL rows in the parent table synchronously.
        // On devices with 10,000+ bookmarks this may block for >1s inside the migration
        // transaction. The Room migration thread is not the main thread, but the cumulative
        // cost across 10 sequential migrations risks the SQLite busy timeout.
        // TODO: Move FTS rebuilds to a post-migration OneTimeWorkRequest or RoomDatabase.Callback#onOpen.
        db.execSQL("INSERT INTO `tweet_fts`(`tweet_fts`) VALUES('rebuild')")

        // reddit_fts — `title` + `selftext` columns from reddit_posts
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `reddit_fts` USING FTS4(" +
                "`title` TEXT NOT NULL, `selftext` TEXT NOT NULL, " +
                "content=`reddit_posts`, tokenize=unicode61)"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_reddit_fts_BEFORE_UPDATE` " +
                "BEFORE UPDATE ON `reddit_posts` " +
                "BEGIN DELETE FROM `reddit_fts` WHERE `docid`=OLD.`rowid`; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_reddit_fts_BEFORE_DELETE` " +
                "BEFORE DELETE ON `reddit_posts` " +
                "BEGIN DELETE FROM `reddit_fts` WHERE `docid`=OLD.`rowid`; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_reddit_fts_AFTER_UPDATE` " +
                "AFTER UPDATE ON `reddit_posts` " +
                "BEGIN INSERT INTO `reddit_fts`(`docid`, `title`, `selftext`) " +
                "VALUES (NEW.`rowid`, NEW.`title`, NEW.`selftext`); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_reddit_fts_AFTER_INSERT` " +
                "AFTER INSERT ON `reddit_posts` " +
                "BEGIN INSERT INTO `reddit_fts`(`docid`, `title`, `selftext`) " +
                "VALUES (NEW.`rowid`, NEW.`title`, NEW.`selftext`); END"
        )
        // WARNING: FTS4 'rebuild' re-tokenizes ALL rows in the parent table synchronously.
        // On devices with 10,000+ bookmarks this may block for >1s inside the migration
        // transaction. The Room migration thread is not the main thread, but the cumulative
        // cost across 10 sequential migrations risks the SQLite busy timeout.
        // TODO: Move FTS rebuilds to a post-migration OneTimeWorkRequest or RoomDatabase.Callback#onOpen.
        db.execSQL("INSERT INTO `reddit_fts`(`reddit_fts`) VALUES('rebuild')")
    }
}

/**
 * v11 → v12: per-uid cursor checkpoint for the streaming Twitter sync.
 * `sync_progress` stores the high-watermark (newest createdAt seen) and the
 * low-watermark (oldest createdAt successfully written) as `(createdAt,
 * tweetId)` tuples so the worker can resume mid-stream after process death.
 */
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_progress` (" +
                "`uid` TEXT NOT NULL, " +
                "`last_high_cursor_created_at` TEXT, " +
                "`last_high_cursor_tweet_id` TEXT, " +
                "`last_low_cursor_created_at` TEXT, " +
                "`last_low_cursor_tweet_id` TEXT, " +
                "`total_batches_ingested` INTEGER NOT NULL, " +
                "`last_updated_at_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`uid`))"
        )
    }
}

/**
 * v12 → v13: add the server-stamped `retrieved_at` column (nullable epoch-millis), the
 * primary feed recency key, plus the composite `(retrieved_at, created_at)` index that backs
 * `ORDER BY retrieved_at DESC, created_at DESC` as a reverse B-tree scan (no full-table sort).
 * Pre-existing rows get NULL `retrieved_at`, which sorts last under a DESC order. The index
 * name must match Room's generated `index_<table>_<col1>_<col2>` or schema validation fails.
 */
val MIGRATION_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tweetEntity` ADD COLUMN `retrieved_at` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tweetEntity_retrieved_at_created_at` " +
                "ON `tweetEntity` (`retrieved_at`, `created_at`)"
        )
    }
}

/**
 * v13 → v14: add an index on `tweetEntity.conversation_id`. It backs the THREAD type-filter's
 * correlated "a sibling tweet shares this conversation" subquery so it runs as an index lookup
 * rather than an O(n²) self-join scan. Index-only change — no column or data is added, so
 * pre-existing rows are untouched. The index name must match Room's generated
 * `index_<table>_<col>` (`index_tweetEntity_conversation_id`) or schema validation fails.
 */
val MIGRATION_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tweetEntity_conversation_id` " +
                "ON `tweetEntity` (`conversation_id`)"
        )
    }
}

/** Full list registered by the DI module's `addMigrations(*ALL_MIGRATIONS)`. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
)
