// Link-preview coverage measurement + backfill over the EXISTING tweet corpus.
//
// Reuses the exact production code path (lib/lib/enrich-links runEnrichLinks +
// lib/lib/og fetchOpenGraph) so the measured outcome distribution matches what
// the deployed enrichTweetLinks trigger / backfillTweetLinks callable produce.
// The user-scoped callable cannot be invoked headlessly, so this Admin SDK
// script is the headless equivalent (same pattern as
// scripts/backfill-camelcase-aliases.mjs).
//
// Crash-tolerant: the local Node + undici stack can throw an uncatchable
// assertion (assert(!this.paused)) on socket-end after a capped/aborted body
// read at scale. We checkpoint the page cursor to --state BEFORE processing a
// page, so an uncaught crash resumes past the offending page (forward progress
// guaranteed) rather than looping. Authoritative AC1 evidence is the persisted
// Firestore re-read (see --verify-only), which makes no outbound fetches.
//
// Modes:
//   --dry-run        read corpus, run new OG logic, tally, WRITE NOTHING.
//   --force          persist enriched url docs to Firestore (idempotent).
//   --verify-only    no fetches; count persisted url docs with a title.
//
// "has-preview %" = (rich + title_only) / total-with-link  (AC1 target >= 75%).
//
// Usage (run from functions/ with ADC auth):
//   node scripts/measure-link-previews.mjs --project crumbs-a4fdb --uid <uid> --force --resume --state <path>
//   node scripts/measure-link-previews.mjs --project crumbs-a4fdb --uid <uid> --verify-only

import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getFirestore, FieldPath } from "firebase-admin/firestore";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { runEnrichLinks } from "../lib/lib/enrich-links.js";
import { fetchOpenGraph } from "../lib/lib/og.js";

const args = process.argv.slice(2);
function flag(name) {
  const i = args.indexOf(`--${name}`);
  if (i < 0) return undefined;
  return args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : true;
}

const projectId = flag("project") || process.env.GCLOUD_PROJECT;
if (!projectId) { console.error("missing --project or GCLOUD_PROJECT"); process.exit(2); }
const onlyUid = flag("uid");
const dryRun = !!flag("dry-run");
const verifyOnly = !!flag("verify-only");
const force = !!flag("force") || dryRun;
const resume = !!flag("resume");
const statePath = flag("state") || null;
const limit = flag("limit") ? Number(flag("limit")) : Infinity;
const CONCURRENCY = flag("concurrency") ? Number(flag("concurrency")) : 8;
// --vendor-cap N: allow up to N iframely vendor calls per run (default 0 =
// vendor branch disabled, preserving prior behaviour). The cap is a shared
// mutable object so it counts down globally across the entire uid pass.
const VENDOR_CAP_INITIAL = flag("vendor-cap") ? Number(flag("vendor-cap")) : 0;
const PAGE_SIZE = 200;

initializeApp({ credential: applicationDefault(), projectId });
const realDb = getFirestore();
const noopDb = { doc: () => ({ get: async () => ({ exists: false }), set: async () => undefined }) };

// ---- persisted state (resume) ----
let state = { tally: {}, cursor: null, scanned: 0, withLink: 0, noLink: 0, written: 0, skipped: 0, skippedPages: [], done: false };
if (resume && statePath && existsSync(statePath)) {
  try { state = { ...state, ...JSON.parse(readFileSync(statePath, "utf8")) }; console.log(`resumed from ${statePath}: scanned=${state.scanned} cursor=${state.cursor}`); }
  catch (e) { console.error("state read failed, starting fresh:", e.message); }
}
function saveState() { if (statePath) writeFileSync(statePath, JSON.stringify(state)); }

const samples = [];
const countingLog = { info: (msg, f) => {
  if (msg === "enrich_link_outcome" && f && f.outcome) {
    state.tally[f.outcome] = (state.tally[f.outcome] || 0) + 1;
    if (samples.length < 12) samples.push({ tweetId: f.tweetId, outcome: f.outcome, hasTitle: f.hasTitle, hasImage: f.hasImage });
  }
}};

// crash handlers: state.cursor already points at the page-end checkpoint, so a
// resume skips the offending page. Flush, then exit 0 so the wrapper re-runs.
function onCrash(kind, err) { console.error(`\n[${kind}] ${err && err.message ? err.message : err} — checkpoint at cursor=${state.cursor}, exiting for resume`); saveState(); process.exit(0); }
process.on("uncaughtException", (e) => onCrash("uncaughtException", e));
process.on("unhandledRejection", (e) => onCrash("unhandledRejection", e));

async function processTweet(uid, doc, sharedVendorCap) {
  const data = doc.data() || {};
  const entities = data.entities;
  if (!entities) { state.noLink++; return; }
  const db = dryRun ? noopDb : realDb;
  try {
    // Pass the shared cap object so fetchOpenGraph can call the iframely vendor
    // path. The cap object is shared across the whole uid pass so total vendor
    // calls stay within the --vendor-cap bound.
    const ogFetch = (url) => fetchOpenGraph(url, undefined, sharedVendorCap);
    const outcome = await runEnrichLinks(db, uid, doc.id, entities, ogFetch, { force, log: countingLog });
    if (outcome === "no_link") state.noLink++;
    else { state.withLink++; if (outcome === "written") state.written++; else if (outcome === "skipped") state.skipped++; }
  } catch (e) {
    state.tally["error"] = (state.tally["error"] || 0) + 1; state.withLink++;
  }
}

async function processUid(uid) {
  console.log(`\n[uid ${uid}]`);
  // One shared cap object per uid pass so the VENDOR_CAP_INITIAL budget is
  // consumed globally across all tweets in the pass (not reset per tweet).
  const sharedVendorCap = { remaining: VENDOR_CAP_INITIAL };
  const col = realDb.collection(`users/${uid}/tweets`);
  let lastDoc = state.cursor ? await col.doc(state.cursor).get() : undefined;
  while (state.scanned < limit) {
    let q = col.orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
    if (lastDoc) q = q.startAfter(lastDoc);
    const snap = await q.get();
    if (snap.empty) { state.done = true; break; }
    // checkpoint BEFORE processing: on crash, resume skips this page.
    const pageStart = snap.docs[0].id;
    state.cursor = snap.docs[snap.docs.length - 1].id;
    saveState();
    const docs = snap.docs.slice(0, Math.max(0, limit - state.scanned));
    let idx = 0, crashedPage = false;
    async function worker() { while (idx < docs.length) { const d = docs[idx++]; await processTweet(uid, d, sharedVendorCap); } }
    try { await Promise.all(Array.from({ length: CONCURRENCY }, worker)); }
    catch { crashedPage = true; state.skippedPages.push(pageStart); }
    state.scanned += docs.length;
    saveState();
    process.stdout.write(`  scanned ${state.scanned} (with-link ${state.withLink}, no-link ${state.noLink})${crashedPage ? " [page skipped]" : ""}\r`);
    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.docs.length < PAGE_SIZE) { state.done = true; break; }
  }
  console.log(`\n  uid done: scanned=${state.scanned} done=${state.done} vendor_calls_used=${VENDOR_CAP_INITIAL - sharedVendorCap.remaining}`);
}

async function verifyPersisted(uid) {
  const col = realDb.collection(`users/${uid}/textAnnotations`);
  let last, total = 0, urlDocs = 0, withTitle = 0, withImage = 0;
  for (;;) {
    let q = col.orderBy(FieldPath.documentId()).limit(500);
    if (last) q = q.startAfter(last);
    const s = await q.get(); if (s.empty) break;
    for (const d of s.docs) { const x = d.data(); if (x.type === "urls" || d.id.includes("_urls_")) { urlDocs++; if (x.title) withTitle++; if (x.imageUrl) withImage++; } total++; }
    last = s.docs[s.docs.length - 1]; if (s.docs.length < 500) break;
  }
  const pct = urlDocs ? (withTitle / urlDocs * 100).toFixed(1) : "0.0";
  console.log(`\n===== PERSISTED (Firestore re-read) =====`);
  console.log(`  textAnnotation docs: ${total} | url docs: ${urlDocs} | with title: ${withTitle} | with image: ${withImage}`);
  console.log(`  has-preview (title present) / url docs = ${withTitle}/${urlDocs} = ${pct}%   [AC1 target >= 75%]`);
  console.log(`  AC1: ${Number(pct) >= 75 ? "MET ✓" : "NOT MET ✗"}`);
  return { urlDocs, withTitle, withImage, pct };
}

function report() {
  const t = state.tally;
  const total = Object.values(t).reduce((a, b) => a + b, 0);
  const has = (t.rich || 0) + (t.title_only || 0);
  const pct = total ? (has / total * 100).toFixed(1) : "0.0";
  console.log(`\n===== outcome histogram (mode: ${dryRun ? "DRY-RUN" : "FORCE WRITE"}) =====`);
  const order = ["rich", "title_only", "image_only", "no_meta", "redirect_blocked", "too_many_redirects", "http_4xx", "non_html", "timeout", "unsafe_url", "error"];
  for (const k of order) if (t[k]) console.log(`  ${k.padEnd(20)} ${t[k]}`);
  console.log(`  ${"-".repeat(28)}`);
  console.log(`  total-with-link        ${total}`);
  console.log(`  no-link tweets         ${state.noLink}`);
  if (!dryRun) console.log(`  written=${state.written} skipped=${state.skipped} skippedPages=${state.skippedPages.length}`);
  console.log(`  has-preview (rich+title_only)/total = ${has}/${total} = ${pct}%   [AC1 target >= 75%]`);
  console.log(`  AC1 (fetch tally): ${Number(pct) >= 75 ? "MET ✓" : "NOT MET ✗"}`);
}

async function main() {
  console.log(`measure-link-previews: project=${projectId} uid=${onlyUid || "(all)"} mode=${verifyOnly ? "verify-only" : dryRun ? "dry-run" : "force"} resume=${resume} concurrency=${CONCURRENCY} limit=${limit === Infinity ? "all" : limit} vendor-cap=${VENDOR_CAP_INITIAL}`);
  const started = new Date().toISOString();
  const uids = onlyUid ? [onlyUid] : (await realDb.collection("users").listDocuments()).map((r) => r.id);
  if (verifyOnly) { for (const u of uids) await verifyPersisted(u); console.log(`\nstarted=${started} finished=${new Date().toISOString()}`); return; }
  for (const u of uids) { if (limit !== Infinity && (state.withLink + state.noLink) >= limit) break; await processUid(u); }
  report();
  if (samples.length) { console.log(`\n  sample outcomes:`); for (const s of samples) console.log(`    ${s.tweetId}  ${s.outcome}  title=${s.hasTitle} image=${s.hasImage}`); }
  console.log(`\nstarted=${started} finished=${new Date().toISOString()} done=${state.done}`);
}

main().catch((e) => { console.error(e); saveState(); process.exit(0); });
