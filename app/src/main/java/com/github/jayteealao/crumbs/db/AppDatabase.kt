package com.github.jayteealao.crumbs.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.jayteealao.twitter.models.MediaConverters
import com.github.jayteealao.crumbs.data.DeletedBookmark
import com.github.jayteealao.crumbs.data.DeletedBookmarkDao
import com.github.jayteealao.crumbs.data.SyncProgress
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.reddit.data.RedditDao
import com.github.jayteealao.reddit.models.RedditPostEntity
import com.github.jayteealao.reddit.models.RedditTagCrossRef
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.PollIds
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetContextAnnotationEntity
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetIncludesEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.TweetReferencedTweets
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation
import com.github.jayteealao.twitter.models.TwitterUserEntity

@Database(
    entities = [
        TweetEntity::class,
        TwitterUserEntity::class,
        TweetMediaEntity::class,
        TweetIncludesEntity::class,
        TweetReferencedTweets::class,
        TweetContextAnnotationEntity::class,
        TweetPublicMetrics::class,
        TweetTextEntityAnnotation::class,
        PollIds::class,
        MediaKeys::class,
        TagEntity::class,
        TweetTagCrossRef::class,
        RedditPostEntity::class,
        RedditTagCrossRef::class,
        DeletedBookmark::class,
        TweetFts::class,
        RedditFts::class,
        SyncProgress::class,
    ],
    version = 17,
    exportSchema = true
)
// MediaConverters is the project's first TypeConverter — backs the JSON
// `tweetMedia.video_variants` column (List<Variant> ⇄ TEXT) added in v15.
@TypeConverters(MediaConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tweetDao(): TweetDao
    abstract fun redditDao(): RedditDao
    abstract fun deletedBookmarkDao(): DeletedBookmarkDao
    abstract fun tweetFtsDao(): TweetFtsDao
    abstract fun redditFtsDao(): RedditFtsDao
    abstract fun syncProgressDao(): SyncProgressDao
}
