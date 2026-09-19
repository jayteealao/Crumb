// One-shot count of bookmarks (and supporting subcollections) for a single UID.
// Mirrors how the Android client reads `users/{uid}/tweets/...`.

import admin from 'firebase-admin';

const PROJECT_ID = 'crumbs-a4fdb';
const UID = process.env.UID || '6yPmdM14V3dPHLe3LO9XCfU4l9f1';

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: PROJECT_ID,
});

const db = admin.firestore();
const userScope = db.collection('users').doc(UID);

async function main() {
  console.log(`Project: ${PROJECT_ID}  UID: ${UID}\n`);

  const userDocSnap = await userScope.get();
  console.log(`users/${UID} exists:`, userDocSnap.exists);

  const syncStateSnap = await userScope.collection('sync_status').doc('state').get();
  console.log(`sync_status/state exists:`, syncStateSnap.exists);
  if (syncStateSnap.exists) {
    const d = syncStateSnap.data();
    console.log('  linked            :', d.linked);
    console.log('  lastPolledAt      :', d.lastPolledAt?.toDate?.()?.toISOString?.() ?? d.lastPolledAt);
    console.log('  lastPollOk        :', d.lastPollOk);
    console.log('  lastPollError     :', d.lastPollError);
    console.log('  cursor            :', d.cursor);
    console.log('  twitterUserId     :', d.twitterUserId);
    console.log('  lastSuccessAt     :', d.lastSuccessAt?.toDate?.()?.toISOString?.() ?? d.lastSuccessAt);
  }

  console.log('\nCollection counts under users/{uid}/...');
  const targets = ['tweets', 'metrics', 'media', 'twitter_users', 'includes', 'textAnnotations'];
  for (const t of targets) {
    try {
      const agg = await userScope.collection(t).count().get();
      console.log(`  ${t.padEnd(20)} ${agg.data().count}`);
    } catch (e) {
      console.log(`  ${t.padEnd(20)} ERROR ${e.message}`);
    }
  }

  // Sample 3 tweet doc IDs + tweetId fields to confirm shape matches what the
  // android-reader paginator expects (orderBy(documentId()), getString("tweetId")).
  console.log('\nFirst 3 tweets (id  ->  tweetId field):');
  const sample = await userScope.collection('tweets').limit(3).get();
  sample.forEach((doc) => {
    console.log(`  ${doc.id}  ->  ${doc.get('tweetId')}`);
  });

  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
