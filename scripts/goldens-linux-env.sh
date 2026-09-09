#!/usr/bin/env bash
# Shared environment for the Linux golden scripts. Sourced, not executed.
#
# Roborazzi goldens are rendered by a per-OS native rasterizer, so the committed
# PNGs are the Linux set. This file wires the no-sudo WSL toolchain, mirrors the
# working tree to a Linux-native directory (Gradle on /mnt/c is slow and the
# Windows local.properties points at a Windows SDK), and exposes helpers.
#
# Override the defaults with CRUMB_TOOLS and CRUMB_LINUX_MIRROR.
set -euo pipefail

if [ "$(uname -s)" != "Linux" ]; then
  echo "goldens: run this from Linux (WSL Ubuntu-24.04); Windows-rendered goldens do not match CI." >&2
  exit 2
fi

CRUMB_TOOLS="${CRUMB_TOOLS:-$HOME/crumb-tools}"
export JAVA_HOME="${JAVA_HOME_OVERRIDE:-$CRUMB_TOOLS/jdk17}"
export ANDROID_HOME="${ANDROID_HOME_OVERRIDE:-$CRUMB_TOOLS/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIRROR="${CRUMB_LINUX_MIRROR:-$HOME/crumb-record}"
DEFAULT_MODULES=(":core:designsystem" ":app" ":feature:reddit" ":feature:twitter")

# Copy the working tree (not just HEAD) into the mirror. Whatever Git ignores
# stays out, so the exclusion list is computed from .gitignore at run time
# rather than repeated here. The mirror keeps its own build outputs and Gradle
# project cache between runs for incremental builds.
sync_mirror() {
  mkdir -p "$MIRROR"
  local ignored
  ignored="$(mktemp)"
  (cd "$REPO_ROOT" && git -c core.quotepath=off ls-files --others --ignored --exclude-standard --directory) \
    | sed 's#^#/#' > "$ignored"
  rsync -a --delete --exclude-from "$ignored" \
    --exclude '.git/' --exclude '.claude/' --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' \
    "$REPO_ROOT/" "$MIRROR/"
  rm -f "$ignored"
  {
    echo "sdk.dir=$ANDROID_HOME"
    [ -f "$REPO_ROOT/local.properties" ] && grep -E '^(twitter|firebase)\.' "$REPO_ROOT/local.properties" || true
  } > "$MIRROR/local.properties"
  sed -i 's/\r$//' "$MIRROR/gradlew"
  chmod +x "$MIRROR/gradlew"
}

# ":core:designsystem" -> "core/designsystem"
module_dir() { local m="${1#:}"; echo "${m//:/\/}"; }

# Copy a module's recorded goldens from the mirror back into the working tree.
copy_back_goldens() {
  local dir; dir="$(module_dir "$1")"
  rsync -a --delete "$MIRROR/$dir/src/test/screenshots/" "$REPO_ROOT/$dir/src/test/screenshots/"
}
