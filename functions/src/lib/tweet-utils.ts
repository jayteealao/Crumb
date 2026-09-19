/**
 * Normalizes a raw tweet `created_at` value to a canonical ISO-8601 UTC string
 * (`yyyy-MM-ddTHH:mm:ss.SSSZ`) so the bookmark corpus is uniform and both
 * `orderBy("createdAt")` and the client's lexicographic comparison sort chronologically.
 *
 * The X corpus mixes two shapes: v2 ISO-8601 (`2026-05-13T12:22:37.000Z`) and legacy v1.1
 * (`Wed Sep 16 17:51:39 +0000 2020`). Both are recognised by the JS date parser; ISO inputs
 * are re-emitted in canonical millisecond form and v1.1 inputs are converted. Anything that
 * does not parse (or is not a non-empty string) is returned unchanged so we never destroy a
 * value we do not understand.
 */
export function normalizeCreatedAt(raw: unknown): unknown {
  if (typeof raw !== "string" || raw.trim() === "") return raw;
  const ts = Date.parse(raw);
  if (Number.isNaN(ts)) return raw;
  return new Date(ts).toISOString();
}
