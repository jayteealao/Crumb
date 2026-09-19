// Pure URL-selection helpers for the link-preview enrichment pipeline.
//
// `entities.urls[]` on a raw X v2 tweet doc carries every link in the tweet —
// the outbound article, internal x.com quote-tweet permalinks, and t.co media
// wrappers all share the array. The card preview wants exactly one: the first
// genuinely-external destination. The "external" test is kept byte-aligned with
// the ARTICLE type-filter SQL predicate in TweetDao
// (`expanded_url NOT LIKE '%twitter.com%' AND NOT LIKE '%x.com%'`) so a tweet
// the server enriches is exactly a tweet the ARTICLE filter and the mapper's
// `ContentType.Link` classification will surface — no drift between the writer
// and the readers.

/** One raw `entities.urls[]` element as X v2 emits it (snake_case). */
export interface RawUrlEntity {
  start?: number;
  end?: number;
  url?: string;
  expanded_url?: string;
  display_url?: string;
  unwound_url?: string;
  // Present only when the URL is a media attachment (pic.twitter.com / a photo
  // or video card). Media links belong to image-rendering / inline-video, never
  // to a link preview, so any url entity carrying one is skipped.
  media_key?: string;
  [key: string]: unknown;
}

/**
 * True when `expanded` points at a non-twitter/x.com destination. Substring
 * match (not host-exact) to mirror the ARTICLE SQL `LIKE '%twitter.com%'`
 * exactly — accepting the same known imprecision (e.g. `box.com` contains
 * `x.com`) so the server-enriched set never diverges from the filtered set.
 */
export function isExternalUrl(expanded: string | undefined | null): boolean {
  if (!expanded) return false;
  return !expanded.includes("twitter.com") && !expanded.includes("x.com");
}

/**
 * The first external outbound link in `entities.urls[]`, or null when the tweet
 * has none (text-only, or only internal/media links). Media-attachment URLs
 * (those carrying a `media_key`) are skipped regardless of host.
 */
export function pickExternalUrl(entities: unknown): RawUrlEntity | null {
  const urls = (entities as { urls?: unknown })?.urls;
  if (!Array.isArray(urls)) return null;
  for (const raw of urls as RawUrlEntity[]) {
    if (!raw || typeof raw !== "object") continue;
    if (raw.media_key) continue;
    if (isExternalUrl(raw.expanded_url)) return raw;
  }
  return null;
}
