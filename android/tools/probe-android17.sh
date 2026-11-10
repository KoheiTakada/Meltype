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
run adb shell getprop ro.boot.qemu.gltransport.name
# Keep recording across system restarts; reconnect from the newest line.
( adb logcat -b all -v threadtime; while :; do sleep 1; adb logcat -b all -v threadtime -T 1; done ) > "$LOG" 2>&1 &
if test "${PROBE_NAV:-}" = threebutton; then
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton && break
    sleep 3
  done
  run adb shell cmd overlay list com.android.internal.systemui.navbar.threebutton
fi
echo "=== idle for 120 s"
sleep 120
sf_aborts() { grep -a -c 'Fatal signal.*(surfaceflinger)' "$LOG"; }
zygotes() { grep -a -c 'START com.android.internal.os.ZygoteInit' "$LOG"; }
echo "idle: surfaceflinger aborts=$(sf_aborts) zygote starts=$(zygotes)"
grep -a -E 'Fatal signal|Abort message' "$LOG" | head -n 6
if test "${2:-}" = run-tests; then
  echo "=== run-instrumentation-tests.sh"
  bash "$ROOT/tools/run-instrumentation-tests.sh"
  echo "  (tests exit $?)"
  sleep 5
  echo "after tests: surfaceflinger aborts=$(sf_aborts) zygote starts=$(zygotes)"
  echo '--- fatal lines (whole run)'
  grep -a -E 'Fatal signal|Abort message|FATAL EXCEPTION|\*\*\* FATAL|Watchdog: \*\*\*|WATCHDOG KILLING|F DEBUG   : Cmdline|AndroidRuntime: (Process|java\.|	at |Caused)' "$LOG" | grep -a -v 'hasReadColorBufferDma' | head -n 80
  echo '--- crash buffer'
  adb logcat -d -b crash 2>&1 | tail -n 60
  test -f "$ROOT/build/qa/summary.md" && cat "$ROOT/build/qa/summary.md"
fi
pkill -f 'adb logcat' 2>/dev/null || true
echo '=== probe done'
