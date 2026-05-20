---
schema: sdlc/v1
type: index
slug: cloud-function-bookmark-sync
title: "Cloud Function bookmark sync (Option D from investigate-sync-architecture)"
status: active
current-stage: implement
stage-number: 5
created-at: "2026-05-19T11:39:20Z"
updated-at: "2026-05-20T22:20:01Z"
selected-slice: daily-poll
branch-strategy: shared
branch: "feat/brutalist-redesign"
base-branch: "main"
review-scope: slug-wide
pr-url: ""
pr-number: 0
open-questions:
  - "Maestro coverage strategy for the X authorize step on emulator (Custom Tab simulation vs mocked redirect) — owned by android-reader plan"
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
next-invocation: "/wf verify cloud-function-bookmark-sync daily-poll"
runtime-evidence-deferrals:
  - slice: auth-foundation
    reason: "Live Google sign-in + collision-link Maestro flow `sign_in_google.yaml` depends on navigation past LoginScreen owned by android-reader slice. Operator prereqs (Firebase Console provider enable + 3 SHA-1 registrations + Type-3 OAuth client in google-services.json + FIREBASE_WEB_OAUTH_CLIENT_ID env) are external manual steps."
    deferred-at: "2026-05-20T06:38:36Z"
    cleared-by: null
  - slice: functions-oauth
    reason: "Live evidence for AC-1, AC-2, AC-5, AC-6, AC-7 requires the 12-item Operator Checklist in 05-implement-functions-oauth.md (dedicated SA, Secret Manager seeds + per-secret IAM, X portal redirect_uri registration, Cloud Scheduler warmup job, `firebase deploy --only functions:crumb-oauth:*`, post-deploy curl + Cloud Logging cold/warm capture). AC-2 end-to-end additionally depends on the Custom Tab + deep-link flow owned by the android-reader slice. None executable from this verify session."
    deferred-at: "2026-05-20T18:57:07Z"
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
    status: implemented
    complexity: l
    depends-on: [functions-oauth]
  - slug: android-reader
    status: defined
    complexity: l
    depends-on: [auth-foundation, daily-poll]
  - slug: pending-delete
    status: defined
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
  - 04-plan.md
  - 04-plan-auth-foundation.md
  - 04-plan-functions-oauth.md
  - 04-plan-daily-poll.md
  - 05-implement.md
  - 05-implement-auth-foundation.md
  - 05-implement-functions-oauth.md
  - 05-implement-daily-poll.md
  - 06-verify.md
  - 06-verify-auth-foundation.md
  - 06-verify-functions-oauth.md
  - po-answers.md
progress:
  intake: complete
  shape: complete
  slice: complete   # 6 slices defined (auth-foundation, functions-oauth, daily-poll, android-reader, pending-delete, cutover-migration); sequential dependency chain
  plan: in-progress   # auth-foundation + functions-oauth + daily-poll planned; 3 slices remain to plan (android-reader, pending-delete, cutover-migration)
  implement: in-progress   # auth-foundation + functions-oauth + daily-poll implemented; 3 slices remain (android-reader, pending-delete, cutover-migration)
  verify: in-progress   # auth-foundation + functions-oauth verified (06-verify-*.md, both result: partial — runtime-evidence deferred); daily-poll awaiting verify; 3 slices remain after that
  review: not-started
  handoff: not-started
  ship: not-started
  retro: not-started
---
