import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import type { DocumentReference, QueryDocumentSnapshot } from "firebase-admin/firestore";

/**
 * Erases all data held for the authenticated caller and deletes their Firebase
 * Auth account. Satisfies GDPR Art. 17 / CCPA § 1798.105 "right to erasure".
 *
 * Deletion cascade (all scoped strictly to the caller's own UID):
 *   1. Firestore sub-collections under `users/{uid}/`:
 *      tweets, metrics, twitter_users, includes, media, sync_status,
 *      textAnnotations  — paged in 500-doc batches to stay within Firestore
 *      limits.
 *   2. The top-level `users/{uid}` document itself.
 *   3. The GCP Secret Manager refresh-token secret (same helper used by
 *      `disconnectX`).
 *   4. The Firebase Auth user record — the account is gone after this step.
 *
 * @param request - callable request; `request.data` is ignored.
 *   `request.auth` must be present.
 * @returns `{ ok: true }` — the client should sign out and navigate to the
 *   login screen after receiving this.
 * @throws {HttpsError} `"unauthenticated"` – when `request.auth` is absent.
 * @throws {HttpsError} `"internal"` – when any server-side step fails.
 */

const BATCH_SIZE = 499; // Firestore batch limit is 500 ops; stay one below.
const PAGE_SIZE = 499;

// All sub-collection names that live under `users/{uid}/`.
const USER_SUBCOLLECTIONS = [
  "tweets",
  "metrics",
  "twitter_users",
  "includes",
  "media",
  "sync_status",
  "textAnnotations",
] as const;

export const deleteAccount = onCall(
  { region: "europe-west2", timeoutSeconds: 540, memory: "512MiB" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required");
    }

    const uid = request.auth.uid;

    const { db } = await import("../lib/admin");
    const { deleteRefreshToken } = await import("../lib/secrets");
    const { getAuth } = await import("firebase-admin/auth");

    const database = db();

    // ── 1. Delete all Firestore sub-collections ──────────────────────────
    let totalDeleted = 0;
    for (const subcol of USER_SUBCOLLECTIONS) {
      const colRef = database.collection(`users/${uid}/${subcol}`);
      let lastDoc: QueryDocumentSnapshot | undefined;
      let pageEmpty = false;

      while (!pageEmpty) {
        let query = colRef.orderBy("__name__").limit(PAGE_SIZE);
        if (lastDoc) query = query.startAfter(lastDoc);

        const snap = await query.get();
        if (snap.empty) {
          pageEmpty = true;
          break;
        }

        // Commit deletions in BATCH_SIZE chunks so we never exceed the 500-op
        // limit even if PAGE_SIZE is bumped in the future.
        const refs: DocumentReference[] = snap.docs.map((d) => d.ref);
        for (let i = 0; i < refs.length; i += BATCH_SIZE) {
          const chunk = refs.slice(i, i + BATCH_SIZE);
          const batch = database.batch();
          for (const ref of chunk) {
            batch.delete(ref);
          }
          await batch.commit();
          totalDeleted += chunk.length;
        }

        lastDoc = snap.docs[snap.docs.length - 1];
        if (snap.docs.length < PAGE_SIZE) {
          pageEmpty = true;
        }
      }
    }

    // ── 2. Delete the top-level users/{uid} document ─────────────────────
    const userDocRef = database.doc(`users/${uid}`);
    await userDocRef.delete();

    // ── 3. Delete the GCP Secret Manager refresh-token ───────────────────
    try {
      await deleteRefreshToken(uid);
    } catch (err) {
      // Log but do not abort — the Auth deletion below is the critical step.
      logger.warn("delete_account_secret_warn", {
        uid,
        error: (err as Error).message,
      });
    }

    // ── 4. Delete the Firebase Auth user ─────────────────────────────────
    try {
      await getAuth().deleteUser(uid);
    } catch (err) {
      logger.error("delete_account_auth_failed", {
        uid,
        error: (err as Error).message,
      });
      throw new HttpsError("internal", "auth_delete_failed");
    }

    logger.info("delete_account_complete", { uid, firestoreDocsDeleted: totalDeleted });
    return { ok: true };
  },
);
