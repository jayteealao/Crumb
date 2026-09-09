// One-shot migration: rewrite root-level Firestore collections to deterministic
// IDs under `users/{TARGET_UID}/...`. Uses Firebase Admin SDK with ADC so it
// bypasses Firestore security rules. Idempotent for the same source data — a
// re-run produces the same target docs because IDs are deterministic.
//
// Usage:
//   DRY_RUN=1 node migrate.mjs   # plan only, no writes
//   node migrate.mjs             # apply
//
// Source collections (counts at time of inventory):
//   tweets             3561  -> users/{UID}/tweets/{tweetId}
//   metrics            3561  -> users/{UID}/metrics/{tweetId}
//   media              2624  -> users/{UID}/media/{mediaKey}
//   users              2298  -> users/{UID}/twitter_users/{userId}   (renamed)
//   includes           2641  -> users/{UID}/includes/{tweetId}_{seq} (composite)
//   textAnnotations    1149  -> users/{UID}/textAnnotations/{tweetId}_{type}_{start}_{end}
//   mediaContent         50  -> SKIP (abandoned base64-blob experiment)
//   mediaKeys          2624  -> SKIP (undocumented mirror of `media`; same data)

import admin from 'firebase-admin';

const PROJECT_ID = 'crumbs-a4fdb';
const TARGET_UID = process.env.TARGET_UID || process.argv[2] || '';
if (!TARGET_UID) {
  console.error('Error: TARGET_UID is required. Set it via the TARGET_UID env var or pass it as the first argument.');
  process.exit(1);
}
const DRY_RUN = process.env.DRY_RUN === '1';
const PAGE_SIZE = 500;     // Firestore page size for source reads
const BATCH_SIZE = 400;    // < 500 so we have headroom for retries; Firestore batch limit is 500

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: PROJECT_ID,
});

const db = admin.firestore();
const userScope = db.collection('users').doc(TARGET_UID);

const stats = {};
const dropped = [];

async function migrateCollection({ source, target, idFrom, recordIdSuffixCounter = null }) {
  console.log(`\n=== ${source} -> users/${TARGET_UID}/${target} ===`);
  stats[source] = { sourceCount: 0, written: 0, skipped: 0 };

  let pageCursor = null;
  let batch = db.batch();
  let opsInBatch = 0;
  let commits = 0;
  const counterByPrefix = new Map(); // for composite IDs needing a stable per-prefix sequence

  while (true) {
    let query = db.collection(source).orderBy('__name__').limit(PAGE_SIZE);
    if (pageCursor) query = query.startAfter(pageCursor);
    const snap = await query.get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      stats[source].sourceCount++;
      const data = doc.data();
      const newId = idFrom(data, counterByPrefix);
      if (!newId) {
        stats[source].skipped++;
        dropped.push({ source, originalId: doc.id, reason: 'no-derivable-id', data });
        continue;
      }

      const targetRef = userScope.collection(target).doc(newId);
      const origCreateTime = doc.createTime?.toDate?.() ?? null;
      const enriched = {
        ...data,
        uid: TARGET_UID,
        migratedAt: admin.firestore.FieldValue.serverTimestamp(),
        originalDocId: doc.id,
        // Use the original Firestore createTime as updatedAt so future cursor
        // queries have a meaningful starting timestamp even for legacy data.
        updatedAt: origCreateTime ?? admin.firestore.FieldValue.serverTimestamp(),
      };

      if (DRY_RUN) {
        stats[source].written++;
        if (stats[source].sourceCount <= 3) {
          console.log(`  [DRY] ${doc.id} -> ${target}/${newId}`);
        }
        continue;
      }

      batch.set(targetRef, enriched, { merge: true });
      opsInBatch++;
      stats[source].written++;

      if (opsInBatch >= BATCH_SIZE) {
        await batch.commit();
        commits++;
        process.stdout.write(`  committed batch ${commits} (${stats[source].written}/${stats[source].sourceCount})\r`);
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
  console.log(`  done. source=${stats[source].sourceCount} written=${stats[source].written} skipped=${stats[source].skipped} batches=${commits}`);
}

// Composite-id helper: derive ID from prefix + sequence number per prefix so
// many-per-parent rows get stable, deduplicated IDs across re-runs.
function compositeId(prefix, counterByPrefix) {
  const seq = (counterByPrefix.get(prefix) ?? 0) + 1;
  counterByPrefix.set(prefix, seq);
  return `${prefix}_${seq.toString().padStart(4, '0')}`;
}

async function run() {
  console.log(`Migration plan: rewrite root collections under users/${TARGET_UID}/...`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN (no writes)' : 'APPLY (writes will happen)'}`);
  console.log(`Project: ${PROJECT_ID}\n`);

  // 1:1 deterministic — use the natural primary key as the doc-id.
  await migrateCollection({
    source: 'tweets',
    target: 'tweets',
    idFrom: (d) => d.tweetId || null,
  });

  await migrateCollection({
    source: 'metrics',
    target: 'metrics',
    idFrom: (d) => d.tweetId || null,
  });

  await migrateCollection({
    source: 'media',
    target: 'media',
    idFrom: (d) => d.mediaKey || null,
  });

  // `users` root collection holds Twitter author profiles; rename so it doesn't
  // shadow the new auth scope at `users/{uid}`.
  await migrateCollection({
    source: 'users',
    target: 'twitter_users',
    idFrom: (d) => d.userId || null,
  });

  // Many-per-parent — composite IDs using tweetId + per-prefix sequence so a
  // re-run produces identical IDs.
  await migrateCollection({
    source: 'includes',
    target: 'includes',
    idFrom: (d, counter) => d.tweetId ? compositeId(d.tweetId, counter) : null,
  });

  await migrateCollection({
    source: 'textAnnotations',
    target: 'textAnnotations',
    idFrom: (d, counter) => d.tweetId ? compositeId(d.tweetId, counter) : null,
  });

  // Skipped: mediaContent (50 abandoned base64-image docs), mediaKeys (2624
  // undocumented mirror of `media`). Quarantine, do not migrate.

  console.log('\n=== Summary ===');
  for (const [src, s] of Object.entries(stats)) {
    console.log(`  ${src.padEnd(20)} source=${s.sourceCount} written=${s.written} skipped=${s.skipped}`);
  }
  if (dropped.length) {
    console.log(`\nDropped docs (no derivable ID): ${dropped.length}`);
    for (const d of dropped.slice(0, 10)) {
      console.log(`  ${d.source}/${d.originalId} (${d.reason})`);
    }
    if (dropped.length > 10) console.log(`  ... and ${dropped.length - 10} more`);
  }

  // Post-migration verification: count target collections.
  if (!DRY_RUN) {
    console.log('\n=== Verification: target collection counts ===');
    const targets = ['tweets', 'metrics', 'media', 'twitter_users', 'includes', 'textAnnotations'];
    for (const t of targets) {
      const agg = await userScope.collection(t).count().get();
      const c = agg.data().count;
      const sourceName = t === 'twitter_users' ? 'users' : t;
      const sourceCount = stats[sourceName]?.sourceCount ?? '?';
      const match = c === stats[sourceName]?.written ? '✓' : '✗';
      console.log(`  ${match} users/{uid}/${t.padEnd(20)} count=${c}   (source=${sourceCount} written=${stats[sourceName]?.written ?? '?'})`);
    }
  }

  console.log('\nDone.');
  process.exit(0);
}

run().catch((err) => {
  console.error('Migration failed:', err);
  process.exit(1);
});
