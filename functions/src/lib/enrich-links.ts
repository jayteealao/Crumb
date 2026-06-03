// Core link-enrichment logic, split from the Firestore-trigger handler so it is
// unit-testable against the in-memory fake (the trigger handler is a thin
// onDocumentCreated wrapper, mirroring poll.ts ⇄ dailyPoll.ts/triggerPoll.ts).
//
// Given a tweet doc's raw `entities`, pick the first external outbound link,
// best-effort OG-fetch its metadata, and write ONE `textAnnotations` url doc in
// the camelCase shape the Android FirestoreTextAnnotation reader expects. The
// doc is keyed `{tweetId}_urls_{start}_{end}` (same convention as poll.ts's
// annotation docs) and the write is skipped if it already exists, so the
// create-trigger and the one-shot backfill never duplicate or clobber a row.

import { FieldValue } from "firebase-admin/firestore";

import { pickExternalUrl } from "./links";
import type { OpenGraphData } from "./og";

/**
 * Minimal Firestore surface used here — satisfied by both the real admin SDK and
 * the test fake. `set`'s options arg is non-optional (always called with
 * `{ merge: true }`) so the real overloaded `DocumentReference.set` is
 * structurally assignable.
 */
interface EnrichDocRef {
  get(): Promise<{ exists: boolean }>;
  set(data: Record<string, unknown>, opts: { merge?: boolean }): Promise<unknown>;
}
export interface EnrichDb {
  doc(path: string): EnrichDocRef;
}

export type EnrichOutcome = "written" | "skipped" | "no_link";

function assertValidUid(uid: string): void {
  if (!uid || uid.includes("/") || uid.includes("..")) {
    throw new Error(`invalid_uid: ${uid}`);
  }
}

/**
 * Enrich one tweet's first external link into a `textAnnotations` url doc.
 *
 * - `"no_link"` — the tweet has no external outbound link (text-only / internal
 *   / media links only); nothing written.
 * - `"skipped"` — the url doc already exists (idempotent re-run); left untouched.
 * - `"written"` — the url doc was written. The URL is ALWAYS written; `title` /
 *   `description` / `imageUrl` are populated only when [ogFetch] succeeds, so a
 *   metadata-less link degrades to a URL-only themed chip on the card.
 */
export async function runEnrichLinks(
  db: EnrichDb,
  uid: string,
  tweetId: string,
  entities: unknown,
  ogFetch: (url: string) => Promise<OpenGraphData>,
): Promise<EnrichOutcome> {
  assertValidUid(uid);
  const picked = pickExternalUrl(entities);
  if (!picked || !picked.expanded_url) return "no_link";

  const start = typeof picked.start === "number" ? picked.start : 0;
  const end = typeof picked.end === "number" ? picked.end : 0;
  const ref = db.doc(`users/${uid}/textAnnotations/${tweetId}_urls_${start}_${end}`);

  // Idempotency: key collision means a prior trigger/backfill already enriched
  // this link. Do not re-fetch (would waste an OG call) or overwrite (a later
  // failed fetch must not clobber an earlier success).
  const existing = await ref.get();
  if (existing.exists) return "skipped";

  const og = await ogFetch(picked.expanded_url).catch(() => ({} as OpenGraphData));

  await ref.set(
    {
      tweetId,
      type: "urls",
      start,
      end,
      url: picked.url ?? null,
      expandedUrl: picked.expanded_url,
      displayUrl: picked.display_url ?? null,
      unwoundUrl: picked.unwound_url ?? null,
      title: og.title ?? null,
      description: og.description ?? null,
      imageUrl: og.image ?? null,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  return "written";
}
