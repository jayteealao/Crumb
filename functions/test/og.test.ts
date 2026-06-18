// Unit tests for isSafePublicUrl — the pre-fetch SSRF barrier in lib/og.
//
// Returns true  → URL is safe to fetch (public, http/https, non-private host).
// Returns false → URL should be blocked (private range, loopback, link-local,
//                 internal hostname, non-http scheme, or malformed input).

import { isSafePublicUrl } from "../src/lib/og";

// ---------------------------------------------------------------------------
// Parameterised REJECT cases
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
    ["bare 'local' would not match (control)",   null], // skip – covered by ACCEPT below

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
  ] as Array<[string, string | null]>)(
    "blocks %s",
    (_label, url) => {
      if (url === null) return; // control placeholder – skip
      expect(isSafePublicUrl(url)).toBe(false);
    },
  );
});

// ---------------------------------------------------------------------------
// Parameterised ACCEPT cases
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
// Spot-check: 0.0.0.0/8 (IANA "this network") is also blocked
// ---------------------------------------------------------------------------

describe("isSafePublicUrl – edge: 0.0.0.0/8 block", () => {
  it("blocks 0.0.0.0", () => {
    expect(isSafePublicUrl("https://0.0.0.0/")).toBe(false);
  });
});
