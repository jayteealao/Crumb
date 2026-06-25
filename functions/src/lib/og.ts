// Server-side OpenGraph fetch for link-preview enrichment.
//
// X v2's own `entities.urls[].{title,description,images}` enrichment fields are
// Enterprise/Gnip-gated and unreliable on Basic/Pro, so preview richness comes
// from US fetching the destination page's `og:`/`twitter:` meta tags. This is a
// best-effort call: ANY failure (timeout, non-2xx, unparseable, blocked host)
// resolves to `{}`, and the caller still writes the URL-only row so a link
// always renders a preview (a themed URL chip when metadata is absent).
//
// SECURITY (SSRF): this fetches arbitrary user-content URLs. [isSafePublicUrl]
// rejects non-http(s) schemes and private/loopback/link-local/metadata hosts
// before the fetch. HTTP 3xx redirects are blocked via `redirect: "error"` in
// fetchOptions so a public URL that 301-redirects to an internal/metadata
// address will abort rather than follow.

import ogs from "open-graph-scraper";

export interface OpenGraphData {
  title?: string;
  description?: string;
  image?: string;
}

/**
 * Outcome tag for per-reason enrichment observability.
 * - `rich`            — both title and image resolved
 * - `title_only`      — title resolved, no image
 * - `image_only`      — image resolved, no title
 * - `no_meta`         — fetch succeeded but no title or image found (inherent static ceiling)
 * - `redirect_blocked`— fetch aborted because the URL issued a redirect (SSRF guard)
 * - `http_4xx`        — server returned 4xx (bot-block, gone, etc.)
 * - `non_html`        — content-type is not HTML
 * - `timeout`         — fetch timed out
 * - `unsafe_url`      — SSRF guard rejected the URL before fetch
 * - `error`           — any other failure
 */
export type OgOutcomeTag =
  | "rich"
  | "title_only"
  | "image_only"
  | "no_meta"
  | "redirect_blocked"
  | "http_4xx"
  | "non_html"
  | "timeout"
  | "unsafe_url"
  | "error";

export interface OpenGraphResult extends OpenGraphData {
  /** Structured outcome for logging. Always present. */
  outcome: OgOutcomeTag;
}

const FETCH_TIMEOUT_SECONDS = 5;
// A realistic browser UA reduces HTTP 403 bot-blocks from sites that filter on
// the user-agent string (measured at ~13% of fetch failures in the RCA sample).
// Pinned to a frozen Chrome/131 string so it stays predictable across deploys.
const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
// Cap the bytes we read per page. OpenGraph/Twitter-card meta tags live in
// <head>, near the top of the document, so 512 KB is ample. Without this bound,
// open-graph-scraper buffers the FULL response body — a handful of multi-MB
// pages fetched concurrently exhausts the function heap (observed: 512 MiB OOM
// during the bulk link backfill). Streaming + a hard byte cap keeps peak memory
// bounded regardless of page size. (SUP-4 / PERF-5.)
const MAX_HTML_BYTES = 512 * 1024;

/**
 * Reject non-http(s) URLs and hosts that resolve to private / loopback /
 * link-local / cloud-metadata space. A literal-IP host in a private range, or
 * an obvious internal name (localhost, *.local, *.internal), is dropped before
 * any network call. Best-effort: hostnames that only resolve to private IPs via
 * DNS are NOT caught here (see the redirect-SSRF note above).
 */
export function isSafePublicUrl(rawUrl: string): boolean {
  let parsed: URL;
  try {
    parsed = new URL(rawUrl);
  } catch {
    return false;
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") return false;

  const host = parsed.hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".localhost")) return false;
  if (host.endsWith(".local") || host.endsWith(".internal")) return false;

  // IPv6 loopback / unspecified.
  if (host === "::1" || host === "[::1]" || host === "::" || host === "[::]") return false;

  // IPv4 literal in a private / loopback / link-local range.
  const ipv4 = host.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4) {
    const [a, b] = [Number(ipv4[1]), Number(ipv4[2])];
    if (a === 10) return false; // 10.0.0.0/8
    if (a === 127) return false; // loopback
    if (a === 0) return false; // 0.0.0.0/8
    if (a === 169 && b === 254) return false; // link-local + 169.254.169.254 metadata
    if (a === 172 && b >= 16 && b <= 31) return false; // 172.16.0.0/12
    if (a === 192 && b === 168) return false; // 192.168.0.0/16
  }
  return true;
}

/**
 * Fetch OpenGraph / Twitter-card metadata for [url]. Always resolves (never
 * rejects) — on any failure the returned object has `outcome` set to the
 * failure reason and no `title`/`image`. The caller still writes the URL-only
 * row; the `outcome` tag is used for structured observability logging so the
 * enrichment rate breakdown is visible in Cloud Logging.
 *
 * Source priority for `title`:  og:title → twitter:title → dc.title → JSON-LD name/headline
 * Source priority for `image`:  og:image → twitter:image → JSON-LD image
 * Source for `description`:     og:description → twitter:description
 */
export async function fetchOpenGraph(url: string): Promise<OpenGraphResult> {
  if (!isSafePublicUrl(url)) return { outcome: "unsafe_url" };
  try {
    const fetched = await fetchHtmlCapped(url);
    if (!fetched.html) {
      const failOutcome = fetched.outcome === "ok" ? "error" : fetched.outcome;
      return { outcome: failOutcome };
    }
    // Parse the already-fetched (size-bounded) HTML — `html` makes ogs skip its
    // own unbounded request. `url` is passed only as the base for relative tags.
    const { error, result } = await ogs({ html: fetched.html, url });
    if (error || !result) return { outcome: "error" };
    // ogs v6.11 result shape after extract → mediaSetup → fallback:
    //   ogTitle      — og:title meta, or <title>/<h1> fallback (populated when og:title is absent)
    //   twitterTitle — twitter:title meta
    //   dcTitle      — dc.title Dublin Core meta (often set when og:title is absent)
    //   ogDescription / twitterDescription — description meta
    //   ogImage[]    — assembled from og:image / og:image:secure_url / og:image:url, then <img> fallback
    //   twitterImage[] — assembled from twitter:image
    //   jsonLD[]     — parsed <script type="application/ld+json"> blocks; may carry name/headline/image
    const r = result as {
      ogTitle?: string;
      twitterTitle?: string;
      dcTitle?: string;
      ogDescription?: string;
      twitterDescription?: string;
      ogImage?: Array<{ url?: string }>;
      twitterImage?: Array<{ url?: string }>;
      jsonLD?: Array<Record<string, unknown>>;
    };
    // Pull title from structured fields first (og/twitter), then Dublin Core, then the first
    // JSON-LD block that advertises `name` or `headline` (WebPage, Article, etc.).
    const jsonLdTitle = r.jsonLD?.reduce<string | undefined>((found, ld) => {
      if (found) return found;
      const v = ld["name"] ?? ld["headline"];
      return typeof v === "string" && v.trim() ? v.trim() : undefined;
    }, undefined);
    // Pull image from JSON-LD `image` property when og/twitter images are absent.
    const jsonLdImage = r.jsonLD?.reduce<string | undefined>((found, ld) => {
      if (found) return found;
      const img = ld["image"];
      if (typeof img === "string" && img.trim()) return img.trim();
      if (Array.isArray(img) && typeof img[0] === "string") return img[0];
      if (img && typeof img === "object" && !Array.isArray(img)) {
        const imgUrl = (img as Record<string, unknown>)["url"];
        if (typeof imgUrl === "string" && imgUrl.trim()) return imgUrl.trim();
      }
      return undefined;
    }, undefined);
    const title = r.ogTitle ?? r.twitterTitle ?? r.dcTitle ?? jsonLdTitle;
    const description = r.ogDescription ?? r.twitterDescription;
    const image = r.ogImage?.[0]?.url ?? r.twitterImage?.[0]?.url ?? jsonLdImage;
    const out: OpenGraphResult = {
      outcome: title && image ? "rich" : title ? "title_only" : image ? "image_only" : "no_meta",
    };
    if (title) out.title = title;
    if (description) out.description = description;
    if (image) out.image = image;
    return out;
  } catch {
    return { outcome: "error" };
  }
}

/**
 * Internal tagged fetch result. When `html` is non-null the fetch succeeded
 * and the caller determines the final `OgOutcomeTag` from parsed metadata.
 * When `html` is null, `outcome` names the failure reason.
 */
interface FetchResult {
  html: string | null;
  outcome: Exclude<OgOutcomeTag, "rich" | "title_only" | "image_only" | "no_meta"> | "ok";
}

/**
 * Fetch a page's HTML, streaming the body and stopping after [MAX_HTML_BYTES].
 * Returns a tagged result — `html` is non-null only on success; `outcome` names
 * the failure reason on any non-HTML, non-2xx, redirect, or timeout so callers
 * can emit structured per-reason logs. `redirect: "error"` preserves the SSRF
 * guard (a public URL that 3xx-redirects to an internal/metadata host aborts
 * rather than following). The byte cap keeps peak memory bounded regardless of
 * page size. (SUP-4 / PERF-5.)
 */
async function fetchHtmlCapped(url: string): Promise<FetchResult> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_SECONDS * 1000);
  try {
    const resp = await fetch(url, {
      headers: {
        "user-agent": USER_AGENT,
        accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "accept-language": "en-US,en;q=0.9",
      },
      redirect: "error",
      signal: controller.signal,
    });
    if (resp.status >= 400 && resp.status < 500) {
      return { html: null, outcome: "http_4xx" };
    }
    if (!resp.ok || !resp.body) return { html: null, outcome: "error" };
    const contentType = (resp.headers.get("content-type") ?? "").toLowerCase();
    if (!contentType.includes("html")) return { html: null, outcome: "non_html" };
    const reader = resp.body.getReader();
    const chunks: Uint8Array[] = [];
    let total = 0;
    // Read the body to a graceful end, but RETAIN only the first MAX_HTML_BYTES;
    // excess is drained and dropped so peak memory stays bounded regardless of
    // page size. We deliberately do NOT call reader.cancel() to stop early: a
    // mid-stream cancel leaves undici's HTTP/1 parser paused and trips an internal
    // assertion (assert(!this.paused) in Parser.finish), crashing the instance.
    // Draining to completion keeps the parser happy; the 5 s abort timeout caps
    // pathologically large/slow responses.
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value && total < MAX_HTML_BYTES) {
        const take = Math.min(value.length, MAX_HTML_BYTES - total);
        chunks.push(take === value.length ? value : value.subarray(0, take));
        total += take;
      }
    }
    const html = Buffer.concat(chunks.map((c) => Buffer.from(c))).toString("utf8");
    return { html, outcome: "ok" }; // caller determines the real outcome after parsing
  } catch (err) {
    const isAbort = err instanceof Error && err.name === "AbortError";
    const isRedirect =
      err instanceof Error &&
      (err.message.includes("redirect") || err.message.includes("3") || err.name === "TypeError");
    if (isAbort) return { html: null, outcome: "timeout" };
    // fetch() with redirect:"error" throws a TypeError when a redirect is encountered.
    if (isRedirect) return { html: null, outcome: "redirect_blocked" };
    return { html: null, outcome: "error" };
  } finally {
    clearTimeout(timer);
  }
}
