#!/usr/bin/env bash
# Record Roborazzi goldens on Linux and copy them into the working tree.
#
#   scripts/record-goldens-linux.sh [module ...] [-- gradle-arg ...]
#
# Modules default to every module that holds goldens. Arguments after "--" go
# to Gradle unchanged, for example: -- --tests "*CardComponentsTest*".
source "$(dirname "$0")/goldens-linux-env.sh"

modules=()
while [ $# -gt 0 ] && [ "$1" != "--" ]; do modules+=("$1"); shift; done
[ $# -gt 0 ] && shift
[ ${#modules[@]} -eq 0 ] && modules=("${DEFAULT_MODULES[@]}")

sync_mirror
tasks=()
for m in "${modules[@]}"; do tasks+=("$m:recordRoborazziDebug"); done
(cd "$MIRROR" && ./gradlew --no-daemon --console=plain "${tasks[@]}" "$@")
for m in "${modules[@]}"; do copy_back_goldens "$m"; done
echo "goldens: recorded ${modules[*]} on Linux and copied back into $REPO_ROOT"
