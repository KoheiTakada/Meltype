#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 KoheiTakada
# Temporary diagnosis for the Android 17 emulator job. Not part of the PR.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
PKG=app.meltype.keyboard.debug
OUT="$ROOT/build/qa"; mkdir -p "$OUT"
run() { echo "+ $*"; "$@" 2>&1 | head -n "${LINES_MAX:-40}"; echo "  (exit ${PIPESTATUS[0]})"; }
ready() { test "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 && adb shell pm path android 2>/dev/null | grep -q '^package:'; }
settle() { ok=0; for _ in $(seq 1 80); do if ready; then ok=$((ok+1)); else ok=0; fi; test "$ok" -ge 5 && return 0; sleep 3; done; return 1; }
crashes() {
  echo "--- crash evidence ($1)"
  grep -a -E 'FATAL EXCEPTION|\*\*\* FATAL|Fatal signal|Abort message|Watchdog|WATCHDOG|system_server.*(died|crash)|Process system|lowmemorykiller|lmkd.*[Kk]ill|init: .*(zygote|system_server).*(exit|kill|restart)|Shutting down VM|AndroidRuntime: |DEBUG   : (pid|signal|Abort|Cmdline|backtrace|    #0[0-9])|16 ?[Kk][Bb]|page size|PageSize|dex2oat.*(fail|abort|crash)|No space left|INSTALL_FAILED' "$2" | grep -v 'adbd service requested' | tail -n "${3:-70}"
}
settle; echo "settled: $?"
run adb shell getprop ro.build.version.release
run adb shell getprop ro.build.fingerprint
run adb shell "df -h /data | tail -n 1"
run adb shell "cat /proc/meminfo | head -n 3"
run adb shell "getconf PAGE_SIZE"
run adb shell pm list users
adb logcat -c || true
adb logcat -b all -v threadtime > "$OUT/probe-logcat-1.txt" 2>&1 &
LC=$!
sleep 2
echo '=== install 1: streamed'
run adb install -r "$APP"
sleep 20
kill "$LC" 2>/dev/null || true
crashes 'after streamed install' "$OUT/probe-logcat-1.txt"
echo "logcat lines: $(wc -l < "$OUT/probe-logcat-1.txt")"
settle; echo "settled: $?"
run adb shell pm path "$PKG"
adb logcat -b all -v threadtime > "$OUT/probe-logcat-2.txt" 2>&1 &
LC=$!
sleep 2
echo '=== install 2: push then pm install'
run adb push "$APP" /data/local/tmp/app.apk
run adb shell pm install -r -t /data/local/tmp/app.apk
sleep 20
run adb shell pm path "$PKG"
run adb shell am start -W -n "$PKG/io.github.ninjin0802.keyboard.app.MainActivity"
sleep 10
kill "$LC" 2>/dev/null || true
crashes 'after pm install and start' "$OUT/probe-logcat-2.txt"
settle; echo "settled: $?"
run adb shell pm path "$PKG"
run adb shell am start -W -n "$PKG/io.github.ninjin0802.keyboard.app.MainActivity"
echo '=== probe done'
