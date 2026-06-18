// Correlate RCA hypotheses against actual Firestore doc shapes.
// Reads samples and aggregates from each subcollection under users/{UID}/...

import admin from 'firebase-admin';

const PROJECT_ID = 'crumbs-a4fdb';
const UID = process.env.UID || '6yPmdM14V3dPHLe3LO9XCfU4l9f1';
const SAMPLE = parseInt(process.env.SAMPLE || '5', 10);
const SCAN = parseInt(process.env.SCAN || '300', 10); // shape-stat scan size per collection

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: PROJECT_ID,
});
const db = admin.firestore();
const userScope = db.collection('users').doc(UID);

function summarizeFields(docs) {
  // For each field name, count: present, null, value-type
  const stats = new Map();
  for (const d of docs) {
    const data = d.data();
    for (const [k, v] of Object.entries(data)) {
      if (!stats.has(k)) stats.set(k, { present: 0, nullCount: 0, types: new Map() });
      const s = stats.get(k);
      s.present++;
      if (v === null) s.nullCount++;
      const t = v === null ? 'null' : Array.isArray(v) ? 'array' : typeof v;
      s.types.set(t, (s.types.get(t) || 0) + 1);
    }
  }
  return stats;
}

function printStats(label, total, stats) {
  console.log(`\n--- ${label} (scanned=${total}) ---`);
  const rows = [...stats.entries()].sort((a, b) => b[1].present - a[1].present);
  for (const [name, s] of rows) {
    const presencePct = ((s.present / total) * 100).toFixed(0);
    const nullPct = s.present > 0 ? ((s.nullCount / s.present) * 100).toFixed(0) : '0';
    const typeStr = [...s.types.entries()].map(([t, c]) => `${t}:${c}`).join(' ');
    console.log(`  ${name.padEnd(22)} present=${s.present}/${total} (${presencePct}%) null=${s.nullCount} (${nullPct}%)  types: ${typeStr}`);
  }
}

async function dumpSample(label, ref) {
  const snap = await ref.limit(SAMPLE).get();
  console.log(`\n=== ${label} — first ${snap.size} docs ===`);
  snap.forEach((doc) => {
    console.log(`docId=${doc.id}`);
    console.log(JSON.stringify(doc.data(), null, 2));
  });
}

async function shapeScan(label, ref) {
  const snap = await ref.limit(SCAN).get();
  const stats = summarizeFields(snap.docs);
  printStats(label, snap.size, stats);
  return { total: snap.size, stats };
}

async function countMissingTweetId() {
  // 719 docs in users/{uid}/tweets were dropped by `doc.getString("tweetId")?.let(...)`
  // — either tweetId field is missing or empty. Confirm directly.
  console.log('\n=== tweets without tweetId field ===');
  const sample = await userScope.collection('tweets').limit(SCAN).get();
  const missing = [];
  for (const doc of sample.docs) {
    const v = doc.get('tweetId');
    if (v === null || v === undefined || v === '') {
      missing.push({ docId: doc.id, tweetIdValue: v });
    }
  }
  console.log(`scanned=${sample.size}  missing-tweetId=${missing.length}`);
  for (const m of missing.slice(0, 10)) {
    console.log(`  docId=${m.docId}  tweetIdValue=${JSON.stringify(m.tweetIdValue)}`);
  }
  if (missing.length > 10) console.log(`  ... and ${missing.length - 10} more`);
}

async function main() {
  console.log(`Project: ${PROJECT_ID}  UID: ${UID}  SAMPLE=${SAMPLE}  SCAN=${SCAN}`);

  // SAMPLES — show actual doc shape
  await dumpSample('tweets', userScope.collection('tweets'));
  await dumpSample('twitter_users', userScope.collection('twitter_users'));
  await dumpSample('metrics', userScope.collection('metrics'));
  await dumpSample('media', userScope.collection('media'));

  // SHAPE STATS — aggregate over a larger scan to catch field-presence and null rates
  await shapeScan('tweets', userScope.collection('tweets'));
  await shapeScan('twitter_users', userScope.collection('twitter_users'));
  await shapeScan('metrics', userScope.collection('metrics'));
  await shapeScan('media', userScope.collection('media'));

  // Targeted check for the 719 missing-tweetId mystery
  await countMissingTweetId();

  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
