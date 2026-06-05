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
    const { error, result } = await ogs({
      url,
      timeout: FETCH_TIMEOUT_SECONDS,
      fetchOptions: { headers: { "user-agent": USER_AGENT }, redirect: "error" },
    });
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
