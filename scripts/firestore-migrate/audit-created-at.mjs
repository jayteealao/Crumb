// Read-only corpus audit: classify the `createdAt` field across every tweet doc under
// `users/{TARGET_UID}/tweets` so we can decide go/no-go on the one-time normalization.
// Produces the v1.1-vs-ISO-vs-other split + counts. Writes nothing. Uses ADC.
//
// Usage:
//   node audit-created-at.mjs
//
// Pair with `normalize-created-at.mjs` (the writer): run this first, review the split,
// then DRY_RUN the normalizer, then apply.

import admin from 'firebase-admin';

const PROJECT_ID = 'crumbs-a4fdb';
const TARGET_UID = '6yPmdM14V3dPHLe3LO9XCfU4l9f1';
const PAGE_SIZE = 500;

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: PROJECT_ID,
});

const db = admin.firestore();
const tweetsCol = db.collection('users').doc(TARGET_UID).collection('tweets');

// Canonical ISO emitted by both the server and the normalizer: millisecond precision + Z.
const CANONICAL_ISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;
// Any other ISO-8601-ish offset/precision the JS parser still accepts.
const ISO_LOOSE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/;
// Legacy X v1.1: `Wed Sep 16 17:51:39 +0000 2020`.
const V1_1 = /^[A-Z][a-z]{2} [A-Z][a-z]{2} \d{2} \d{2}:\d{2}:\d{2} [+-]\d{4} \d{4}$/;

function classify(raw) {
  if (raw === undefined || raw === null || raw === '') return 'missing';
  if (typeof raw !== 'string') return `non-string(${typeof raw})`;
  if (CANONICAL_ISO.test(raw)) return 'canonical-iso';
  if (V1_1.test(raw)) return 'v1.1';
  if (ISO_LOOSE.test(raw)) return 'iso-loose';
  if (!Number.isNaN(Date.parse(raw))) return 'other-parseable';
  return 'unparseable';
}

async function run() {
  console.log(`Audit createdAt under users/${TARGET_UID}/tweets (read-only)\n`);

  const counts = {};
  const samples = {};
  let scanned = 0;
  let pageCursor = null;

  while (true) {
    let query = tweetsCol.orderBy('__name__').limit(PAGE_SIZE);
    if (pageCursor) query = query.startAfter(pageCursor);
    const snap = await query.get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      scanned++;
      const raw = doc.data().createdAt;
      const cls = classify(raw);
      counts[cls] = (counts[cls] ?? 0) + 1;
      if (!samples[cls]) samples[cls] = { id: doc.id, createdAt: raw };
    }

    pageCursor = snap.docs[snap.docs.length - 1];
    if (snap.size < PAGE_SIZE) break;
  }

  console.log('=== createdAt format distribution ===');
  for (const [cls, n] of Object.entries(counts).sort((a, b) => b[1] - a[1])) {
    const pct = ((n / scanned) * 100).toFixed(1);
    const s = samples[cls];
    console.log(`  ${cls.padEnd(18)} ${String(n).padStart(6)}  (${pct}%)  e.g. ${JSON.stringify(s.createdAt)}`);
  }
  console.log(`\n  total scanned: ${scanned}`);

  const needsMigration = (counts['v1.1'] ?? 0) + (counts['iso-loose'] ?? 0) + (counts['other-parseable'] ?? 0);
  console.log(`  would-normalize (non-canonical but parseable): ${needsMigration}`);
  console.log(`  unparseable (left untouched): ${counts['unparseable'] ?? 0}`);

  process.exit(0);
}

run().catch((err) => {
  console.error('Audit failed:', err);
  process.exit(1);
});
