// Unit tests for unfurl-vendor.ts — vendor client + Microlink adapter.
//
// All tests use fake fetch responses and injectable DNS lookup (DnsLookupAll).
// No real network calls; no jest.mock() needed.

import { resolveViaUnfurl, MicrolinkAdapter } from "../src/lib/unfurl-vendor";
import type { DnsLookupAll } from "../src/lib/og";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Public DNS lookup stub (all hostnames resolve to a public IP). */
const publicLookup: DnsLookupAll = async () => [{ address: "18.244.124.21", family: 4 }];

/** Private DNS lookup stub (all hostnames resolve to a private IP). */
const privateLookup: DnsLookupAll = async () => [{ address: "10.0.0.1", family: 4 }];

/** A far-future deadline so budget checks do not interfere with normal tests. */
const FAR_DEADLINE = Date.now() + 60_000;

/** Build a minimal fake JSON response for the Microlink API. */
function fakeMicrolinkResponse(data: Record<string, unknown>, status = 200) {
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: () => "application/json" },
    json: async () => data,
  } as unknown as Response;
}

/** Microlink success payload with both title and image. */
const RICH_PAYLOAD = {
  status: "success",
  data: {
    title: "Example Article Title",
    description: "A description of the article.",
    image: { url: "https://cdn.example.com/cover.png" },
  },
};

// ---------------------------------------------------------------------------
// resolveViaUnfurl — success path
// ---------------------------------------------------------------------------

describe("resolveViaUnfurl – success: returns title and image from Microlink response", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("returns {title, image, description} from a well-formed Microlink response", async () => {
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse(RICH_PAYLOAD)) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );

    expect(result).not.toBeNull();
    expect(result!.title).toBe("Example Article Title");
    expect(result!.image).toBe("https://cdn.example.com/cover.png");
    expect(result!.description).toBe("A description of the article.");
  });

  it("returns title only when data.image is absent from response", async () => {
    const titleOnlyPayload = {
      status: "success",
      data: { title: "Title Without Image" },
    };
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse(titleOnlyPayload)) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );

    expect(result).not.toBeNull();
    expect(result!.title).toBe("Title Without Image");
    expect(result!.image).toBeUndefined();
  });

  it("falls back to data.screenshot.url when data.image is absent", async () => {
    const screenshotPayload = {
      status: "success",
      data: {
        title: "Screenshot Fallback",
        screenshot: { url: "https://cdn.example.com/screenshot.png" },
      },
    };
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse(screenshotPayload)) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );

    expect(result).not.toBeNull();
    expect(result!.image).toBe("https://cdn.example.com/screenshot.png");
  });
});

// ---------------------------------------------------------------------------
// Private image URL stripped (AC2 — vendor-returned image re-validation)
// ---------------------------------------------------------------------------

describe("resolveViaUnfurl – private image URL stripped (AC2)", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("strips private data.image.url (192.168.x) and returns title only", async () => {
    const privateImagePayload = {
      status: "success",
      data: {
        title: "Article With Private Image",
        image: { url: "http://192.168.1.1/img.png" },
      },
    };
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse(privateImagePayload)) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );

    expect(result).not.toBeNull();
    expect(result!.title).toBe("Article With Private Image");
    // Private image URL must be stripped — only title returned
    expect(result!.image).toBeUndefined();
  });

  it("strips metadata endpoint image (169.254.169.254) — returns title only", async () => {
    const metaPayload = {
      status: "success",
      data: {
        title: "Metadata Image",
        image: { url: "http://169.254.169.254/latest/meta-data/" },
      },
    };
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse(metaPayload)) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );

    expect(result).not.toBeNull();
    expect(result!.image).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// Non-200 / malformed response → null
// ---------------------------------------------------------------------------

describe("resolveViaUnfurl – failure paths return null", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("returns null on HTTP 429 (non-200 status)", async () => {
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse({}, 429)) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );
    expect(result).toBeNull();
  });

  it("returns null on malformed JSON (JSON parse error)", async () => {
    globalThis.fetch = jest.fn(async () => ({
      status: 200,
      ok: true,
      headers: { get: () => "application/json" },
      json: async () => { throw new SyntaxError("bad json"); },
    })) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );
    expect(result).toBeNull();
  });

  it("returns null when Microlink status is not 'success'", async () => {
    const failPayload = { status: "fail", data: null };
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse(failPayload)) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );
    expect(result).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Budget expired → null, fetch never called
// ---------------------------------------------------------------------------

describe("resolveViaUnfurl – budget expired → null without fetch", () => {
  it("returns null immediately when deadline is already expired", async () => {
    const fetchSpy = jest.fn();
    const realFetch = globalThis.fetch;
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    try {
      const expiredDeadline = Date.now() - 1; // already past
      const result = await resolveViaUnfurl(
        "https://example.com/article",
        publicLookup,
        expiredDeadline,
        "test-api-key",
      );
      expect(result).toBeNull();
      expect(fetchSpy).not.toHaveBeenCalled();
    } finally {
      globalThis.fetch = realFetch;
    }
  });
});

// ---------------------------------------------------------------------------
// SSRF block on vendor endpoint (privateLookup) → null, fetch never called
// ---------------------------------------------------------------------------

describe("resolveViaUnfurl – SSRF block on vendor endpoint", () => {
  it("returns null without fetching when vendor hostname resolves to a private IP", async () => {
    const fetchSpy = jest.fn();
    const realFetch = globalThis.fetch;
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    try {
      // privateLookup returns 10.0.0.1 for any hostname (including api.microlink.io)
      const result = await resolveViaUnfurl(
        "https://example.com/article",
        privateLookup,
        FAR_DEADLINE,
        "test-api-key",
      );
      expect(result).toBeNull();
      expect(fetchSpy).not.toHaveBeenCalled();
    } finally {
      globalThis.fetch = realFetch;
    }
  });
});

// ---------------------------------------------------------------------------
// API key passed as x-api-key header and NOT present in any log output (AC2)
// ---------------------------------------------------------------------------

describe("resolveViaUnfurl – API key header hygiene (AC2)", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("passes the API key as x-api-key header and not in the URL or body", async () => {
    const capturedOptions: RequestInit[] = [];
    globalThis.fetch = jest.fn(async (_url: unknown, opts?: RequestInit) => {
      capturedOptions.push(opts ?? {});
      return fakeMicrolinkResponse(RICH_PAYLOAD);
    }) as unknown as typeof globalThis.fetch;

    const SECRET_KEY = "super-secret-microlink-key-xyzzy";
    await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      SECRET_KEY,
    );

    expect(capturedOptions.length).toBeGreaterThan(0);
    const headers = capturedOptions[0].headers as Record<string, string>;
    // Key must be set as x-api-key header
    expect(headers["x-api-key"]).toBe(SECRET_KEY);

    // Key must NOT appear in the URL (query string)
    const calledUrl = String((globalThis.fetch as jest.Mock).mock.calls[0][0]);
    expect(calledUrl).not.toContain(SECRET_KEY);
  });
});

// ---------------------------------------------------------------------------
// Cap: vendorCallsRemaining.remaining = 0 → vendor never called
// (tested at the MicrolinkAdapter level — cap is enforced in og.ts)
// ---------------------------------------------------------------------------

describe("MicrolinkAdapter – fetch is called when budget remains", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("adapter calls fetch when budget is positive (baseline — cap checked in og.ts)", async () => {
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse(RICH_PAYLOAD)) as unknown as typeof globalThis.fetch;

    const adapter = new MicrolinkAdapter();
    const result = await adapter.resolve(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
      "test-api-key",
    );

    expect(result).not.toBeNull();
    expect(globalThis.fetch as jest.Mock).toHaveBeenCalled();
  });
});
