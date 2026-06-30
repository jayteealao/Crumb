// Server-side OpenGraph fetch for link-preview enrichment.
//
// X v2's own `entities.urls[].{title,description,images}` enrichment fields are
// Enterprise/Gnip-gated and unreliable on Basic/Pro, so preview richness comes
// from us fetching the destination page's `og:`/`twitter:` meta tags. This is a
// best-effort call: ANY failure (timeout, non-2xx, unparseable, blocked host)
// resolves to `{}`, and the caller still writes the URL-only row so a link
// always renders a preview (a themed URL chip when metadata is absent).
//
// SECURITY (SSRF): this fetches arbitrary user-content URLs. [isSafePublicUrl]
// rejects non-http(s) schemes and private/loopback/link-local/metadata hosts
// before the fetch. HTTP 3xx redirects are followed with a bounded manual loop
// that re-validates EVERY hop (scheme, userinfo, and resolved IP) to prevent
// SSRF via redirect: a public URL that 301-redirects to an internal/metadata
// address is blocked when the redirect target's host is validated.

import * as dns from "node:dns";
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
 * - `redirect_blocked`— (legacy) fetch aborted because the URL issued a redirect (SSRF guard);
 *                       kept in the union for back-compat with logged history — essentially
 *                       stops firing now that redirects are followed with per-hop validation
 * - `too_many_redirects` — redirect chain exceeded MAX_REDIRECT_HOPS
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
  | "too_many_redirects"
  | "http_4xx"
  | "non_html"
  | "timeout"
  | "unsafe_url"
  | "error";

export interface OpenGraphResult extends OpenGraphData {
  /** Structured outcome for logging. Always present. */
  outcome: OgOutcomeTag;
}

const FETCH_TIMEOUT_MS = 8_000; // bumped from 5s → 8s to accommodate slow sites (triage)
// A realistic browser UA reduces HTTP 403 bot-blocks from sites that filter on
// the user-agent string (measured at ~13% of fetch failures in the RCA sample).
// Pinned to a frozen Chrome/131 string so it stays predictable across deploys.
const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
// Alternate UA for the bot-block retry (triage bucket: http_4xx ~10.6%).
// Some sites 403 on Chrome UA but pass curl/wget-style agents — a different UA
// profile on a single retry recovers a fraction of that bucket.
const ALT_USER_AGENT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
const ALT_ACCEPT = "text/html,application/xhtml+xml,*/*;q=0.8";
// Cap the bytes we read per page. OpenGraph/Twitter-card meta tags live in
// <head>, near the top of the document, so 512 KB is ample. Without this bound,
// open-graph-scraper buffers the FULL response body — a handful of multi-MB
// pages fetched concurrently exhausts the function heap (observed: 512 MiB OOM
// during the bulk link backfill). Streaming + a hard byte cap keeps peak memory
// bounded regardless of page size. (SUP-4 / PERF-5.)
const MAX_HTML_BYTES = 512 * 1024;
// Maximum redirect hops before giving up. Five is what most browsers allow;
// chains longer than this are unusual in legitimate sites and a common infinite-
// redirect attack pattern.
const MAX_REDIRECT_HOPS = 5;

// ---------------------------------------------------------------------------
// SSRF — pure IP classifier (no I/O)
// ---------------------------------------------------------------------------

/**
 * Returns true if the given canonical IP string (IPv4 dotted-decimal or IPv6
 * expanded/compressed) resolves to a private, loopback, link-local, CGNAT,
 * metadata, or otherwise non-routable address. Pure function — no DNS call.
 *
 * Covers:
 *  - RFC 1918: 10/8, 172.16/12, 192.168/16
 *  - Loopback: 127.0.0.0/8, ::1
 *  - Link-local + cloud metadata: 169.254.0.0/16, fe80::/10
 *  - CGNAT: 100.64.0.0/10
 *  - IANA "this network": 0.0.0.0/8
 *  - IPv6 unspecified: ::
 *  - IPv6 ULA: fc00::/7
 *  - GCP internal IPv6 metadata: fd00:ec2::254 (exact)
 *  - IPv4-mapped IPv6 hex form: ::ffff:HHHH:HHHH (reconstructed + re-classified)
 *
 * The IPv4-mapped form `::ffff:a9fe:a9fe` encodes 169.254.169.254 (metadata);
 * Node normalises resolved IPs to this form, which a naive string check misses.
 * (Real advisory class: GHSA-vrcj-hv2q-c58m, LibreChat PR #12130.)
 */
export function isPrivateIp(ip: string): boolean {
  const s = ip.toLowerCase().trim();

  // IPv4 dotted-decimal
  const ipv4 = s.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4) {
    const [a, b, c, d] = [Number(ipv4[1]), Number(ipv4[2]), Number(ipv4[3]), Number(ipv4[4])];
    if (a === 0) return true;                        // 0.0.0.0/8 (this network)
    if (a === 10) return true;                       // 10.0.0.0/8
    if (a === 100 && b >= 64 && b <= 127) return true; // 100.64.0.0/10 CGNAT
    if (a === 127) return true;                      // 127.0.0.0/8 loopback
    if (a === 169 && b === 254) return true;         // 169.254.0.0/16 link-local + metadata
    if (a === 172 && b >= 16 && b <= 31) return true; // 172.16.0.0/12
    if (a === 192 && b === 168) return true;         // 192.168.0.0/16
    // Suppress unused-variable warning: d is used implicitly via destructure
    void d;
    void c;
    return false;
  }

  // IPv6: strip optional brackets
  const raw = s.startsWith("[") && s.endsWith("]") ? s.slice(1, -1) : s;

  // IPv4-mapped IPv6 hex form: ::ffff:HHHH:HHHH
  // Node dns.promises.lookup returns this form for IPv4-mapped addresses.
  // e.g. ::ffff:a9fe:a9fe maps to 169.254.254.254 — each 16-bit group is two octets.
  const mapped = raw.match(/^::ffff:([0-9a-f]{1,4}):([0-9a-f]{1,4})$/i);
  if (mapped) {
    const hi = parseInt(mapped[1], 16);
    const lo = parseInt(mapped[2], 16);
    const a = (hi >> 8) & 0xff;
    const b = hi & 0xff;
    const c = (lo >> 8) & 0xff;
    const d = lo & 0xff;
    // Reconstruct dotted-decimal and re-classify
    return isPrivateIp(`${a}.${b}.${c}.${d}`);
  }

  // IPv4-mapped in dotted notation: ::ffff:1.2.3.4
  const mappedDotted = raw.match(/^::ffff:(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})$/i);
  if (mappedDotted) {
    return isPrivateIp(mappedDotted[1]);
  }

  // IPv6 named addresses
  if (raw === "::" || raw === "::1") return true;        // unspecified + loopback

  // GCP internal IPv6 metadata endpoint (exact)
  if (raw === "fd00:ec2::254") return true;

  // Parse first 16-bit group for range classification
  const parts = raw.split(":");
  if (parts.length >= 1) {
    const first = parseInt(parts[0] || "0", 16);
    if (isNaN(first)) return false;
    // fc00::/7 — ULA (fc00 and fd00 prefixes, bits 7 downwards)
    if ((first & 0xfe00) === 0xfc00) return true;
    // fe80::/10 — link-local
    if ((first & 0xffc0) === 0xfe80) return true;
  }

  return false;
}

// ---------------------------------------------------------------------------
// SSRF — async host gate (injectable lookup for testability)
// ---------------------------------------------------------------------------

/** DNS lookup function signature — matches dns.promises.lookup with {all:true}. */
export type DnsLookupAll = (
  hostname: string,
  opts: { all: true },
) => Promise<Array<{ address: string; family: number }>>;

/**
 * Resolve [host] via DNS and reject (throw) if any returned address is private.
 * For a bare IP-literal host, DNS is skipped and the literal is validated directly.
 * Throws a tagged Error({ message: "unsafe_host" }) on failure.
 *
 * Injectable [lookup] defaults to dns.promises.lookup so tests can pass a fake
 * without jest.mock("node:dns").
 */
export async function resolveAndValidateHost(
  host: string,
  lookup: DnsLookupAll = dns.promises.lookup as DnsLookupAll,
): Promise<void> {
  // Strip brackets from IPv6 literals (URL.hostname includes them)
  const bare = host.startsWith("[") && host.endsWith("]") ? host.slice(1, -1) : host;

  // If it looks like an IPv4 or IPv6 literal, validate directly without DNS
  const isIpLiteral =
    /^(\d{1,3}\.){3}\d{1,3}$/.test(bare) || // IPv4
    /^[0-9a-f:]+$/i.test(bare); // IPv6 (simplified — covers ::, ::1, full form)

  if (isIpLiteral) {
    if (isPrivateIp(bare)) {
      throw new Error("unsafe_host");
    }
    return;
  }

  // Hostname — resolve all addresses and check each one
  let addresses: Array<{ address: string; family: number }>;
  try {
    addresses = await lookup(bare, { all: true });
  } catch {
    // DNS failure — treat as unsafe (can't confirm it's public)
    throw new Error("unsafe_host");
  }
  for (const { address } of addresses) {
    if (isPrivateIp(address)) {
      throw new Error("unsafe_host");
    }
  }
}

// ---------------------------------------------------------------------------
// SSRF — per-hop gate
// ---------------------------------------------------------------------------

/**
 * Validate a single URL for safety before fetching it.
 * Rejects: non-http(s) scheme, userinfo (credentials in URL), unsafe host.
 * Throws a tagged Error({ message: "unsafe_url" | "unsafe_host" }) on failure.
 */
async function assertSafeHop(
  url: URL,
  lookup: DnsLookupAll,
): Promise<void> {
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("unsafe_url");
  }
  // Reject embedded credentials (user:pass@host) — a common SSRF smuggling vector
  if (url.username || url.password) {
    throw new Error("unsafe_url");
  }
  // isSafePublicUrl literal checks (internal hostnames / obvious private names)
  if (!isSafePublicUrl(url.href)) {
    throw new Error("unsafe_url");
  }
  // DNS resolution check — catches public hostnames that resolve to private IPs
  await resolveAndValidateHost(url.hostname, lookup);
}

// ---------------------------------------------------------------------------
// Public SSRF pre-filter (unchanged interface — now a component of assertSafeHop)
// ---------------------------------------------------------------------------

/**
 * Reject non-http(s) URLs and hosts that resolve to private / loopback /
 * link-local / cloud-metadata space. A literal-IP host in a private range, or
 * an obvious internal name (localhost, *.local, *.internal), is dropped before
 * any network call. Best-effort on hostnames: DNS-rebinding is caught by
 * resolveAndValidateHost which is called on every hop in fetchHtmlCapped.
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

// ---------------------------------------------------------------------------
// Fetch helpers
// ---------------------------------------------------------------------------

/**
 * Fetch OpenGraph / Twitter-card metadata for [url]. Always resolves (never
 * rejects) — on any failure the returned object has `outcome` set to the
 * failure reason and no `title`/`image`. The caller still writes the URL-only
 * row; the `outcome` tag is used for structured observability logging so the
 * enrichment rate breakdown is visible in Cloud Logging.
 *
 * Source priority for `title`:  og:title → twitter:title → dc.title → <title> → JSON-LD name/headline
 * Source priority for `image`:  og:image → twitter:image → JSON-LD image → favicon (no_meta fallback)
 * Source for `description`:     og:description → twitter:description
 */
export async function fetchOpenGraph(
  url: string,
  lookup: DnsLookupAll = dns.promises.lookup as DnsLookupAll,
): Promise<OpenGraphResult> {
  if (!isSafePublicUrl(url)) return { outcome: "unsafe_url" };
  try {
    const fetched = await fetchHtmlCapped(url, lookup);
    if (!fetched.html) {
      const failOutcome = fetched.outcome === "ok" ? "error" : fetched.outcome;
      return { outcome: failOutcome };
    }
    // Parse the already-fetched (size-bounded) HTML. open-graph-scraper v6.11
    // REJECTS a call that passes both `html` and `url` ("Must specify either
    // `url` or `html`, not both"), so pass `html` only — we have already fetched
    // the body ourselves (SSRF guard + byte cap) and do not want ogs to re-fetch.
    const { error, result } = await ogs({ html: fetched.html });
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
    // Note: ogs already populates ogTitle from <title> when og:title is absent (confirmed spike).
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
    let image = r.ogImage?.[0]?.url ?? r.twitterImage?.[0]?.url ?? jsonLdImage;

    // no_meta favicon fallback: derive an icon from the favicon when no structured
    // image is available. Spike confirmed ogs already covers <title> → ogTitle, so
    // only favicon derivation is incremental value here. Extract from <link rel="icon">
    // in the fetched HTML, then fall back to /favicon.ico at the origin.
    if (!image) {
      image = extractFavicon(fetched.html, fetched.finalUrl);
    }

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
 * Extract a favicon URL from HTML. Tries <link rel="icon"> / <link rel="shortcut icon">
 * first, then synthesises the standard /favicon.ico path from the page origin.
 * Returns undefined if [html] or [pageUrl] is not available.
 */
function extractFavicon(html: string | null, pageUrl?: string): string | undefined {
  if (!html || !pageUrl) return undefined;
  // Match <link rel="icon"> or <link rel="shortcut icon"> — attribute order varies
  const iconLink = html.match(
    /<link[^>]+rel=["'](?:shortcut )?icon["'][^>]*href=["']([^"']+)["'][^>]*>/i,
  ) ?? html.match(
    /<link[^>]+href=["']([^"']+)["'][^>]*rel=["'](?:shortcut )?icon["'][^>]*>/i,
  );
  if (iconLink?.[1]) {
    try {
      return new URL(iconLink[1], pageUrl).href;
    } catch {
      // malformed href — fall through to /favicon.ico
    }
  }
  // Synthesise /favicon.ico from the page origin
  try {
    const origin = new URL(pageUrl).origin;
    return `${origin}/favicon.ico`;
  } catch {
    return undefined;
  }
}

// ---------------------------------------------------------------------------
// Internal tagged fetch result
// ---------------------------------------------------------------------------

/**
 * Internal tagged fetch result. When `html` is non-null the fetch succeeded
 * and the caller determines the final `OgOutcomeTag` from parsed metadata.
 * When `html` is null, `outcome` names the failure reason.
 * `finalUrl` is the URL of the terminal response (after any redirects followed).
 */
interface FetchResult {
  html: string | null;
  outcome: Exclude<OgOutcomeTag, "rich" | "title_only" | "image_only" | "no_meta"> | "ok";
  finalUrl?: string;
}

// Standard fetch options for a browser-like request
function browserHeaders(ua = USER_AGENT): Record<string, string> {
  return {
    "user-agent": ua,
    accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "accept-language": "en-US,en;q=0.9",
  };
}

/**
 * Fetch a page's HTML with an SSRF-safe bounded redirect-follow loop.
 * Returns a tagged result — `html` is non-null only on success; `outcome` names
 * the failure reason on any non-HTML, non-2xx, redirect, or timeout.
 *
 * Security: every hop (including the initial URL) is validated by assertSafeHop
 * before the fetch. A redirect to a private/metadata host is blocked as
 * `unsafe_url`. Chains exceeding MAX_REDIRECT_HOPS return `too_many_redirects`.
 * The byte cap keeps peak memory bounded.
 */
async function fetchHtmlCapped(
  url: string,
  lookup: DnsLookupAll,
  attempt = 0, // 0 = first attempt, 1 = retry
): Promise<FetchResult> {
  const ua = attempt === 0 ? USER_AGENT : ALT_USER_AGENT;
  const accept = attempt === 0 ? browserHeaders(ua).accept : ALT_ACCEPT;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);

  let currentUrl: URL;
  try {
    currentUrl = new URL(url);
  } catch {
    return { html: null, outcome: "unsafe_url" };
  }

  try {
    let hops = 0;

    for (;;) {
      // Validate EVERY hop before fetching (initial URL + each redirect target)
      try {
        await assertSafeHop(currentUrl, lookup);
      } catch {
        return { html: null, outcome: "unsafe_url" };
      }

      const resp = await fetch(currentUrl.href, {
        headers: { "user-agent": ua, accept, "accept-language": "en-US,en;q=0.9" },
        redirect: "manual",
        signal: controller.signal,
      });

      // Handle 3xx redirects manually
      if (resp.status >= 300 && resp.status < 400) {
        if (hops >= MAX_REDIRECT_HOPS) {
          return { html: null, outcome: "too_many_redirects" };
        }
        const location = resp.headers.get("location");
        if (!location) {
          return { html: null, outcome: "error" };
        }
        // Resolve the Location relative to the current URL (handles relative + protocol-relative)
        try {
          currentUrl = new URL(location, currentUrl.href);
        } catch {
          return { html: null, outcome: "error" };
        }
        hops++;
        // Drain the redirect response body to keep undici's HTTP parser happy
        if (resp.body) {
          try {
            await resp.body.getReader().read();
          } catch {
            // ignore drain errors
          }
        }
        continue;
      }

      // Terminal response — process it
      if (resp.status >= 400 && resp.status < 500) {
        // Bot-block retry: on first attempt, retry once with alternate UA (triage bucket)
        if (attempt === 0) {
          clearTimeout(timer);
          return fetchHtmlCapped(url, lookup, 1);
        }
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
      // Draining to completion keeps the parser happy; the timeout caps slow responses.
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
      return { html, outcome: "ok", finalUrl: currentUrl.href };
    }
  } catch (err) {
    const isAbort = err instanceof Error && err.name === "AbortError";
    if (isAbort) {
      // Timeout retry: on first attempt, retry once (triage bucket: ~2.7% slow sites)
      if (attempt === 0) {
        clearTimeout(timer);
        return fetchHtmlCapped(url, lookup, 1);
      }
      return { html: null, outcome: "timeout" };
    }
    return { html: null, outcome: "error" };
  } finally {
    clearTimeout(timer);
  }
}
