// One-time corpus migration: normalize the `createdAt` field on every tweet doc
// under `users/{TARGET_UID}/tweets` to canonical ISO-8601 UTC
// (`yyyy-MM-ddTHH:mm:ss.SSSZ`). After this runs, the whole corpus is uniform, so
// Firestore `orderBy("createdAt")` and the Android client's lexicographic comparison
// both sort chronologically. Uses Firebase Admin SDK with ADC (bypasses security
// rules). Idempotent — docs already in canonical form are skipped, so a re-run is a
// no-op. Mirrors the server's `normalizeCreatedAt` (functions/src/lib/tweet-utils.ts).
//
// Usage:
//   DRY_RUN=1 node normalize-created-at.mjs   # plan only, no writes (review first)
//   node normalize-created-at.mjs             # apply (touches production data)
//
// Safety: only the `createdAt` field is written (merge); all other fields are left
// untouched. Unparseable values are reported and left as-is — we never destroy a value
// we do not understand.

import admin from 'firebase-admin';

const PROJECT_ID = 'crumbs-a4fdb';
const TARGET_UID = process.env.TARGET_UID || process.argv[2] || '';
if (!TARGET_UID) {
  console.error('Error: TARGET_UID is required. Set it via the TARGET_UID env var or pass it as the first argument.');
  process.exit(1);
}
const DRY_RUN = process.env.DRY_RUN === '1';
const PAGE_SIZE = 500;
const BATCH_SIZE = 400; // < 500 Firestore batch limit, headroom for retries

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: PROJECT_ID,
});

const db = admin.firestore();
const tweetsCol = db.collection('users').doc(TARGET_UID).collection('tweets');

// Same rule as the server: parse (ISO or v1.1), re-emit canonical ISO with millis;
// return null when the value is not a non-empty parseable string so the caller leaves
// it untouched.
function canonicalIso(raw) {
  if (typeof raw !== 'string' || raw.trim() === '') return null;
  const ts = Date.parse(raw);
  if (Number.isNaN(ts)) return null;
  return new Date(ts).toISOString();
}

const stats = { scanned: 0, normalized: 0, alreadyCanonical: 0, missing: 0, unparseable: 0 };
const unparseableSamples = [];

async function run() {
  console.log(`Normalize createdAt under users/${TARGET_UID}/tweets`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN (no writes)' : 'APPLY (writes will happen)'}`);
  console.log(`Project: ${PROJECT_ID}\n`);

  let pageCursor = null;
  let batch = db.batch();
  let opsInBatch = 0;
  let commits = 0;

  while (true) {
    let query = tweetsCol.orderBy('__name__').limit(PAGE_SIZE);
    if (pageCursor) query = query.startAfter(pageCursor);
    const snap = await query.get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      stats.scanned++;
      const data = doc.data();
      const raw = data.createdAt;

      if (raw === undefined || raw === null || raw === '') {
        stats.missing++;
        continue;
      }

      const canonical = canonicalIso(raw);
      if (canonical === null) {
        stats.unparseable++;
        if (unparseableSamples.length < 10) {
          unparseableSamples.push({ id: doc.id, createdAt: raw });
        }
        continue;
      }

      if (canonical === raw) {
        stats.alreadyCanonical++;
        continue;
      }

      stats.normalized++;
      if (DRY_RUN) {
        if (stats.normalized <= 5) {
          console.log(`  [DRY] ${doc.id}: ${JSON.stringify(raw)} -> ${canonical}`);
        }
        continue;
      }

      batch.set(doc.ref, { createdAt: canonical }, { merge: true });
      opsInBatch++;
      if (opsInBatch >= BATCH_SIZE) {
        await batch.commit();
        commits++;
        process.stdout.write(`  committed batch ${commits} (normalized ${stats.normalized})\r`);
        batch = db.batch();
        opsInBatch = 0;
      }
    }

    pageCursor = snap.docs[snap.docs.length - 1];
    if (snap.size < PAGE_SIZE) break;
  }

  if (opsInBatch > 0 && !DRY_RUN) {
    await batch.commit();
    commits++;
  }

  console.log('\n=== Summary ===');
  console.log(`  scanned          ${stats.scanned}`);
  console.log(`  normalized       ${stats.normalized}${DRY_RUN ? ' (would write)' : ''}`);
  console.log(`  alreadyCanonical ${stats.alreadyCanonical}`);
  console.log(`  missing          ${stats.missing}`);
  console.log(`  unparseable      ${stats.unparseable}`);
  console.log(`  batches          ${commits}`);
  if (unparseableSamples.length) {
    console.log('\nUnparseable samples (left untouched):');
    for (const s of unparseableSamples) {
      console.log(`  ${s.id}: ${JSON.stringify(s.createdAt)}`);
    }
  }

  console.log('\nDone.');
  process.exit(0);
}

run().catch((err) => {
  console.error('Normalization failed:', err);
  process.exit(1);
});
