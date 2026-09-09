// Verify helper for poll-correctness slice.
//
// Mints a Firebase custom token via IAM Credentials API signJwt (requires
// `roles/iam.serviceAccountTokenCreator` on firebase-adminsdk-fbsvc), exchanges
// it for an ID token via Identity Toolkit, then POSTs to the triggerPoll
// callable once or twice in quick succession to exercise the debounce path.
//
// Usage:
//   cd scripts/firestore-migrate
//   node invoke-triggerpoll.mjs <uid> [--debounce]
//
// With --debounce: first call exercises the success path, second call
// (immediately after the first returns) should return ok:false reason:debounced.

import admin from 'firebase-admin';

const PROJECT_ID = 'crumbs-a4fdb';
const SA_EMAIL = 'firebase-adminsdk-fbsvc@crumbs-a4fdb.iam.gserviceaccount.com';
const TRIGGERPOLL_URL = 'https://triggerpoll-6cs5i7tprq-nw.a.run.app';
const WEB_API_KEY = 'AIzaSyAUXoD37Cy8ghlz7dGggEC9w657nHLbo9U';

const args = process.argv.slice(2);
const flags = args.filter((a) => a.startsWith('--'));
const positional = args.filter((a) => !a.startsWith('--'));
const TEST_DEBOUNCE = flags.includes('--debounce');
const uid = positional[0];

if (!uid) {
  console.error('Usage: node invoke-triggerpoll.mjs <uid> [--debounce]');
  process.exit(2);
}

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  serviceAccountId: SA_EMAIL,
  projectId: PROJECT_ID,
});

async function mintIdToken() {
  const customToken = await admin.auth().createCustomToken(uid);
  const resp = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${WEB_API_KEY}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: customToken, returnSecureToken: true }),
    },
  );
  if (!resp.ok) {
    throw new Error(`identitytoolkit_exchange_failed: ${resp.status} ${await resp.text()}`);
  }
  const json = await resp.json();
  return json.idToken;
}

async function callTriggerPoll(idToken, label) {
  const startedAt = new Date().toISOString();
  const resp = await fetch(TRIGGERPOLL_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${idToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ data: {} }),
  });
  const text = await resp.text();
  const finishedAt = new Date().toISOString();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { __raw: text };
  }
  console.log(JSON.stringify({ label, startedAt, finishedAt, status: resp.status, body: parsed }, null, 2));
  return parsed;
}

const idToken = await mintIdToken();
console.log('=== call 1 ===');
const r1 = await callTriggerPoll(idToken, 'call-1');

if (TEST_DEBOUNCE) {
  console.log('=== call 2 (debounce probe) ===');
  const r2 = await callTriggerPoll(idToken, 'call-2-debounce');
  const debounced = r2?.result?.ok === false && r2?.result?.reason === 'debounced';
  console.log(`debounce_observed: ${debounced}`);
  if (!debounced) {
    process.exitCode = 1;
  }
}

process.exit(process.exitCode ?? 0);
