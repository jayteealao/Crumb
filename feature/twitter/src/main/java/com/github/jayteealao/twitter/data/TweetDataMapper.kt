package com.github.jayteealao.twitter.data

import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.crumbs.models.VideoVariant
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.player.VariantSelection
import com.github.jayteealao.twitter.util.parseTweetTimestamp

/**
 * Maps a [TweetData] database projection to the shared [Bookmark] domain model.
 *
 * Kept in the data layer so any module that holds a [TweetData] reference can call
 * this without importing from the presentation (screens) package.
 *
 * @param tags Per-tweet tag list sourced from the tag table; defaults to empty.
 */
fun TweetData.toBookmark(tags: List<String> = emptyList()): Bookmark {
    // First external (non-twitter/x.com) url entity — the article the preview
    // links to. Replaces the old `text.contains("http")` heuristic so the Link
    // type is authoritative (matching the ARTICLE filter SQL exactly), which
    // also makes count-and-number's ARTICLE count honest for synced data. Null
    // when the tweet has no outbound link (only internal/media links, or none).
    val externalLink = tweetTextAnnotation.firstOrNull { it.type == "urls" && it.expandedUrl.isExternalLink() }
    val contentType = when {
        media.any { it.type == "video" || it.type == "animated_gif" } -> ContentType.Video
        media.any { it.type == "photo" } -> ContentType.Image
        externalLink != null -> ContentType.Link
        else -> ContentType.Text
    }
    // Keep every photo URL (the card grid + viewer page through all of them);
    // imageUrl stays the primary single URL for back-compat. Previously only the
    // first photo survived, so multi-image tweets silently lost their extra photos.
    val imageUrls = media.filter { it.type == "photo" }.mapNotNull { it.url }
    val imageUrl = imageUrls.firstOrNull()
    // Video + animated_gif (same muted tap-to-play path): carry the persisted stream
    // variants and the poster frame so the card can play inline. videoUrl is the single
    // best playable stream for back-compat, falling back to the row's flat url.
    val videoMedia = media.firstOrNull { it.type == "video" || it.type == "animated_gif" }
    val videoVariants = videoMedia?.videoVariants.orEmpty().map {
        VideoVariant(contentType = it.contentType, url = it.url, bitRate = it.bitRate)
    }
    val videoThumbnailUrl = videoMedia?.previewImageUrl
    val videoUrl = VariantSelection.bestUrl(videoVariants) ?: videoMedia?.url
    // Prefer the server-stamped retrieval time; fall back to the tweet's own creation time;
    // when neither is available/parseable, use the unknown-time sentinel rather than
    // fabricating "now" (which produced the long-standing wrong "X months ago" label).
    val timestamp = tweet.retrievedAt
        ?: parseTweetTimestamp(tweet.createdAt)
        ?: Bookmark.UNKNOWN_TIME
    // Quoted tweet (orthogonal to contentType — a quote co-exists with the parent's own
    // media/link/text). The first type=="quoted" reference is the quote; a reference row
    // with no resolved body ⇒ unavailable (quotedTweetId set, quotedText null). The
    // permalink uses the resolved handle, falling back to the handle-less /i/status form.
    val quotedRef = referencedTweets.firstOrNull { it.type == "quoted" }
    val quotedBody = quotedTweets.firstOrNull { it.tweet.id == quotedRef?.id }
    val quotedUsername = quotedBody?.author?.username
    val quotedTweetUrl = quotedRef?.id?.let { qId ->
        if (quotedUsername != null) "https://twitter.com/$quotedUsername/status/$qId"
        else "https://x.com/i/status/$qId"
    }
    val title = tweet.text.lines().firstOrNull()?.take(100) ?: tweet.text.take(100)
    return Bookmark(
        id = tweet.id,
        source = BookmarkSource.Twitter,
        author = "@${user.username}",
        title = title,
        previewText = tweet.text,
        imageUrl = imageUrl,
        imageUrls = imageUrls,
        videoUrl = videoUrl,
        videoThumbnailUrl = videoThumbnailUrl,
        videoVariants = videoVariants,
        // Link-preview fields (null for non-link tweets). displayUrl falls back to
        // the expanded URL's host when X did not supply a short label; title /
        // description / image are the server-enriched OpenGraph metadata.
        linkUrl = externalLink?.expandedUrl,
        linkDisplayUrl = externalLink?.displayUrl ?: externalLink?.expandedUrl?.linkHost(),
        linkTitle = externalLink?.title,
        linkDescription = externalLink?.description,
        linkImageUrl = externalLink?.imageUrl,
        // Quoted-tweet fields (null for non-quote tweets). quotedTweetId set with a null
        // quotedText is the unavailable state (deleted/protected quote). The handle is
        // prefixed with '@' for display; the URL above carries the bare username.
        quotedTweetId = quotedRef?.id,
        quotedText = quotedBody?.tweet?.text,
        quotedAuthorName = quotedBody?.author?.name,
        quotedAuthorHandle = quotedUsername?.let { "@$it" },
        quotedTweetUrl = quotedTweetUrl,
        contentType = contentType,
        savedAt = timestamp,
        tags = tags,
        isThread = false,
        threadCount = 1,
        isDeleted = false,
        pendingDelete = tweet.pendingDelete,
        sourceUrl = "https://twitter.com/${user.username}/status/${tweet.id}",
        // The card's index strip shows this as the per-row "number in the DB".
        dbNumber = dbRowId,
    )
}

/**
 * True when this expanded URL points at a genuinely external destination (not a
 * twitter.com / x.com permalink). Kept byte-aligned with the ARTICLE type-filter
 * SQL predicate (`NOT LIKE '%twitter.com%' AND NOT LIKE '%x.com%'`) and the
 * server-side picker so the writer and every reader agree on what is a "link".
 */
internal fun String?.isExternalLink(): Boolean =
    this != null && !contains("twitter.com") && !contains("x.com")

/** Host of a URL (stripped of `www.`) for the preview's domain label; the raw URL on parse failure. */
internal fun String.linkHost(): String =
    runCatching { java.net.URI(this).host?.removePrefix("www.") }.getOrNull() ?: this
