// One-shot backfill: write `{id: doc.id}` onto every doc under
// `users/{UID}/tweets/` that is missing the `id` field, and seed
// `users/{UID}/sync_status/state.latest_tweet_id` to the BigInt-max of all
// stored tweet ids if absent or numerically smaller.
//
// Idempotent: re-running is a no-op once every doc has `id` and the cache
// reflects the BigInt-max. Composes safely with concurrent dailyPoll writes
// (uses set({merge: true})).
//
// Why this exists:
//   The legacy root-collection migration (scripts/firestore-migrate/migrate.mjs)
//   carried `tweetId` as a data field but did NOT write `id`. The poll engine
//   previously selected the stop-on-overlap boundary via `orderBy("id")` —
//   which mis-ordered mixed-length snowflake ids (lexicographic). The fix
//   reads `sync_status.latest_tweet_id` (a BigInt-max-string cache) instead
//   and the success-path poll re-writes it. This script seeds that cache on
//   first run.
//
// Usage:
//   gcloud auth application-default login
//   cd scripts/firestore-migrate
//   node backfill-tweet-id-field.mjs <uid>
//   node backfill-tweet-id-field.mjs <uid> --dry-run
//
// Credentials: ADC. If `gcloud` impersonation is set, the script may fail
// with a credential-type mismatch — run `gcloud auth application-default
// login` to ensure direct ADC.

import admin from 'firebase-admin';

const PROJECT_ID = 'crumbs-a4fdb';
const PAGE_SIZE = 500;
const BATCH_SIZE = 400;

function bail(msg) {
  console.error(msg);
  process.exit(1);
}

const args = process.argv.slice(2);
const flags = args.filter((a) => a.startsWith('--'));
const positional = args.filter((a) => !a.startsWith('--'));
const DRY_RUN = flags.includes('--dry-run');
const UID = positional[0];

if (!UID) bail('Usage: node backfill-tweet-id-field.mjs <uid> [--dry-run]');
if (UID.includes('/') || UID.includes('..')) bail(`invalid uid: ${UID}`);

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: PROJECT_ID,
});

const db = admin.firestore();
const tweetsRef = db.collection(`users/${UID}/tweets`);
const statusRef = db.doc(`users/${UID}/sync_status/state`);

function bigintMax(a, b) {
  return a > b ? a : b;
}

async function run() {
  console.log(`Project:  ${PROJECT_ID}`);
  console.log(`UID:      ${UID}`);
  console.log(`Mode:     ${DRY_RUN ? 'DRY RUN (no writes)' : 'APPLY'}`);
  console.log(`Path:     users/${UID}/tweets`);
  console.log('');

  let total = 0;
  let missing = 0;
  let backfilled = 0;
  let maxBig = 0n;
  let cursor = null;
  let batch = db.batch();
  let opsInBatch = 0;
  let commits = 0;

  while (true) {
    let query = tweetsRef.orderBy(admin.firestore.FieldPath.documentId()).limit(PAGE_SIZE);
    if (cursor) query = query.startAfter(cursor);
    const snap = await query.get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      total++;
      const data = doc.data() ?? {};
      // Update BigInt-max regardless of whether `id` was missing — every doc
      // contributes to the cache.
      try {
        const idBig = BigInt(doc.id);
        if (idBig > maxBig) maxBig = idBig;
      } catch {
        // Non-numeric doc id (unexpected for tweet collection). Skip the
        // BigInt update but still backfill the `id` field if missing.
      }

      if (data.id !== undefined) continue;
      missing++;

      if (DRY_RUN) {
        if (missing <= 5) console.log(`  [DRY] ${doc.id} would get { id: "${doc.id}" }`);
        continue;
      }

      batch.set(doc.ref, { id: doc.id }, { merge: true });
      opsInBatch++;
      backfilled++;

      if (opsInBatch >= BATCH_SIZE) {
        await batch.commit();
        commits++;
        process.stdout.write(`  committed batch ${commits} (backfilled=${backfilled}/${missing})\r`);
        batch = db.batch();
        opsInBatch = 0;
      }
    }

    cursor = snap.docs[snap.docs.length - 1];
    if (snap.size < PAGE_SIZE) break;
  }

  if (opsInBatch > 0 && !DRY_RUN) {
    await batch.commit();
    commits++;
  }

  console.log('');
  console.log(`Scanned:    ${total}`);
  console.log(`Missing id: ${missing}`);
  console.log(`Backfilled: ${backfilled}${DRY_RUN ? ' (dry-run, no writes)' : ''}`);
  console.log(`Batches:    ${commits}`);

  // sync_status.latest_tweet_id seed/update.
  if (maxBig > 0n) {
    const newLatest = maxBig.toString();
    const statusSnap = await statusRef.get();
    const current = statusSnap.exists ? (statusSnap.data() ?? {}) : {};
    const existing = current.latest_tweet_id;
    let needsWrite = false;
    if (!existing) {
      needsWrite = true;
    } else {
      try {
        if (BigInt(existing) < maxBig) needsWrite = true;
      } catch {
        needsWrite = true;
      }
    }
    if (needsWrite) {
      console.log(`latest_tweet_id:   ${existing ?? '(absent)'} -> ${newLatest}`);
      if (!DRY_RUN) {
        await statusRef.set({ latest_tweet_id: newLatest }, { merge: true });
      }
    } else {
      console.log(`latest_tweet_id:   ${existing} (already >= BigInt-max ${newLatest}, no-op)`);
    }
  } else {
    console.log('latest_tweet_id:   no numeric doc ids found, skipping cache seed');
  }

  console.log('\nDone.');
  process.exit(0);
}

run().catch((err) => {
  console.error('Backfill failed:', err);
  process.exit(1);
});
