#!/usr/bin/env bash
#
# verify-function-iam.sh — Least-privilege IAM audit for the Cloud Functions
# that handle X (Twitter) bookmarks.
#
# Asserts (exit non-zero on first failure):
#   1. The runtime service account on every deployed function in the
#      `crumb-oauth` codebase equals the dedicated SA — NOT the default App
#      Engine SA.
#   2. The dedicated SA holds `roles/secretmanager.secretAccessor` on each
#      relevant secret, scoped per-secret (not project-level).
#   3. The dedicated SA holds NO project-level Firestore roles
#      (datastore.user / datastore.owner / datastore.writer). The Admin SDK
#      bypass is the write mechanism; project-level roles would be redundant
#      and dangerous.
#
# Usage:
#   bash scripts/verify-function-iam.sh [uid]
#
# Defaults: uid = 6yPmdM14V3dPHLe3LO9XCfU4l9f1
# Requires: gcloud CLI authenticated, jq, bash 4+. Run via WSL or Git Bash on
# Windows; native Linux/macOS works as-is.

set -euo pipefail

PROJECT_ID="crumbs-a4fdb"
SA_EMAIL="crumb-twitter-poller@${PROJECT_ID}.iam.gserviceaccount.com"
UID_ARG="${1:-6yPmdM14V3dPHLe3LO9XCfU4l9f1}"

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "PASS: $*"; }

echo "==> Verifying function runtime service account on each deployed function"
for FN in dailyPoll triggerPoll oauthCallback mintOAuthState warmUp; do
  RUNTIME_SA=$(gcloud functions describe "$FN" \
    --gen2 \
    --region=europe-west2 \
    --project="$PROJECT_ID" \
    --format='value(serviceConfig.serviceAccountEmail)' 2>/dev/null || echo "")
  if [[ -z "$RUNTIME_SA" ]]; then
    fail "$FN is not deployed (or describe failed)"
  fi
  if [[ "$RUNTIME_SA" != "$SA_EMAIL" ]]; then
    fail "$FN runtime SA is '$RUNTIME_SA', expected '$SA_EMAIL'"
  fi
  pass "$FN runtime SA -> $SA_EMAIL"
done

echo
echo "==> Verifying per-secret secretAccessor bindings on each required secret"
for SECRET in \
  "crumb-x-refresh-token-${UID_ARG}" \
  "crumb-oauth-state-secret" \
  "crumb-x-client-id" \
  "crumb-x-client-secret"; do
  gcloud secrets get-iam-policy "$SECRET" \
    --project="$PROJECT_ID" \
    --format=json \
    | jq -e --arg sa "serviceAccount:$SA_EMAIL" '
        .bindings[]
        | select(.role == "roles/secretmanager.secretAccessor")
        | .members[]
        | select(. == $sa)
      ' >/dev/null \
    || fail "$SECRET missing roles/secretmanager.secretAccessor for $SA_EMAIL"
  pass "$SECRET secretAccessor -> $SA_EMAIL"
done

echo
echo "==> Verifying NO project-level Firestore roles on the SA"
BAD=$(gcloud projects get-iam-policy "$PROJECT_ID" --format=json \
  | jq --arg sa "serviceAccount:$SA_EMAIL" '
      [.bindings[]
       | select(.role == "roles/datastore.user"
             or .role == "roles/datastore.owner"
             or .role == "roles/datastore.writer")
       | select(.members[]? == $sa)
       | .role]
    ')
if [[ "$BAD" != "[]" ]]; then
  fail "SA has unexpected Firestore roles at project level: $BAD"
fi
pass "SA has no project-level Firestore roles"

echo
echo "==> ALL CHECKS PASSED"
