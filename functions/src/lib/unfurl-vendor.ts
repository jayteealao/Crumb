// Hosted unfurl vendor client — last-resort fallback for residual http_4xx links.
//
// A hosted unfurl service can retrieve metadata from bot-blocked pages that no
// direct HTTP fetch can reach. This module is the FINAL FALLBACK in the
// enrichment chain: it is called ONLY when a link remains http_4xx after the
// free direct-fetch + oEmbed path. The vendor call is opt-in (controlled by a
// shared cap counter in the caller) so call volume is minimized and a backfill
// run cannot exhaust any tier budget.
//
// SECURITY: the vendor endpoint is a fixed hostname validated once via
// assertSafeHop before the fetch (same pattern as oEmbed providers). The user
// URL travels only in the `?url=` query parameter — the vendor's server fetches
// the target page, not our function. Vendor-returned image URLs are re-validated
// through isSafePublicUrl before storage (defence-in-depth against private image
// URLs embedded in vendor responses). The ID token is passed only as an HTTP
// header and is NEVER included in any log payload.
//
// Active vendor: self-hosted iframely (itteco/iframely, MIT-licensed) running as
// a private Cloud Run service in the same GCP project. No external quota, no API
// key secret, no ToS ceiling. The VendorClient interface allows swapping to a
// different vendor by replacing the adapter class in this file.
//
// MicrolinkAdapter is retained as a reference implementation of the VendorClient
// interface but is no longer wired as the active vendor.

import { GoogleAuth, IdTokenClient } from "google-auth-library";
import { assertSafeHop, isSafePublicUrl, DnsLookupAll } from "./og";

// ---------------------------------------------------------------------------
// Vendor-agnostic interface
// ---------------------------------------------------------------------------

/** Minimal vendor metadata result — maps onto the existing OpenGraphData shape. */
export interface VendorResult {
  title?: string;
  image?: string;
  description?: string;
}

/** Vendor-agnostic client interface for testability and future swap-out. */
export interface VendorClient {
  resolve(
    url: string,
    lookup: DnsLookupAll,
    deadline: number,
  ): Promise<VendorResult | null>;
}

// ---------------------------------------------------------------------------
// iframely adapter (active vendor)
// ---------------------------------------------------------------------------

const IFRAMELY_FETCH_TIMEOUT_MS = 15_000; // extended to 15s to absorb Cloud Run cold-start

// Module-level singleton for ~1h OIDC token caching (google-auth-library built-in).
let _authClient: IdTokenClient | null = null;
async function getAuthClient(audience: string): Promise<IdTokenClient> {
  if (!_authClient) {
    _authClient = await new GoogleAuth().getIdTokenClient(audience);
  }
  return _authClient;
}

/**
 * IframelyAdapter — calls a self-hosted iframely Cloud Run service and maps
 * the JSON response onto VendorResult. The service endpoint is SSRF-validated
 * before the fetch; the returned image URL is re-validated by isSafePublicUrl
 * (defence-in-depth). ID-token auth keeps the service private. Returns null
 * on any error (missing env var, SSRF block, non-200, malformed JSON, budget).
 */
export class IframelyAdapter implements VendorClient {
  async resolve(
    url: string,
    lookup: DnsLookupAll,
    deadline: number,
  ): Promise<VendorResult | null> {
    const baseUrl = process.env["IFRAMELY_BASE_URL"];
    if (!baseUrl) return null; // service not yet deployed — silent best-effort

    // Budget guard — refuse immediately if no time remains
    const remaining = deadline - Date.now();
    if (remaining <= 0) return null;

    // Build the iframely endpoint URL. group=true is required for the structured
    // links.thumbnail / links.icon shape; omit_script=1 strips JS embed code.
    let endpointUrl: URL;
    try {
      endpointUrl = new URL("/iframely", baseUrl);
      endpointUrl.searchParams.set("url", url);
      endpointUrl.searchParams.set("group", "true");
      endpointUrl.searchParams.set("omit_script", "1");
    } catch {
      return null;
    }

    // SSRF-validate the iframely service hostname before any network call.
    // The user URL travels only in the query parameter — iframely fetches it.
    try {
      await assertSafeHop(endpointUrl, lookup, deadline);
    } catch {
      return null;
    }

    // Clamp per-fetch timeout to remaining budget (never exceed IFRAMELY_FETCH_TIMEOUT_MS)
    const timeout = Math.min(IFRAMELY_FETCH_TIMEOUT_MS, deadline - Date.now());
    if (timeout <= 0) return null;

    // Mint an OIDC ID token scoped to the iframely service origin. The token is
    // cached ~1h by google-auth-library. MUST NOT appear in any log payload.
    let authHeaders: Record<string, string>;
    try {
      const serviceOrigin = endpointUrl.origin;
      const client = await getAuthClient(serviceOrigin);
      authHeaders = await client.getRequestHeaders() as Record<string, string>;
    } catch {
      return null;
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      // redirect:"error" rejects any 3xx from the iframely service endpoint — the
      // fixed Cloud Run URL should answer directly; a redirect to a different host
      // would bypass the DNS SSRF gate run on endpointUrl above.
      const resp = await fetch(endpointUrl.href, {
        headers: {
          ...authHeaders,
          accept: "application/json",
        },
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

      if (!json || typeof json !== "object") return null;

      const meta = json.meta as Record<string, unknown> | null | undefined;
      const links = json.links as Record<string, unknown> | null | undefined;

      const title =
        typeof meta?.["title"] === "string" && (meta["title"] as string).trim()
          ? (meta["title"] as string).trim()
          : undefined;
      const description =
        typeof meta?.["description"] === "string" && (meta["description"] as string).trim()
          ? (meta["description"] as string).trim()
          : undefined;

      // iframely may return links.thumbnail as a plain object (not an array) for
      // single-thumbnail responses. [].concat() normalises both shapes safely.
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const thumbnails: Array<any> = [].concat((links?.["thumbnail"] as any) ?? []);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const icons: Array<any> = [].concat((links?.["icon"] as any) ?? []);
      const rawImage: string =
        (typeof thumbnails[0]?.href === "string" ? (thumbnails[0].href as string).trim() : "") ||
        (typeof icons[0]?.href === "string" ? (icons[0].href as string).trim() : "");

      // Re-validate returned image URL through isSafePublicUrl — defence-in-depth
      // against private/metadata image URLs embedded in the iframely response.
      let image: string | undefined;
      if (rawImage && isSafePublicUrl(rawImage)) {
        image = rawImage;
      }

      if (!title && !image && !description) return null;
      return { title, image, description };
    } catch {
      return null;
    } finally {
      clearTimeout(timer);
    }
  }
}

// ---------------------------------------------------------------------------
// Microlink adapter (reference implementation — not currently wired)
// ---------------------------------------------------------------------------

const VENDOR_FETCH_TIMEOUT_MS = 8_000;
const MICROLINK_ENDPOINT = "https://api.microlink.io/";

/**
 * Microlink adapter — reference implementation of VendorClient (not wired).
 * Preserved so a future deployment can restore Microlink with minimal diff.
 * The vendor endpoint is SSRF-validated before the fetch; the returned image
 * URL is validated by isSafePublicUrl before it leaves this function.
 */
export class MicrolinkAdapter implements VendorClient {
  async resolve(
    url: string,
    lookup: DnsLookupAll,
    deadline: number,
    _apiKey?: string,
  ): Promise<VendorResult | null> {
    // Budget guard — refuse immediately if no time remains
    const remaining = deadline - Date.now();
    if (remaining <= 0) return null;

    // Build the vendor endpoint URL with the target URL as a query parameter.
    // The user URL must be encoded so it does not interfere with Microlink's
    // own query-string parsing.
    let endpointUrl: URL;
    try {
      endpointUrl = new URL(MICROLINK_ENDPOINT);
      endpointUrl.searchParams.set("url", url);
    } catch {
      return null;
    }

    // SSRF-validate the vendor endpoint hostname before any network call.
    // The user URL travels only in the query parameter — the vendor fetches it.
    try {
      await assertSafeHop(endpointUrl, lookup, deadline);
    } catch {
      return null;
    }

    // Clamp per-fetch timeout to remaining budget (never exceed VENDOR_FETCH_TIMEOUT_MS)
    const timeout = Math.min(VENDOR_FETCH_TIMEOUT_MS, deadline - Date.now());
    if (timeout <= 0) return null;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      // redirect:"error" rejects any 3xx from the vendor API endpoint — the fixed
      // Microlink endpoint (api.microlink.io) should answer directly; a redirect to a
      // different host would bypass the DNS SSRF gate run on endpointUrl above.
      const resp = await fetch(endpointUrl.href, {
        headers: {
          accept: "application/json",
        },
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

      // Microlink response shape: { status: "success", data: { title, image: { url }, description } }
      if (!json || json.status !== "success" || !json.data) return null;
      const data = json.data as Record<string, unknown>;

      const title =
        typeof data["title"] === "string" && (data["title"] as string).trim()
          ? (data["title"] as string).trim()
          : undefined;
      const description =
        typeof data["description"] === "string" && (data["description"] as string).trim()
          ? (data["description"] as string).trim()
          : undefined;

      // Extract image URL from data.image.url or data.screenshot.url as fallback.
      // Re-validate through isSafePublicUrl before returning — defence-in-depth
      // against private/metadata image URLs embedded in the vendor response.
      let image: string | undefined;
      const imgObj = data["image"] as Record<string, unknown> | null | undefined;
      const screenshotObj = data["screenshot"] as Record<string, unknown> | null | undefined;
      const rawImage =
        (typeof imgObj?.["url"] === "string" ? (imgObj["url"] as string).trim() : "") ||
        (typeof screenshotObj?.["url"] === "string" ? (screenshotObj["url"] as string).trim() : "");
      if (rawImage && isSafePublicUrl(rawImage)) {
        image = rawImage;
      }

      if (!title && !image && !description) return null;
      return { title, image, description };
    } catch {
      return null;
    } finally {
      clearTimeout(timer);
    }
  }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Call the configured unfurl vendor (iframely self-hosted) for the given URL
 * and return metadata, or null on any error (missing env var, SSRF block,
 * non-200, malformed JSON, budget expired).
 *
 * The ID token used for Cloud Run auth is fetched internally by IframelyAdapter
 * and MUST NOT be logged at any call site.
 *
 * @param url     The original user content URL to enrich (NOT the vendor endpoint)
 * @param lookup  Injectable DNS lookup for SSRF validation (testability)
 * @param deadline Wall-clock epoch-ms budget shared with the enclosing fetch chain
 */
export async function resolveViaUnfurl(
  url: string,
  lookup: DnsLookupAll,
  deadline: number,
): Promise<VendorResult | null> {
  const client = new IframelyAdapter();
  return client.resolve(url, lookup, deadline);
}
