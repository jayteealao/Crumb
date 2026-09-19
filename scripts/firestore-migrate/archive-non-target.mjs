// Archive every Firestore document that is NOT under
// users/6yPmdM14V3dPHLe3LO9XCfU4l9f1/**, then optionally delete the originals.
//
// Mirror path mapping (preserves structure with an `items` separator at the
// root so Firestore's collection/doc alternation stays valid; we can't use
// `__items__` because Firestore reserves collection IDs matching `__.*__`):
//   <coll>/<doc>                 -> archived/<coll>/items/<doc>
//   <coll>/<doc>/<sub>/<id>      -> archived/<coll>/items/<doc>/<sub>/<id>
//   users/<otherUid>/<sub>/<id>  -> archived/users/items/<otherUid>/<sub>/<id>
//
// users/<TARGET_UID>/** is left untouched. The `archived` root collection
// itself is also skipped (so re-runs don't re-archive the archive).
//
// Usage:
//   node archive-non-target.mjs                 # DRY RUN (default, prints plan)
//   APPLY=1 node archive-non-target.mjs         # copy to archive, leave originals
//   APPLY=1 DELETE=1 node archive-non-target.mjs  # copy then recursiveDelete originals
//
// Archive writes are idempotent (deterministic target paths, set with merge:false).
// Deletes are NOT — once gone, gone. Run APPLY=1 first, verify, then add DELETE=1.

import admin from 'firebase-admin';

const PROJECT_ID = 'crumbs-a4fdb';
const TARGET_UID = '6yPmdM14V3dPHLe3LO9XCfU4l9f1';
const APPLY = process.env.APPLY === '1';
const DELETE_AFTER_COPY = process.env.DELETE === '1';
const PAGE_SIZE = 200;
const BATCH_SIZE = 400;
// Firestore caps a single commit request at 10 MiB. Flush early to keep
// headroom; base64-blob docs in `mediaContent` can be ~1 MB each so the
// op-count limit alone isn't enough.
const BATCH_BYTE_LIMIT = 8 * 1024 * 1024;
const ARCHIVE_ROOT = 'archived';
const ITEMS_SEGMENT = 'items';

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: PROJECT_ID,
});
const db = admin.firestore();

const perCollection = new Map(); // rootCollId -> { copied, ghostsSkipped, deletedTrees }
let totalCopied = 0;
let totalGhostsSkipped = 0;
let totalTreesDeleted = 0;

function stat(id) {
  if (!perCollection.has(id)) perCollection.set(id, { copied: 0, ghostsSkipped: 0, deletedTrees: 0 });
  return perCollection.get(id);
}

function archivePathFor(originalPath) {
  const [rootColl, ...rest] = originalPath.split('/');
  return `${ARCHIVE_ROOT}/${rootColl}/${ITEMS_SEGMENT}/${rest.join('/')}`;
}

let batch = db.batch();
let opsInBatch = 0;
let bytesInBatch = 0;
let batchCommits = 0;

async function flushBatch(force = false) {
  if (opsInBatch === 0) return;
  if (!force && opsInBatch < BATCH_SIZE && bytesInBatch < BATCH_BYTE_LIMIT) return;
  await batch.commit();
  batchCommits++;
  process.stdout.write(`  committed batch ${batchCommits} (ops=${opsInBatch} bytes=${bytesInBatch} total copied=${totalCopied})\r`);
  batch = db.batch();
  opsInBatch = 0;
  bytesInBatch = 0;
}

function approxBytes(obj) {
  // Cheap upper-bound estimate for the serialized doc payload. Server-side
  // sentinels (FieldValue.serverTimestamp) serialize compactly so JSON length
  // overestimates them harmlessly.
  try { return Buffer.byteLength(JSON.stringify(obj), 'utf8'); }
  catch { return 1024; }
}

async function archiveDocRecursive(docRef, rootCollId) {
  const snap = await docRef.get();
  if (snap.exists) {
    const target = db.doc(archivePathFor(docRef.path));
    const enriched = {
      ...snap.data(),
      __archivedAt: admin.firestore.FieldValue.serverTimestamp(),
      __originalPath: docRef.path,
    };
    if (APPLY) {
      const docBytes = approxBytes(enriched);
      // Pre-flush if adding this doc would push us past the byte limit.
      if (opsInBatch > 0 && bytesInBatch + docBytes > BATCH_BYTE_LIMIT) {
        await flushBatch(true);
      }
      batch.set(target, enriched, { merge: false });
      opsInBatch++;
      bytesInBatch += docBytes;
      await flushBatch();
    } else if (stat(rootCollId).copied < 3) {
      console.log(`  [DRY] copy ${docRef.path} -> ${target.path}`);
    }
    stat(rootCollId).copied++;
    totalCopied++;
  } else {
    stat(rootCollId).ghostsSkipped++;
    totalGhostsSkipped++;
  }

  // listCollections returns every subcollection — works even when the parent
  // doc is a "missing document" (ghost) that only exists to hold subcollections.
  const subs = await docRef.listCollections();
  for (const sub of subs) {
    await archiveCollectionRecursive(sub, rootCollId);
  }
}

async function archiveCollectionRecursive(collRef, rootCollId) {
  // listDocuments returns refs for every doc in the collection, including ghosts
  // that only exist as parents of subcollections. Safe for arbitrary collection
  // sizes per Firestore Admin SDK docs.
  const refs = await collRef.listDocuments();
  if (refs.length === 0) return;
  console.log(`  walking ${collRef.path} (${refs.length} refs)`);
  for (const ref of refs) {
    await archiveDocRecursive(ref, rootCollId);
  }
}

async function recursiveDeleteIfRequested(ref, label) {
  if (!APPLY || !DELETE_AFTER_COPY) {
    if (!APPLY) console.log(`  [DRY] would recursiveDelete ${label}`);
    else console.log(`  skip delete (DELETE!=1) for ${label}`);
    return;
  }
  console.log(`  recursiveDelete ${label}`);
  await db.recursiveDelete(ref);
  totalTreesDeleted++;
}

async function processRootCollection(collRef) {
  const id = collRef.id;
  if (id === ARCHIVE_ROOT) {
    console.log(`\nSkipping '${ARCHIVE_ROOT}' (the archive itself).`);
    return;
  }

  console.log(`\n=== root collection: ${id} ===`);

  if (id === 'users') {
    const userDocs = await collRef.listDocuments();
    console.log(`  users/* has ${userDocs.length} doc refs`);
    for (const userRef of userDocs) {
      if (userRef.id === TARGET_UID) {
        console.log(`  keep users/${TARGET_UID} (target — left untouched)`);
        continue;
      }
      await archiveDocRecursive(userRef, 'users');
      await flushBatch(true);
      stat('users').deletedTrees++;
      await recursiveDeleteIfRequested(userRef, `users/${userRef.id}`);
    }
    return;
  }

  await archiveCollectionRecursive(collRef, id);
  await flushBatch(true);
  stat(id).deletedTrees++;
  await recursiveDeleteIfRequested(collRef, `entire root collection ${id}`);
}

async function main() {
  console.log(`Project: ${PROJECT_ID}`);
  console.log(`Target (kept intact): users/${TARGET_UID}/**`);
  const mode = APPLY
    ? (DELETE_AFTER_COPY ? 'APPLY + DELETE originals' : 'APPLY (copy only, no delete)')
    : 'DRY RUN (no writes, no deletes)';
  console.log(`Mode: ${mode}\n`);

  const rootCollections = await db.listCollections();
  console.log(`Discovered root collections: ${rootCollections.map((c) => c.id).join(', ') || '(none)'}`);

  for (const c of rootCollections) {
    await processRootCollection(c);
  }

  await flushBatch(true);

  console.log('\n\n=== Summary ===');
  for (const [id, s] of perCollection.entries()) {
    console.log(`  ${id.padEnd(20)} copied=${s.copied}  ghosts-skipped=${s.ghostsSkipped}  trees-deleted=${s.deletedTrees}`);
  }
  console.log(`  ----`);
  console.log(`  total copied=${totalCopied}  ghosts-skipped=${totalGhostsSkipped}  trees-deleted=${totalTreesDeleted}  batch-commits=${batchCommits}`);

  if (!APPLY) {
    console.log(`\nDRY RUN finished. Re-run with APPLY=1 to copy. Add DELETE=1 only after verifying the archive.`);
  } else if (!DELETE_AFTER_COPY) {
    console.log(`\nCopy phase complete. Originals are still in place. Verify the archive, then re-run with APPLY=1 DELETE=1 to remove originals.`);
  } else {
    console.log(`\nMove complete: originals removed, archive at '${ARCHIVE_ROOT}/<coll>/${ITEMS_SEGMENT}/...'.`);
  }
  process.exit(0);
}

main().catch((err) => {
  console.error('Archive failed:', err);
  process.exit(1);
});
