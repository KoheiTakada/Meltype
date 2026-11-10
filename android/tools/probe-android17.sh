#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 KoheiTakada
# Temporary diagnosis for the Android 17 emulator job. Not part of the PR.
# Usage: probe-android17.sh <label> [run-tests]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LABEL="${1:-probe}"
OUT="$ROOT/build/qa"; mkdir -p "$OUT"
LOG="$OUT/probe-$LABEL-logcat.txt"
run() { echo "+ $*"; "$@" 2>&1 | head -n "${LINES_MAX:-40}"; echo "  (exit ${PIPESTATUS[0]})"; }
echo "=== probe $LABEL"
run adb shell getprop ro.build.fingerprint
run adb shell "getprop | grep -iE 'egl|gles|gfxstream|vulkan|hwui|ro.hardware|gltransport|sf\\.' | head -n 30"
adb logcat -b all -v threadtime > "$LOG" 2>&1 &
LC=$!
if test "${PROBE_NAV:-}" = threebutton; then
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton && break
    sleep 3
  done
  run adb shell cmd overlay list com.android.internal.systemui.navbar.threebutton
fi
# Watch an idle system: no install, no launch.
echo "=== idle for 120 s"
sleep 120
sf_aborts() { grep -a -c 'Fatal signal.*(surfaceflinger)' "$LOG"; }
zygotes() { grep -a -c 'START com.android.internal.os.ZygoteInit' "$LOG"; }
echo "idle: surfaceflinger aborts=$(sf_aborts) zygote starts=$(zygotes)"
grep -a -E 'Fatal signal|Abort message|F DEBUG   :       #[0-9]{2}' "$LOG" | head -n 30
if test "${2:-}" = run-tests; then
  echo "=== run-instrumentation-tests.sh"
  bash "$ROOT/tools/run-instrumentation-tests.sh"
  echo "  (tests exit $?)"
  echo "after tests: surfaceflinger aborts=$(sf_aborts) zygote starts=$(zygotes)"
fi
kill "$LC" 2>/dev/null || true
echo '=== probe done'
