// Per-tweet media attribution — the fix for the page-level cross-product.
//
// The X v2 bookmarks endpoint returns `includes.media` once per *page* (every
// media object for every tweet on that page). Each tweet's OWN media is named by
// its `attachments.media_keys`; a quoted/retweeted tweet's media is NOT promoted
// into the quoter — it lives on the quoted tweet object inside `includes.tweets`.
// So the correct tweet↔media join is each tweet's own `attachments.media_keys`
// resolved against the page-level media bag, NEVER the whole bag per tweet (the
// cross-product that attached a neighbour's image/video to the wrong card).
//
// These helpers are pure (no firebase-admin / Firestore imports) so lib/poll.ts
// and the backfillTweetMedia handler share one tested implementation and write
// byte-identical media + junction docs.

import { logger } from "firebase-functions/v2";

export interface MediaLike {
  media_key: string;
  [key: string]: unknown;
}

/**
 * The media keys a tweet OWNS — its own `attachments.media_keys`, de-duplicated,
 * empties/non-strings dropped. Read defensively (`attachments` is `unknown` on the
 * page-level `includes.tweets` objects) so both the top-level tweet and a quoted
 * tweet can be passed without a cast.
 */
export function ownedMediaKeys(tweet: Record<string, unknown>): string[] {
  const attachments = tweet.attachments as { media_keys?: unknown } | undefined | null;
  const keys = attachments?.media_keys;
  if (!Array.isArray(keys)) return [];
  return [
    ...new Set(keys.filter((k): k is string => typeof k === "string" && k.length > 0)),
  ];
}

/** Firestore doc id for the tweet↔media junction (one per owned media key). */
export function mediaJunctionDocId(tweetId: string, mediaKey: string): string {
  return `${tweetId}_media_${mediaKey}`;
}

/** The junction doc payload, identical for the poll write and the backfill rewrite. */
export function mediaJunctionData(tweetId: string, mediaKey: string): Record<string, unknown> {
  return { tweetId, mediaKey, kind: "media" };
}

/**
 * Build a Map from media_key → MediaLike from the page-level includes.media array.
 * Callers should build this once per page (not per tweet) and pass it to
 * {@link ownedMediaFor} to avoid O(tweets × media) rebuilds per page.
 */
export function buildPageMediaMap(pageMedia: MediaLike[] | undefined): Map<string, MediaLike> {
  const byKey = new Map<string, MediaLike>();
  for (const m of pageMedia ?? []) {
    if (m && typeof m.media_key === "string") byKey.set(m.media_key, m);
  }
  return byKey;
}

/**
 * Resolve a tweet's owned media against the page-level media bag. Returns one entry
 * per owned key that is actually present in `pageMediaMap` (an owned key with no
 * resolvable media object is warned and skipped — the media doc cannot be written
 * without its fields, and a junction pointing at a missing media doc would render
 * nothing).
 *
 * M5: accepts a pre-built Map (built once per page via {@link buildPageMediaMap})
 * instead of rebuilding it on every call.
 * M1: emits a logger.warn for any owned key that has no entry in the map so the
 * miss is visible in Cloud Logging.
 */
export function ownedMediaFor(
  tweet: Record<string, unknown>,
  pageMediaMap: Map<string, MediaLike>,
  tweetId?: string,
): Array<{ mediaKey: string; media: MediaLike }> {
  const owned = ownedMediaKeys(tweet);
  if (owned.length === 0) return [];
  const out: Array<{ mediaKey: string; media: MediaLike }> = [];
  for (const key of owned) {
    const media = pageMediaMap.get(key);
    if (media) {
      out.push({ mediaKey: key, media });
    } else {
      // M1: warn on unresolved owned media key so the miss surfaces in logs.
      logger.warn("media_attribution_unresolved_key", {
        mediaKey: key,
        tweetId: tweetId ?? (tweet.id as string | undefined) ?? "unknown",
      });
    }
  }
  return out;
}

/**
 * Map a raw X-API media object to the canonical Firestore media doc shape
 * (snake_case raw `...mr` spread + camelCase aliases + typed `variants`), exactly
 * matching what poll.ts wrote inline before the attribution refactor — so poll and
 * backfill emit identical media docs. `serverTimestamp` is injected by the caller
 * (FieldValue.serverTimestamp()) so this module stays free of the firebase-admin
 * import. `bit_rate` is absent on adaptive (HLS/DASH) variants, so it defaults to 0.
 */
export function mediaDocData(media: MediaLike, serverTimestamp: unknown): Record<string, unknown> {
  const mr = media as Record<string, unknown>;
  const rawVariants = (mr.variants as Array<Record<string, unknown>> | undefined) ?? [];
  const variants = rawVariants.map((v) => ({
    bitRate: typeof v.bit_rate === "number" ? v.bit_rate : 0,
    contentType: v.content_type,
    url: v.url,
  }));
  return {
    ...mr,
    mediaKey: media.media_key,
    previewImageUrl: mr.preview_image_url,
    durationMs: mr.duration_ms,
    altText: mr.alt_text,
    variants,
    updatedAt: serverTimestamp,
  };
}
