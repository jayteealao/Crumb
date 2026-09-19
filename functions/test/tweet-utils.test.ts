import { normalizeCreatedAt } from "../src/lib/tweet-utils";

describe("normalizeCreatedAt", () => {
  it("converts legacy v1.1 timestamps to canonical ISO-8601", () => {
    expect(normalizeCreatedAt("Wed Sep 16 17:51:39 +0000 2020")).toBe("2020-09-16T17:51:39.000Z");
  });

  it("re-emits ISO-8601 inputs in canonical millisecond form", () => {
    expect(normalizeCreatedAt("2026-05-13T12:22:37Z")).toBe("2026-05-13T12:22:37.000Z");
    expect(normalizeCreatedAt("2026-05-13T12:22:37.000Z")).toBe("2026-05-13T12:22:37.000Z");
  });

  it("leaves unparseable or non-string values untouched (never destroys unknown data)", () => {
    expect(normalizeCreatedAt("not a date")).toBe("not a date");
    expect(normalizeCreatedAt("")).toBe("");
    expect(normalizeCreatedAt(undefined)).toBeUndefined();
    expect(normalizeCreatedAt(null)).toBeNull();
    expect(normalizeCreatedAt(12345)).toBe(12345);
  });
});
