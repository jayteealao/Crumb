// One-shot backfill: copy the snake_case fields the server-side poll wrote
// into camelCase aliases the Android FirestoreRepository / FirestoreModels
// readers require. Idempotent — re-running on a doc that already has the
// aliases is a no-op write (Firestore .update is delta-only).
//
// Scope:
//   users/{uid}/tweets/*           → tweetId, authorId, createdAt,
//                                    conversationId, inReplyToUserId
//   users/{uid}/metrics/*          → tweetId, likeCount, retweetCount,
//                                    replyCount, quoteCount, bookmarkCount,
//                                    impressionCount
//   users/{uid}/twitter_users/*    → userId, profileImageUrl, verifiedType,
//                                    pinnedTweetId, createdAt,
//                                    followersCount, followingCount,
//                                    tweetCount, listedCount
//   users/{uid}/media/*            → mediaKey, previewImageUrl, durationMs,
//                                    altText
//   users/{uid}/includes/*         → referencedTweetId (only for docs whose
//                                    kind === "referenced_tweet" and which
//                                    were written with the legacy referencedId
//                                    field but no camelCase alias)
//
// Usage (run from functions/ with ADC auth):
//   node scripts/backfill-camelcase-aliases.mjs --project crumbs-a4fdb
//   node scripts/backfill-camelcase-aliases.mjs --project crumbs-a4fdb --uid 6yPmdM14V3dPHLe3LO9XCfU4l9f1
//   node scripts/backfill-camelcase-aliases.mjs --project crumbs-a4fdb --dry-run

import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

const args = process.argv.slice(2);
function flag(name) {
  const idx = args.indexOf(`--${name}`);
  if (idx < 0) return undefined;
  return args[idx + 1] && !args[idx + 1].startsWith("--")
    ? args[idx + 1]
    : true;
}

const projectId = flag("project") || process.env.GCLOUD_PROJECT;
if (!projectId) {
  console.error("missing --project or GCLOUD_PROJECT");
  process.exit(2);
}
const onlyUid = flag("uid");
const dryRun = !!flag("dry-run");
const BATCH_SIZE = 400;

initializeApp({ credential: applicationDefault(), projectId });
const db = getFirestore();

function pick(o, k) {
  return o && Object.prototype.hasOwnProperty.call(o, k) ? o[k] : undefined;
}

function tweetPatch(d) {
  const patch = {};
  if (d.tweetId === undefined && d.id !== undefined) patch.tweetId = d.id;
  if (d.authorId === undefined && d.author_id !== undefined)
    patch.authorId = d.author_id;
  if (d.createdAt === undefined && d.created_at !== undefined)
    patch.createdAt = d.created_at;
  if (d.conversationId === undefined && d.conversation_id !== undefined)
    patch.conversationId = d.conversation_id;
  if (d.inReplyToUserId === undefined && d.in_reply_to_user_id !== undefined)
    patch.inReplyToUserId = d.in_reply_to_user_id;
  return patch;
}

function metricsPatch(d, docId) {
  const patch = {};
  if (d.tweetId === undefined) patch.tweetId = docId;
  if (d.likeCount === undefined && d.like_count !== undefined)
    patch.likeCount = d.like_count;
  if (d.retweetCount === undefined && d.retweet_count !== undefined)
    patch.retweetCount = d.retweet_count;
  if (d.replyCount === undefined && d.reply_count !== undefined)
    patch.replyCount = d.reply_count;
  if (d.quoteCount === undefined && d.quote_count !== undefined)
    patch.quoteCount = d.quote_count;
  if (d.bookmarkCount === undefined && d.bookmark_count !== undefined)
    patch.bookmarkCount = d.bookmark_count;
  if (d.impressionCount === undefined && d.impression_count !== undefined)
    patch.impressionCount = d.impression_count;
  return patch;
}

function userPatch(d) {
  const patch = {};
  if (d.userId === undefined && d.id !== undefined) patch.userId = d.id;
  if (d.profileImageUrl === undefined && d.profile_image_url !== undefined)
    patch.profileImageUrl = d.profile_image_url;
  if (d.verifiedType === undefined && d.verified_type !== undefined)
    patch.verifiedType = d.verified_type;
  if (d.pinnedTweetId === undefined && d.pinned_tweet_id !== undefined)
    patch.pinnedTweetId = d.pinned_tweet_id;
  if (d.createdAt === undefined && d.created_at !== undefined)
    patch.createdAt = d.created_at;
  const pm = d.public_metrics || {};
  if (d.followersCount === undefined && pm.followers_count !== undefined)
    patch.followersCount = pm.followers_count;
  if (d.followingCount === undefined && pm.following_count !== undefined)
    patch.followingCount = pm.following_count;
  if (d.tweetCount === undefined && pm.tweet_count !== undefined)
    patch.tweetCount = pm.tweet_count;
  if (d.listedCount === undefined && pm.listed_count !== undefined)
    patch.listedCount = pm.listed_count;
  return patch;
}

function mediaPatch(d) {
  const patch = {};
  if (d.mediaKey === undefined && d.media_key !== undefined)
    patch.mediaKey = d.media_key;
  if (d.previewImageUrl === undefined && d.preview_image_url !== undefined)
    patch.previewImageUrl = d.preview_image_url;
  if (d.durationMs === undefined && d.duration_ms !== undefined)
    patch.durationMs = d.duration_ms;
  if (d.altText === undefined && d.alt_text !== undefined)
    patch.altText = d.alt_text;
  return patch;
}

function includesPatch(d) {
  const patch = {};
  if (
    d.kind === "referenced_tweet" &&
    d.referencedTweetId === undefined &&
    d.referencedId !== undefined
  ) {
    patch.referencedTweetId = d.referencedId;
  }
  return patch;
}

async function processCollection(uid, collectionName, patchFn) {
  const colRef = db.collection(`users/${uid}/${collectionName}`);
  let scanned = 0;
  let touched = 0;
  let lastDoc = null;
  for (;;) {
    let q = colRef.orderBy("__name__").limit(BATCH_SIZE);
    if (lastDoc) q = q.startAfter(lastDoc);
    const snap = await q.get();
    if (snap.empty) break;
    const batch = db.batch();
    let writesInBatch = 0;
    for (const doc of snap.docs) {
      scanned++;
      const patch = patchFn(doc.data(), doc.id);
      if (Object.keys(patch).length > 0) {
        if (!dryRun) {
          batch.set(doc.ref, patch, { merge: true });
          writesInBatch++;
        }
        touched++;
      }
    }
    if (writesInBatch > 0) await batch.commit();
    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.size < BATCH_SIZE) break;
  }
  return { scanned, touched };
}

async function processUid(uid) {
  console.log(`\n[uid ${uid}]`);
  const tweets = await processCollection(uid, "tweets", tweetPatch);
  console.log(
    `  tweets:        scanned=${tweets.scanned} touched=${tweets.touched}`,
  );
  const metrics = await processCollection(uid, "metrics", metricsPatch);
  console.log(
    `  metrics:       scanned=${metrics.scanned} touched=${metrics.touched}`,
  );
  const users = await processCollection(uid, "twitter_users", userPatch);
  console.log(
    `  twitter_users: scanned=${users.scanned} touched=${users.touched}`,
  );
  const includes = await processCollection(uid, "includes", includesPatch);
  console.log(
    `  includes:      scanned=${includes.scanned} touched=${includes.touched}`,
  );
  const media = await processCollection(uid, "media", mediaPatch);
  console.log(
    `  media:         scanned=${media.scanned} touched=${media.touched}`,
  );
}

async function main() {
  console.log(
    `backfill-camelcase-aliases: project=${projectId} uid=${onlyUid || "(all)"} dryRun=${dryRun}`,
  );
  if (onlyUid) {
    await processUid(onlyUid);
  } else {
    const usersSnap = await db.collection("users").get();
    for (const u of usersSnap.docs) {
      await processUid(u.id);
    }
  }
  console.log("\ndone.");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
