// Hosted unfurl vendor client — last-resort fallback for residual http_4xx links.
//
// A hosted unfurl service runs headless-render / rotating-proxy backends and can
// retrieve metadata from bot-blocked pages that no direct HTTP fetch can reach.
// This module is the FINAL FALLBACK in the enrichment chain: it is called ONLY
// when a link remains http_4xx after the free direct-fetch + oEmbed path. The
// vendor call is opt-in (controlled by a shared cap counter in the caller) so the
// paid call volume is minimized and a backfill run cannot exhaust a tier budget.
//
// SECURITY: the vendor endpoint is a fixed public hostname validated once via
// assertSafeHop before the fetch (same pattern as oEmbed providers). The user
// URL travels only in the `?url=` query parameter — the vendor's server fetches
// the target page, not our function. Vendor-returned image URLs are re-validated
// through isSafePublicUrl before storage (defence-in-depth against private image
// URLs embedded in vendor responses). The API key is passed only as an HTTP header
// and is NEVER included in any log payload.
//
// Vendor chosen: Microlink (api.microlink.io). Rationale: structured response
// (data.title, data.image.url), 100 req/month free tier, personal-use ToS,
// single fixed endpoint. The VendorClient interface allows swapping to a different
// vendor in one file if needed.

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
    apiKey: string,
  ): Promise<VendorResult | null>;
}

// ---------------------------------------------------------------------------
// Microlink adapter
// ---------------------------------------------------------------------------

const VENDOR_FETCH_TIMEOUT_MS = 8_000;
const MICROLINK_ENDPOINT = "https://api.microlink.io/";

/**
 * Microlink adapter — calls api.microlink.io and maps the JSON response onto
 * VendorResult. The vendor endpoint is SSRF-validated before the fetch; the
 * returned image URL is validated by isSafePublicUrl before it leaves this
 * function (private/metadata URLs are stripped). Returns null on any error.
 */
export class MicrolinkAdapter implements VendorClient {
  async resolve(
    url: string,
    lookup: DnsLookupAll,
    deadline: number,
    apiKey: string,
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
          // API key is passed as a header and MUST NOT appear in any log payload.
          "x-api-key": apiKey,
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
 * Call the configured unfurl vendor for the given URL and return metadata, or
 * null on any error (SSRF block, non-200, malformed JSON, budget expired, cap=0).
 *
 * The API key is passed through the call chain only as a function argument and
 * is used exclusively as an HTTP header inside MicrolinkAdapter — it MUST NOT
 * be logged at any call site.
 *
 * @param url     The original user content URL to enrich (NOT the vendor endpoint)
 * @param lookup  Injectable DNS lookup for SSRF validation (testability)
 * @param deadline Wall-clock epoch-ms budget shared with the enclosing fetch chain
 * @param apiKey  Vendor API key — never log this value
 */
export async function resolveViaUnfurl(
  url: string,
  lookup: DnsLookupAll,
  deadline: number,
  apiKey: string,
): Promise<VendorResult | null> {
  const client = new MicrolinkAdapter();
  return client.resolve(url, lookup, deadline, apiKey);
}
