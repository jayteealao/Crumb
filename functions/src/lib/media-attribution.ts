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
 * Resolve a tweet's owned media against the page-level media bag. Returns one entry
 * per owned key that is actually present in `pageMedia` (an owned key with no
 * resolvable media object is skipped — the media doc cannot be written without its
 * fields, and a junction pointing at a missing media doc would render nothing).
 */
export function ownedMediaFor(
  tweet: Record<string, unknown>,
  pageMedia: MediaLike[] | undefined,
): Array<{ mediaKey: string; media: MediaLike }> {
  const owned = ownedMediaKeys(tweet);
  if (owned.length === 0) return [];
  const byKey = new Map<string, MediaLike>();
  for (const m of pageMedia ?? []) {
    if (m && typeof m.media_key === "string") byKey.set(m.media_key, m);
  }
  const out: Array<{ mediaKey: string; media: MediaLike }> = [];
  for (const key of owned) {
    const media = byKey.get(key);
    if (media) out.push({ mediaKey: key, media });
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
