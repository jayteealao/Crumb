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

const FETCH_TIMEOUT_SECONDS = 5;
const USER_AGENT = "CrumbsLinkPreviewBot/1.0 (+https://graphitenerd.xyz)";
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
 * Fetch OpenGraph / Twitter-card metadata for [url]. Resolves to `{}` on any
 * failure (best-effort). Falls back from `og:` to `twitter:` tags for each
 * field; the image is the first usable card image URL (stored as-is — Coil
 * loads it on-device, no re-hosting).
 */
export async function fetchOpenGraph(url: string): Promise<OpenGraphData> {
  if (!isSafePublicUrl(url)) return {};
  try {
    const html = await fetchHtmlCapped(url);
    if (!html) return {};
    // Parse the already-fetched (size-bounded) HTML — `html` makes ogs skip its
    // own unbounded request. `url` is passed only as the base for relative tags.
    const { error, result } = await ogs({ html, url });
    if (error || !result) return {};
    const r = result as {
      ogTitle?: string;
      twitterTitle?: string;
      ogDescription?: string;
      twitterDescription?: string;
      ogImage?: Array<{ url?: string }>;
      twitterImage?: Array<{ url?: string }>;
    };
    const title = r.ogTitle ?? r.twitterTitle;
    const description = r.ogDescription ?? r.twitterDescription;
    const image = r.ogImage?.[0]?.url ?? r.twitterImage?.[0]?.url;
    const out: OpenGraphData = {};
    if (title) out.title = title;
    if (description) out.description = description;
    if (image) out.image = image;
    return out;
  } catch {
    return {};
  }
}

/**
 * Fetch a page's HTML, streaming the body and stopping after [MAX_HTML_BYTES].
 * Returns `null` on any non-HTML, non-2xx, redirect, or timeout — the caller
 * treats that as "no preview". `redirect: "error"` preserves the SSRF guard
 * (a public URL that 3xx-redirects to an internal/metadata host aborts rather
 * than following). The byte cap is the memory fix: we never buffer more than
 * ~512 KB per page regardless of Content-Length.
 */
async function fetchHtmlCapped(url: string): Promise<string | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_SECONDS * 1000);
  try {
    const resp = await fetch(url, {
      headers: { "user-agent": USER_AGENT, accept: "text/html,application/xhtml+xml" },
      redirect: "error",
      signal: controller.signal,
    });
    if (!resp.ok || !resp.body) return null;
    const contentType = (resp.headers.get("content-type") ?? "").toLowerCase();
    if (!contentType.includes("html")) return null; // skip images/pdf/binary
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
    return Buffer.concat(chunks.map((c) => Buffer.from(c))).toString("utf8");
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}
