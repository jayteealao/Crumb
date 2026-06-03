package com.github.jayteealao.crumbs.debug

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.reddit.data.RedditPrefs
import com.github.jayteealao.twitter.data.Prefs
import com.github.jayteealao.twitter.data.SyncStatusRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cross-source-set test: the test class lives in `androidTest` but references
 * DebugDataInjector from `app/src/debug/`. AGP merges `debug` + `androidTest`
 * source sets at `debugAndroidTest` assembly, so the reference compiles.
 *
 * Runs against an in-memory Room database; Prefs are instantiated against
 * the instrumentation context (DataStore writes to a scratch file).
 */
@RunWith(AndroidJUnit4::class)
class DebugDataInjectorTest {

    private lateinit var db: AppDatabase
    private lateinit var injector: DebugDataInjector

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        injector = DebugDataInjector(
            context = ctx,
            db = db,
            twitterPrefs = Prefs(ctx),
            redditPrefs = RedditPrefs(ctx),
            syncStatusRepository = SyncStatusRepository(
                firestore = FirebaseFirestore.getInstance(),
                auth = FirebaseAuth.getInstance(),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun run_wipeTrue_seedsDeterministicCounts() = runBlocking {
        injector.run(wipe = true)
        assertEquals(5, db.tweetDao().getAllTags().size)
        // The seed emits ids debug-tweet-{1..10}: four base tweets, then the
        // type-filter fixtures (image, video, article, thread-reply), a multi-photo
        // tweet, and an animated_gif tweet. The DAO must return exactly those.
        val tweetIds = db.tweetDao().getAllTweetIds().toSet()
        assertEquals(
            "Seed should insert exactly the ten debug tweets",
            (1..10).map { "debug-tweet-$it" }.toSet(),
            tweetIds,
        )
        assertEquals("debug-tweet-1", db.tweetDao().getLatestBookmark()?.id)
        assertEquals(4, db.redditDao().getPostCount())
    }
}
