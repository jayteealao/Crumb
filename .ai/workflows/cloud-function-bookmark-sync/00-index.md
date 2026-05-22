---
schema: sdlc/v1
type: index
slug: cloud-function-bookmark-sync
title: "Cloud Function bookmark sync (Option D from investigate-sync-architecture)"
status: active
current-stage: implement
stage-number: 5
created-at: "2026-05-19T11:39:20Z"
updated-at: "2026-05-22T20:14:35Z"
selected-slice: pending-delete
branch-strategy: shared
branch: "feat/brutalist-redesign"
base-branch: "main"
review-scope: slug-wide
pr-url: ""
pr-number: 0
open-questions: []
tags: [sync, cloud-functions, firestore, twitter, server-side, oauth]
stack:
  detected-at: "2026-05-19T11:39:20Z"
  platforms: [android, service]
  languages: [kotlin, typescript]
  ui: [compose]
  build: [gradle, firebase-cli]
  package-managers: [gradle, npm]
  testing: [junit, roborazzi, maestro, jest]
  observability: [lazylogcat]
  integrations: [hilt, room, paging, firestore, datastore, firebase-auth, cloud-functions, cloud-scheduler, secret-manager]
  available-skills:
    - {name: sdlc-workflow, hint: "SDLC lifecycle stage dispatcher (this workflow)"}
    - {name: web-search-prime, hint: "Freshness research for Cloud Functions / X API / Firebase pricing"}
    - {name: webReader, hint: "Read external docs verbatim"}
    - {name: zread, hint: "Cross-repo source-of-truth lookups (Firebase SDK, Cloud Functions samples)"}
    - {name: ccd_session, hint: "Session/chapter management"}
  available-cli:
    - {name: firebase, hint: "Firebase CLI (auth, deploy --only functions, firestore:rules, etc.)"}
    - {name: gcloud, hint: "Google Cloud CLI (Identity Platform, Secret Manager, Scheduler, IAM)"}
    - {name: android, hint: "Android CLI for project + SDK orchestration"}
    - {name: lazylogcat, hint: "Non-interactive logcat capture and filter"}
    - {name: maestro, hint: "UI flow automation (existing flows under maestro/)"}
  available-mcp:
    - {name: web-search-prime, hint: "Web search"}
    - {name: web-reader, hint: "URL → markdown"}
    - {name: zai-mcp-server, hint: "Screenshot/diagram analysis for client UX changes"}
    - {name: scheduled-tasks, hint: "OS-level scheduled tasks (NOT Cloud Scheduler)"}
  user-confirmed: true
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync pending-delete"
runtime-evidence-deferrals:
  - slice: auth-foundation
    reason: "Live Google sign-in + collision-link Maestro flow `sign_in_google.yaml` depends on navigation past LoginScreen owned by android-reader slice. Operator prereqs (Firebase Console provider enable + 3 SHA-1 registrations + Type-3 OAuth client in google-services.json + FIREBASE_WEB_OAUTH_CLIENT_ID env) are external manual steps."
    deferred-at: "2026-05-20T06:38:36Z"
    cleared-by: null
  - slice: functions-oauth
    reason: "Live evidence for AC-1, AC-2, AC-5, AC-6, AC-7 originally deferred behind a 12-item operator checklist. Operator checklist executed during the daily-poll verify on 2026-05-22 (Tier 0-2: APIs enabled, SA + bindings created, secrets seeded, redirect_uri registered, functions deployed, warmup scheduler created, invokers granted, IAM verifier ALL PASS live). Functions-oauth's AC-1/AC-5/AC-6/AC-7 are now indirectly proven by the live 5-function deploy + verify-function-iam.sh exit-0; AC-2 (end-to-end OAuth round-trip) is also proven via the local-redirect bootstrap and the curl-based handshake against the deployed oauthCallback (302 -> crumbs://x-oauth-complete + RT in Secret Manager + sync_status linked:true). Remaining clearing event: an `android-reader` Custom Tab + deep-link round-trip via the production Android client, captured via /wf-quick probe."
    deferred-at: "2026-05-20T18:57:07Z"
    cleared-by: null
  - slice: poll-correctness
    reason: "AC7-server pending_delete round-trip (un-bookmark a tweet in X.com -> poll -> doc transitions to pending_delete:true -> re-bookmark -> poll -> doc transitions to pending_delete:false) requires a manual interaction in the X.com web/app UI. CLI-driven operator commands cannot drive the X bookmark toggle. The code path is proven by jest test (l) (chunked `in` precondition reads + deletedSet skip) and the production poll's `itemsFlaggedPendingDelete: 0` is internally consistent with stop-on-overlap firing on the correctly-computed BigInt-max boundary. Clearing event: /wf-quick probe after operator manually toggles bookmark state on a sample tweet, confirms doc transitions through pending_delete:true -> false."
    deferred-at: "2026-05-22T13:45:36Z"
    cleared-by: null
  - slice: android-reader
    reason: "Live device + emulator + jayteealao@gmail.com Google account + redeployed mintOAuthState/oauthCallback + live X account round-trip required for AC1 (Maestro sign_in_google.yaml + UID capture), AC2 (Maestro connect_x_blocking.yaml), AC2-live (manual Custom Tab + deep-link round-trip + Cloud Logging oauth_callback_linked -> daily_poll_completed capture), AC5 (Maestro pull_to_refresh.yaml + lazylogcat triggerPoll capture), AC8 (Maestro reconnect_banner.yaml), and NFR (cold/warm Firestore one-shot + triggerPoll round-trip timing samples). All seven automated checks pass green at commit cd107da (jest 29/29, Android lint clean, Robolectric green, Roborazzi green vs. 7 PNG references, assembleDebug clean). Clearing event: /wf-quick probe captures evidence under verify-evidence/android-reader/ after operator runs `firebase deploy --only functions:crumb-oauth:mintOAuthState,functions:crumb-oauth:oauthCallback` and executes the four Maestro flows + manual Custom Tab cycle. The same probe pass also clears the auth-foundation and functions-oauth deferrals (they share the same live OAuth + bookmarks session)."
    deferred-at: "2026-05-22T19:17:44Z"
    cleared-by: null
slices:
  - slug: auth-foundation
    status: implemented
    complexity: m
    depends-on: []
  - slug: functions-oauth
    status: implemented
    complexity: l
    depends-on: [auth-foundation]
  - slug: daily-poll
    status: verified-escalated
    complexity: l
    depends-on: [functions-oauth]
  - slug: poll-correctness
    status: verified
    complexity: m
    depends-on: [daily-poll]
    source: extension
    extension-round: 1
  - slug: android-reader
    status: verified
    complexity: l
    depends-on: [auth-foundation, daily-poll, poll-correctness]
  - slug: pending-delete
    status: implemented
    complexity: m
    depends-on: [android-reader]
  - slug: cutover-migration
    status: defined
    complexity: m
    depends-on: [pending-delete]
workflow-files:
  - 00-index.md
  - 01-intake.md
  - 02-shape.md
  - 03-slice.md
  - 03-slice-auth-foundation.md
  - 03-slice-functions-oauth.md
  - 03-slice-daily-poll.md
  - 03-slice-android-reader.md
  - 03-slice-pending-delete.md
  - 03-slice-cutover-migration.md
  - 03-slice-poll-correctness.md
  - 04-plan.md
  - 04-plan-auth-foundation.md
  - 04-plan-functions-oauth.md
  - 04-plan-daily-poll.md
  - 04-plan-poll-correctness.md
  - 04-plan-android-reader.md
  - 04-plan-pending-delete.md
  - 05-implement.md
  - 05-implement-auth-foundation.md
  - 05-implement-functions-oauth.md
  - 05-implement-daily-poll.md
  - 05-implement-poll-correctness.md
  - 05-implement-android-reader.md
  - 05-implement-pending-delete.md
  - 06-verify-poll-correctness.md
  - 06-verify.md
  - 06-verify-auth-foundation.md
  - 06-verify-functions-oauth.md
  - 06-verify-daily-poll.md
  - 06-verify-android-reader.md
  - po-answers.md
progress:
  intake: complete
  shape: complete
  slice: complete   # 7 slices defined (auth-foundation, functions-oauth, daily-poll, poll-correctness, android-reader, pending-delete, cutover-migration); sequential dependency chain
  plan: in-progress   # auth-foundation + functions-oauth + daily-poll + poll-correctness + android-reader + pending-delete planned (6/7); 1 slice remains to plan (cutover-migration)
  implement: in-progress   # auth-foundation + functions-oauth + daily-poll + poll-correctness + android-reader + pending-delete implemented (6/7); 1 slice remains (cutover-migration)
  verify: in-progress   # auth-foundation + functions-oauth + daily-poll + poll-correctness + android-reader verified (5/7); auth-foundation + functions-oauth + android-reader result: partial (runtime-evidence deferred — all three clear via a single /wf-quick probe operator session that runs the four Maestro flows + manual Custom Tab + deep-link cycle); daily-poll result: partial + convergence: escalated (4 defects surfaced and substantively closed by poll-correctness); poll-correctness result: partial + convergence: not-needed (8/9 AC met live; 1 AC deferred for X.com UI interaction). 2 slices remain.
  review: not-started
  handoff: not-started
  ship: not-started
  retro: not-started
---
