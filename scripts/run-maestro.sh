#!/usr/bin/env bash
# run-maestro.sh — boot AVD, install debug APK, capture logs, run Maestro flows.
# Usage: bash scripts/run-maestro.sh [AVD_NAME]
set -euo pipefail

AVD="${1:-Medium_Phone_API_36}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

TS="$(date +%Y%m%d-%H%M%S)"
LOG_DIR="$REPO_ROOT/build/maestro-logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/$TS.log"

EMU_PID=""
LOG_PID=""

cleanup() {
    if [[ -n "$LOG_PID" ]] && kill -0 "$LOG_PID" 2>/dev/null; then
        kill "$LOG_PID" 2>/dev/null || true
    fi
    if [[ -n "$EMU_PID" ]] && kill -0 "$EMU_PID" 2>/dev/null; then
        adb emu kill >/dev/null 2>&1 || true
        kill "$EMU_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo "==> Booting AVD: $AVD"
emulator -avd "$AVD" -no-snapshot-save >/dev/null 2>&1 &
EMU_PID=$!

echo "==> Waiting for device"
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done;'

echo "==> Building debug APK"
if [[ -x ./gradlew ]]; then
    ./gradlew :app:assembleDebug
else
    ./gradlew.bat :app:assembleDebug
fi

echo "==> Installing APK"
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "==> Starting log capture -> $LOG_FILE"
lazylogcat logs dump --pkg com.github.jayteealao.crumbs > "$LOG_FILE" 2>&1 &
LOG_PID=$!

echo "==> Running Maestro flows"
set +e
maestro test \
    maestro/happy_path.yaml \
    maestro/long_press.yaml \
    maestro/filter_overlay.yaml \
    maestro/sync_error.yaml
MAESTRO_EXIT=$?
set -e

echo "==> Done. Log: $LOG_FILE"
exit "$MAESTRO_EXIT"
