// oEmbed recovery module for link-preview enrichment.
//
// Two paths raise the rich-preview rate above the HTML-only ceiling:
//   1. Registry-first: for a URL on a known provider (YouTube, Vimeo, Spotify,
//      SoundCloud, Flickr, TikTok), call the provider's own oEmbed endpoint
//      INSTEAD of fetching the HTML — budget-neutral, not bot-blocked.
//   2. Discovery fallback: after a successful HTML fetch that yields image_only
//      or no_meta, scan the buffered HTML for a <link rel="alternate"
//      type="application/json+oembed"> endpoint and call it if budget allows.
//
// SECURITY (SSRF): every oEmbed URL (registry endpoint, discovered href, and
// the returned thumbnail_url) is validated through the same SSRF gate
// (assertSafeHop / isSafePublicUrl) already used for the primary HTML fetch.
// The import is one-directional: oembed.ts imports pure SSRF helpers from
// og.ts; og.ts imports oEmbed API from this module. No circular dependency.

import { assertSafeHop, isSafePublicUrl, DnsLookupAll } from "./og";

// ---------------------------------------------------------------------------
// Provider registry
// ---------------------------------------------------------------------------

/**
 * Static registry of known oEmbed providers: hostname (lowercase, www-stripped)
 * → endpoint URL template where `{url}` is the placeholder for the
 * encodeURIComponent-encoded page URL.
 *
 * Covers high-frequency media embed providers most likely to appear in Twitter
 * bookmarks. The full oembed.com registry (~400 providers) is out of scope; the
 * discoverOembedLink fallback handles the long tail via <link rel="alternate">.
 */
const REGISTRY: Record<string, string> = {
  "youtube.com":      "https://www.youtube.com/oembed?url={url}&format=json",
  "youtu.be":         "https://www.youtube.com/oembed?url={url}&format=json",
  "vimeo.com":        "https://vimeo.com/api/oembed.json?url={url}",
  // Spotify bookmark URLs always use the open.spotify.com subdomain;
  // register that exact host so the www-strip logic still works.
  "open.spotify.com": "https://open.spotify.com/oembed?url={url}",
  "soundcloud.com":   "https://soundcloud.com/oembed?url={url}&format=json",
  "flickr.com":       "https://www.flickr.com/services/oembed/?url={url}&format=json",
  "tiktok.com":       "https://www.tiktok.com/oembed?url={url}",
};

/**
 * Look up the oEmbed endpoint URL for a given page URL using the static registry.
 * Strips a leading `www.` from the hostname before the lookup so that
 * `www.youtube.com` and `youtube.com` both match the `youtube.com` entry.
 *
 * Returns the fully-formed endpoint URL (with the page URL encoded into the
 * `url` query parameter), or `null` if no registry entry matches.
 */
export function matchProvider(url: string): string | null {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return null;
  }
  const hostname = parsed.hostname.toLowerCase().replace(/^www\./, "");
  const template = REGISTRY[hostname];
  if (!template) return null;
  return template.replace("{url}", encodeURIComponent(url));
}

// ---------------------------------------------------------------------------
// HTML link-rel discovery
// ---------------------------------------------------------------------------

/**
 * Scan HTML for a `<link rel="alternate" type="application/json+oembed">` tag
 * and return its `href` value, or `null` if not found.
 *
 * Tolerates both attribute orderings (type-before-href and href-before-type)
 * and single/double quotes. The regex is intentionally permissive about
 * intervening attributes to handle real-world HTML.
 */
export function discoverOembedLink(html: string): string | null {
  // Pattern A: type attribute before href attribute
  const patternA =
    /<link[^>]+type=["']application\/json\+oembed["'][^>]*href=["']([^"']+)["'][^>]*>/i;
  // Pattern B: href attribute before type attribute
  const patternB =
    /<link[^>]+href=["']([^"']+)["'][^>]*type=["']application\/json\+oembed["'][^>]*>/i;

  const matchA = html.match(patternA);
  if (matchA?.[1]) return matchA[1];

  const matchB = html.match(patternB);
  if (matchB?.[1]) return matchB[1];

  return null;
}

// ---------------------------------------------------------------------------
// oEmbed fetch
// ---------------------------------------------------------------------------

/**
 * Fetch an oEmbed endpoint and return the title and thumbnail image, or null
 * on any failure (SSRF block, non-200, malformed JSON, budget expired).
 *
 * SSRF safety: the endpoint URL is validated by assertSafeHop (DNS-backed)
 * before the fetch. The returned thumbnail_url is validated by isSafePublicUrl
 * (literal check sufficient for a response-body value whose origin hostname was
 * already DNS-checked) — if unsafe, the image is dropped and only the title is
 * returned.
 *
 * Budget: a per-fetch AbortController timeout is set to
 * `min(OEMBED_FETCH_TIMEOUT_MS, deadline - Date.now())`. If the deadline has
 * already expired, returns null immediately.
 *
 * @param endpointUrl  Fully-formed oEmbed endpoint URL (must pass assertSafeHop)
 * @param lookup       Injectable DNS lookup (for testability)
 * @param deadline     Epoch-ms wall-clock deadline for the operation
 */
export async function fetchOembed(
  endpointUrl: string,
  lookup: DnsLookupAll,
  deadline: number,
): Promise<{ title?: string; image?: string } | null> {
  // Budget guard — refuse immediately if no time remains
  const remaining = deadline - Date.now();
  if (remaining <= 0) return null;

  // Parse and SSRF-validate the endpoint URL before any network call
  let parsedEndpoint: URL;
  try {
    parsedEndpoint = new URL(endpointUrl);
  } catch {
    return null;
  }
  try {
    await assertSafeHop(parsedEndpoint, lookup, deadline);
  } catch {
    return null;
  }

  // Clamp per-fetch timeout to remaining budget (never exceed 8 s)
  const OEMBED_FETCH_TIMEOUT_MS = 8_000;
  const timeout = Math.min(OEMBED_FETCH_TIMEOUT_MS, deadline - Date.now());
  if (timeout <= 0) return null;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    // redirect:"error" rejects any 3xx response from the oEmbed endpoint — trusted
    // providers (YouTube, Vimeo, etc.) MUST answer directly; a redirect from their
    // endpoint to a different host bypasses the DNS-based SSRF gate we ran above.
    const resp = await fetch(endpointUrl, {
      headers: { accept: "application/json" },
      redirect: "error",
      signal: controller.signal,
    });
    if (!resp.ok) return null;

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let json: any;
    try {
      json = await resp.json();
    } catch {
      return null;
    }

    const title = typeof json?.title === "string" && json.title.trim()
      ? json.title.trim()
      : undefined;

    // thumbnail_url must pass the literal SSRF check before we store/return it.
    // The endpoint hostname was already DNS-validated above; the thumbnail may be
    // on a different CDN host, so we apply isSafePublicUrl (literal, no DNS) as
    // a last-defence gate — adequate for a value from a trusted oEmbed response.
    let image: string | undefined;
    if (typeof json?.thumbnail_url === "string" && json.thumbnail_url.trim()) {
      const thumb = json.thumbnail_url.trim();
      if (isSafePublicUrl(thumb)) {
        image = thumb;
      }
    }

    if (!title && !image) return null;
    return { title, image };
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}
