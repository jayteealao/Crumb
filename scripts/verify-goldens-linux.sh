#!/usr/bin/env bash
# Verify Roborazzi goldens on Linux, or pre-flight the pull-request check.
#
#   scripts/verify-goldens-linux.sh [module ...] [-- gradle-arg ...]
#   scripts/verify-goldens-linux.sh --ci
#
# Default mode runs verifyRoborazziDebug for the given modules (default: every
# module that holds goldens). --ci runs the exact Gradle command from
# .github/workflows/pr_check.yml against the mirrored working tree.
source "$(dirname "$0")/goldens-linux-env.sh"

CI_TASKS=(clean assembleDebug lintDebug lintKotlin testDebugUnitTest verifyRoborazziDebug)

if [ "${1:-}" = "--ci" ]; then
  shift
  sync_mirror
  (cd "$MIRROR" && ./gradlew --no-daemon --console=plain :app:verifyCutoverDeletions "${CI_TASKS[@]}" "$@")
  echo "goldens: pull-request check command passed on Linux"
  exit 0
fi

modules=()
while [ $# -gt 0 ] && [ "$1" != "--" ]; do modules+=("$1"); shift; done
[ $# -gt 0 ] && shift
[ ${#modules[@]} -eq 0 ] && modules=("${DEFAULT_MODULES[@]}")

sync_mirror
tasks=()
for m in "${modules[@]}"; do tasks+=("$m:verifyRoborazziDebug"); done
(cd "$MIRROR" && ./gradlew --no-daemon --console=plain "${tasks[@]}" "$@")
echo "goldens: ${modules[*]} verified on Linux"
