// Unit tests for og.ts — SSRF guard, redirect-follow, and triage buckets.
//
// Tests are fully deterministic via fake fetch + injected lookup. No real
// network calls; no jest.mock("node:dns") needed (lookup is injectable).

import { fetchOpenGraph, isSafePublicUrl, isPrivateIp, resolveAndValidateHost } from "../src/lib/og";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Build a minimal fake Response for use in globalThis.fetch stubs. */
function fakeHtmlResponse(html: string, status = 200) {
  const bytes = new TextEncoder().encode(html);
  let sent = false;
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: {
      get: (k: string) =>
        k.toLowerCase() === "content-type" ? "text/html; charset=utf-8" : null,
    },
    body: {
      getReader: () => ({
        read: async () =>
          sent ? { done: true, value: undefined } : ((sent = true), { done: false, value: bytes }),
      }),
    },
  };
}

/** Build a fake 3xx redirect Response with a Location header. */
function fakeRedirectResponse(location: string, status = 302) {
  return {
    status,
    ok: false,
    headers: {
      get: (k: string) => (k.toLowerCase() === "location" ? location : null),
    },
    body: {
      getReader: () => ({
        read: async () => ({ done: true, value: undefined }),
      }),
    },
  };
}

/** A fake lookup that always returns a public IP. */
const publicLookup = async () => [{ address: "93.184.216.34", family: 4 }];

/** A fake lookup that returns a specific address. */
function lookupReturning(address: string): typeof publicLookup {
  return async () => [{ address, family: 4 }];
}

const FIXTURE_HTML =
  "<!doctype html><html><head>" +
  '<meta property="og:title" content="Regression Title">' +
  '<meta property="og:image" content="https://cdn.example.com/cover.png">' +
  '<meta property="og:description" content="A description">' +
  "</head><body>hello</body></html>";

const TITLE_ONLY_HTML =
  "<!doctype html><html><head><title>Page Title Only</title></head><body>content</body></html>";

const FAVICON_HTML =
  '<!doctype html><html><head><title>No OG</title>' +
  '<link rel="icon" href="/assets/logo.ico">' +
  "</head><body></body></html>";

// ---------------------------------------------------------------------------
// fetchOpenGraph — real open-graph-scraper parse (regression guard)
// ---------------------------------------------------------------------------

describe("fetchOpenGraph – real open-graph-scraper parse", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("resolves rich metadata from provided HTML (regression: both-params ogs bug)", async () => {
    globalThis.fetch = jest.fn(async () => fakeHtmlResponse(FIXTURE_HTML)) as unknown as typeof globalThis.fetch;
    const r = await fetchOpenGraph("https://example.com/article", publicLookup);
    expect(r.outcome).toBe("rich");
    expect(r.title).toBe("Regression Title");
    expect(r.image).toBe("https://cdn.example.com/cover.png");
  });
});

// ---------------------------------------------------------------------------
// Redirect happy path
// ---------------------------------------------------------------------------

describe("fetchOpenGraph – redirect follow", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("follows a single 302 redirect and returns rich metadata (AC1 mechanism, AC3)", async () => {
    let calls = 0;
    globalThis.fetch = jest.fn(async () => {
      calls++;
      if (calls === 1) return fakeRedirectResponse("https://final.example.com/article");
      return fakeHtmlResponse(FIXTURE_HTML);
    }) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://source.example.com/redirect", publicLookup);
    expect(r.outcome).toBe("rich");
    expect(r.title).toBe("Regression Title");
    expect(calls).toBe(2); // one redirect + one terminal fetch
  });

  it("follows 301, 303, 307, 308 redirects", async () => {
    for (const status of [301, 303, 307, 308]) {
      let callCount = 0;
      globalThis.fetch = jest.fn(async () => {
        callCount++;
        return callCount === 1
          ? fakeRedirectResponse("https://final.example.com/page", status)
          : fakeHtmlResponse(FIXTURE_HTML);
      }) as unknown as typeof globalThis.fetch;
      const r = await fetchOpenGraph("https://source.example.com/", publicLookup);
      expect(r.outcome).toBe("rich");
    }
  });

  it("handles a relative Location header (e.g. /next)", async () => {
    let relCalls = 0;
    const seenUrls: string[] = [];
    globalThis.fetch = jest.fn(async (u: unknown) => {
      relCalls++;
      seenUrls.push(String(u));
      if (relCalls === 1) return fakeRedirectResponse("/next");
      return fakeHtmlResponse(FIXTURE_HTML);
    }) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://source.example.com/original", publicLookup);
    expect(r.outcome).toBe("rich");
    // Second call should be to the resolved absolute URL
    expect(seenUrls[1]).toBe("https://source.example.com/next");
  });

  it("handles a protocol-relative Location header (//host/p)", async () => {
    let prCalls = 0;
    const prUrls: string[] = [];
    globalThis.fetch = jest.fn(async (u: unknown) => {
      prCalls++;
      prUrls.push(String(u));
      if (prCalls === 1) return fakeRedirectResponse("//cdn.example.com/article");
      return fakeHtmlResponse(FIXTURE_HTML);
    }) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://source.example.com/", publicLookup);
    expect(r.outcome).toBe("rich");
    expect(prUrls[1]).toBe("https://cdn.example.com/article");
  });
});

// ---------------------------------------------------------------------------
// SSRF block cases (AC2)
// ---------------------------------------------------------------------------

describe("fetchOpenGraph – SSRF block via redirect", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("blocks redirect to literal private IP (169.254.169.254 metadata endpoint)", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeRedirectResponse("http://169.254.169.254/latest/meta-data/"),
    ) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://public.example.com/redirect", publicLookup);
    expect(r.outcome).toBe("unsafe_url");
    // Only 1 call should happen — the second fetch (to metadata IP) must never occur
    expect((globalThis.fetch as jest.Mock).mock.calls.length).toBe(1);
  });

  it("blocks redirect to a hostname that DNS-resolves to a private IP (DNS-rebinding)", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeRedirectResponse("https://rebind.example.com/sensitive"),
    ) as unknown as typeof globalThis.fetch;

    // Resolver: start.example.com → public IP; rebind.example.com → private IP
    const rebindLookup = async (host: string) =>
      host.startsWith("rebind")
        ? [{ address: "10.0.0.5", family: 4 }]
        : [{ address: "93.184.216.34", family: 4 }];

    const r = await fetchOpenGraph("https://start.example.com/", rebindLookup);
    expect(r.outcome).toBe("unsafe_url");
    // Only 1 fetch should happen (the first, public one); the redirect target is blocked before fetch
    expect((globalThis.fetch as jest.Mock).mock.calls.length).toBe(1);
  });

  it("blocks redirect to 10.x private range", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeRedirectResponse("http://10.0.0.1/admin"),
    ) as unknown as typeof globalThis.fetch;
    const r = await fetchOpenGraph("https://public.example.com/", publicLookup);
    expect(r.outcome).toBe("unsafe_url");
  });

  it("blocks redirect to 192.168.x private range", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeRedirectResponse("http://192.168.1.1/"),
    ) as unknown as typeof globalThis.fetch;
    const r = await fetchOpenGraph("https://public.example.com/", publicLookup);
    expect(r.outcome).toBe("unsafe_url");
  });

  it("blocks initial URL that resolves to private IP (pre-existing gap now closed)", async () => {
    // Even the very first URL's host is now DNS-resolved before fetch
    const privateResolver = lookupReturning("172.16.0.1");
    // fetch should never be called — blocked before the network
    const fetchSpy = jest.fn();
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://looks-public.example.com/page", privateResolver);
    expect(r.outcome).toBe("unsafe_url");
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// IPv4-mapped IPv6 hex bypass (R2)
// ---------------------------------------------------------------------------

describe("isPrivateIp – IPv4-mapped IPv6 hex (R2 bypass prevention)", () => {
  it("classifies ::ffff:a9fe:a9fe as private (maps to 169.254.254.254)", () => {
    expect(isPrivateIp("::ffff:a9fe:a9fe")).toBe(true);
  });

  it("classifies ::ffff:a9fe:a9fe (169.254.169.254 metadata) as private", () => {
    // 169 = 0xa9, 254 = 0xfe → a9fe for first octet-pair; a9fe for second
    // This encodes 169.254.169.254 exactly
    expect(isPrivateIp("::ffff:a9fe:a9fe")).toBe(true);
  });

  it("classifies ::ffff:0a00:0001 (10.0.0.1) as private", () => {
    expect(isPrivateIp("::ffff:0a00:0001")).toBe(true);
  });

  it("classifies ::ffff:c0a8:0101 (192.168.1.1) as private", () => {
    expect(isPrivateIp("::ffff:c0a8:0101")).toBe(true);
  });

  it("classifies ::ffff:08.08.08.08 (8.8.8.8 dotted-notation mapped) as public", () => {
    expect(isPrivateIp("::ffff:8.8.8.8")).toBe(false);
  });

  it("blocks redirect when lookup returns an IPv4-mapped IPv6 address encoding a private IP", async () => {
    const realFetch = globalThis.fetch;
    try {
      globalThis.fetch = jest.fn(async () =>
        fakeRedirectResponse("https://rebind-v6.example.com/"),
      ) as unknown as typeof globalThis.fetch;

      // start.example.com → public IP; rebind-v6.example.com → IPv4-mapped private
      const mappedLookup = async (host: string) =>
        host.startsWith("rebind-v6")
          ? [{ address: "::ffff:a9fe:a9fe", family: 6 }]
          : [{ address: "93.184.216.34", family: 4 }];

      const r = await fetchOpenGraph("https://start.example.com/", mappedLookup);
      expect(r.outcome).toBe("unsafe_url");
      // Only 1 fetch — the redirect target is blocked before second fetch
      expect((globalThis.fetch as jest.Mock).mock.calls.length).toBe(1);
    } finally {
      globalThis.fetch = realFetch;
    }
  });
});

// ---------------------------------------------------------------------------
// Hop cap
// ---------------------------------------------------------------------------

describe("fetchOpenGraph – redirect hop cap", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("returns too_many_redirects after exceeding MAX_REDIRECT_HOPS (6 chained 302s)", async () => {
    globalThis.fetch = jest.fn(async () => {
      const n = (globalThis.fetch as jest.Mock).mock.calls.length;
      return fakeRedirectResponse(`https://hop${n}.example.com/`);
    }) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://start.example.com/", publicLookup);
    expect(r.outcome).toBe("too_many_redirects");
    // Should stop at MAX_REDIRECT_HOPS fetches (5 hops consumed, next check triggers stop)
    const calls = (globalThis.fetch as jest.Mock).mock.calls.length;
    expect(calls).toBeLessThanOrEqual(7); // generous bound
    expect(calls).toBeGreaterThan(4);
  });
});

// ---------------------------------------------------------------------------
// Bot-block retry (triage bucket: http_4xx ~10.6%)
// ---------------------------------------------------------------------------

describe("fetchOpenGraph – bot-block retry", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("retries with alternate UA on 403 and succeeds on second attempt", async () => {
    const uas: string[] = [];
    globalThis.fetch = jest.fn(async (_u: unknown, opts?: RequestInit) => {
      const ua = (opts?.headers as Record<string, string>)?.["user-agent"] ?? "";
      uas.push(ua);
      if (uas.length === 1) {
        // First attempt: 403 bot-block
        return { status: 403, ok: false, headers: { get: () => null }, body: { getReader: () => ({ read: async () => ({ done: true, value: undefined }) }) } };
      }
      // Retry with alternate UA: succeed
      return fakeHtmlResponse(FIXTURE_HTML);
    }) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://example.com/article", publicLookup);
    expect(r.outcome).toBe("rich");
    expect(uas.length).toBe(2);
    // First and second call must use DIFFERENT user agents
    expect(uas[0]).not.toBe(uas[1]);
    expect(uas[1]).not.toBe(""); // alt UA is non-empty
  });

  it("returns http_4xx after both attempts fail with 403", async () => {
    globalThis.fetch = jest.fn(async () => ({
      status: 403, ok: false,
      headers: { get: () => null },
      body: { getReader: () => ({ read: async () => ({ done: true, value: undefined }) }) },
    })) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://example.com/blocked", publicLookup);
    expect(r.outcome).toBe("http_4xx");
  });
});

// ---------------------------------------------------------------------------
// Timeout retry (triage bucket: ~2.7% slow sites)
// ---------------------------------------------------------------------------

describe("fetchOpenGraph – timeout retry", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("retries on timeout and succeeds on second attempt", async () => {
    let timeoutCalls = 0;
    globalThis.fetch = jest.fn(async () => {
      timeoutCalls++;
      if (timeoutCalls === 1) {
        // Simulate AbortError (timeout)
        const err = new Error("aborted");
        err.name = "AbortError";
        throw err;
      }
      return fakeHtmlResponse(FIXTURE_HTML);
    }) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://slow.example.com/", publicLookup);
    expect(r.outcome).toBe("rich");
    expect(timeoutCalls).toBe(2);
  });

  it("returns timeout after both attempts time out", async () => {
    globalThis.fetch = jest.fn(async () => {
      const err = new Error("aborted");
      err.name = "AbortError";
      throw err;
    }) as unknown as typeof globalThis.fetch;

    const r = await fetchOpenGraph("https://very-slow.example.com/", publicLookup);
    expect(r.outcome).toBe("timeout");
  });
});

// ---------------------------------------------------------------------------
// no_meta favicon fallback
// ---------------------------------------------------------------------------

describe("fetchOpenGraph – no_meta favicon fallback", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("derives image from <link rel=icon> when no OG/Twitter image is present", async () => {
    globalThis.fetch = jest.fn(async () => fakeHtmlResponse(FAVICON_HTML)) as unknown as typeof globalThis.fetch;
    const r = await fetchOpenGraph("https://example.com/page", publicLookup);
    // ogs populates title from <title> (confirmed spike); favicon fallback provides image
    expect(r.title).toBeDefined(); // "No OG" from <title>
    expect(r.image).toContain("logo.ico"); // favicon extracted from <link>
  });

  it("falls back to /favicon.ico when no <link rel=icon> present but page has title", async () => {
    globalThis.fetch = jest.fn(async () => fakeHtmlResponse(TITLE_ONLY_HTML)) as unknown as typeof globalThis.fetch;
    const r = await fetchOpenGraph("https://example.com/title-only", publicLookup);
    expect(r.title).toBe("Page Title Only"); // from <title> via ogs
    // /favicon.ico fallback
    expect(r.image).toBe("https://example.com/favicon.ico");
  });
});

// ---------------------------------------------------------------------------
// isPrivateIp — parameterised REJECT cases
// ---------------------------------------------------------------------------

describe("isPrivateIp – REJECT cases (private ranges)", () => {
  it.each([
    // RFC 1918
    ["10.0.0.1 RFC 1918",                "10.0.0.1"],
    ["10.255.255.255 RFC 1918 high",     "10.255.255.255"],
    ["172.16.0.1 RFC 1918",              "172.16.0.1"],
    ["172.31.255.255 RFC 1918 upper",    "172.31.255.255"],
    ["192.168.1.1 RFC 1918",             "192.168.1.1"],
    // Loopback
    ["127.0.0.1 loopback",               "127.0.0.1"],
    ["127.255.255.255 loopback block",   "127.255.255.255"],
    // Link-local + metadata
    ["169.254.169.254 GCP/AWS metadata", "169.254.169.254"],
    ["169.254.0.1 link-local",           "169.254.0.1"],
    // CGNAT
    ["100.64.0.1 CGNAT",                 "100.64.0.1"],
    ["100.127.255.255 CGNAT upper",      "100.127.255.255"],
    // IANA this-network
    ["0.0.0.0 this-network",             "0.0.0.0"],
    // IPv6
    ["::1 loopback",                     "::1"],
    [":: unspecified",                   "::"],
    ["fc00:: ULA",                       "fc00::"],
    ["fd00:: ULA (also fd prefix)",      "fd00::"],
    ["fe80:: link-local",                "fe80::"],
    ["fd00:ec2::254 GCP internal",       "fd00:ec2::254"],
    // IPv4-mapped hex
    ["::ffff:a9fe:a9fe (169.254.169.254)", "::ffff:a9fe:a9fe"],
    ["::ffff:0a00:0001 (10.0.0.1)",      "::ffff:0a00:0001"],
    ["::ffff:c0a8:0101 (192.168.1.1)",   "::ffff:c0a8:0101"],
  ] as Array<[string, string]>)(
    "classifies %s as private",
    (_label, ip) => {
      expect(isPrivateIp(ip)).toBe(true);
    },
  );
});

// ---------------------------------------------------------------------------
// isPrivateIp — parameterised ACCEPT cases
// ---------------------------------------------------------------------------

describe("isPrivateIp – ACCEPT cases (public ranges)", () => {
  it.each([
    ["8.8.8.8 public DNS",              "8.8.8.8"],
    ["1.1.1.1 Cloudflare DNS",          "1.1.1.1"],
    ["93.184.216.34 example.com",       "93.184.216.34"],
    ["11.0.0.0 just outside 10/8",      "11.0.0.0"],
    ["172.15.0.1 just below 172.16",    "172.15.0.1"],
    ["172.32.0.1 just above 172.31",    "172.32.0.1"],
    ["191.168.1.1 not 192.168",         "191.168.1.1"],
    ["100.63.255.255 below CGNAT",      "100.63.255.255"],
    ["100.128.0.0 above CGNAT",         "100.128.0.0"],
    ["::ffff:8.8.8.8 mapped public",    "::ffff:8.8.8.8"],
  ] as Array<[string, string]>)(
    "classifies %s as public",
    (_label, ip) => {
      expect(isPrivateIp(ip)).toBe(false);
    },
  );
});

// ---------------------------------------------------------------------------
// resolveAndValidateHost — injectable lookup
// ---------------------------------------------------------------------------

describe("resolveAndValidateHost", () => {
  it("resolves successfully for a public hostname", async () => {
    await expect(
      resolveAndValidateHost("example.com", publicLookup),
    ).resolves.toBeUndefined();
  });

  it("throws if any resolved address is private", async () => {
    await expect(
      resolveAndValidateHost("evil.example.com", lookupReturning("10.0.0.1")),
    ).rejects.toThrow();
  });

  it("validates an IP-literal directly without DNS lookup", async () => {
    const noDns = jest.fn() as jest.Mock;
    await expect(resolveAndValidateHost("169.254.169.254", noDns as unknown as Parameters<typeof resolveAndValidateHost>[1])).rejects.toThrow();
    expect(noDns).not.toHaveBeenCalled();
  });

  it("throws if DNS resolution fails (treats as unsafe)", async () => {
    const failLookup = async () => { throw new Error("ENOTFOUND"); };
    await expect(
      resolveAndValidateHost("nonexistent.example.invalid", failLookup as unknown as Parameters<typeof resolveAndValidateHost>[1]),
    ).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// isSafePublicUrl – REJECT cases (unchanged interface, extended table)
// ---------------------------------------------------------------------------

describe("isSafePublicUrl – REJECT cases", () => {
  it.each([
    // Private IPv4 ranges (RFC 1918)
    ["10.x private range – first octet",         "https://10.0.0.1/path"],
    ["10.x private range – high address",        "https://10.255.255.255/"],
    ["172.16 private range – lower bound",       "https://172.16.0.1/"],
    ["172.20 private range – mid range",         "https://172.20.50.50/"],
    ["172.31 private range – upper bound",       "https://172.31.255.255/"],
    ["192.168.x private range",                  "https://192.168.1.1/admin"],
    ["192.168.0.x private range",                "https://192.168.0.254/"],

    // Loopback
    ["127.0.0.1 loopback",                       "https://127.0.0.1/"],
    ["127.x.x.x full loopback block",            "http://127.1.2.3/"],
    ["localhost hostname",                        "https://localhost/"],
    ["subdomain of localhost",                    "http://foo.localhost/"],

    // Link-local / cloud-metadata endpoint
    ["169.254.169.254 AWS/GCP metadata",         "http://169.254.169.254/latest/meta-data/"],
    ["169.254.x.x link-local",                   "https://169.254.0.1/"],

    // Internal hostnames
    [".internal TLD",                            "https://service.internal/api"],
    [".local mDNS TLD",                          "https://printer.local/"],

    // IPv6 loopback
    ["[::1] IPv6 loopback with brackets",        "https://[::1]/"],
    ["::1 IPv6 loopback without brackets",       "https://::1/"],

    // Non-HTTP schemes
    ["file:// scheme",                           "file:///etc/passwd"],
    ["ftp:// scheme",                            "ftp://files.example.com/data"],
    ["gopher:// scheme",                         "gopher://gopher.example.com/"],
    ["javascript: scheme",                       "javascript:alert(1)"],
    ["data: URI",                                "data:text/html,<h1>hi</h1>"],

    // Malformed / garbage
    ["empty string",                             ""],
    ["plain garbage",                            "not-a-url-at-all"],
    ["missing scheme",                           "example.com/path"],
  ] as Array<[string, string]>)(
    "blocks %s",
    (_label, url) => {
      expect(isSafePublicUrl(url)).toBe(false);
    },
  );
});

// ---------------------------------------------------------------------------
// isSafePublicUrl – ACCEPT cases (unchanged interface)
// ---------------------------------------------------------------------------

describe("isSafePublicUrl – ACCEPT cases", () => {
  it.each([
    ["ordinary https URL",                 "https://example.com/article"],
    ["ordinary http URL",                  "http://example.com/article"],
    ["https with path and query",          "https://news.ycombinator.com/item?id=12345"],
    ["public IP address",                  "https://8.8.8.8/"],
    ["public IP not in private range",     "https://1.1.1.1/"],
    ["172.15 – just outside 172.16/12",    "https://172.15.0.1/"],
    ["172.32 – just outside 172.16/12",    "https://172.32.0.1/"],
    ["11.x – just outside 10.0.0.0/8",    "https://11.0.0.0/"],
    ["191.168.x – 191 ≠ 192",             "https://191.168.1.1/"],
    ["URL with port",                      "https://example.com:8443/secure"],
    ["URL with subdomain",                 "https://cdn.example.co.uk/img.png"],
  ] as Array<[string, string]>)(
    "allows %s",
    (_label, url) => {
      expect(isSafePublicUrl(url)).toBe(true);
    },
  );
});

// ---------------------------------------------------------------------------
// Spot-checks
// ---------------------------------------------------------------------------

describe("isSafePublicUrl – edge: 0.0.0.0/8 block", () => {
  it("blocks 0.0.0.0", () => {
    expect(isSafePublicUrl("https://0.0.0.0/")).toBe(false);
  });
});

describe("fetchOpenGraph – unsafe_url on malformed input", () => {
  it("returns unsafe_url for a non-http scheme", async () => {
    const r = await fetchOpenGraph("file:///etc/passwd", publicLookup);
    expect(r.outcome).toBe("unsafe_url");
  });

  it("returns unsafe_url for a 10.x private host", async () => {
    const r = await fetchOpenGraph("http://10.0.0.1/admin", publicLookup);
    expect(r.outcome).toBe("unsafe_url");
  });
});
