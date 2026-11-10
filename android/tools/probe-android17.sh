#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 KoheiTakada
# Temporary diagnosis for the Android 17 emulator job. Not part of the PR.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
PKG=app.meltype.keyboard.debug
run() { echo "+ $*"; "$@" 2>&1 | head -n "${LINES_MAX:-40}"; echo "  (exit ${PIPESTATUS[0]})"; }
for _ in $(seq 1 40); do
  test "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 && adb shell pm path android >/dev/null 2>&1 && break
  sleep 3
done
sleep 30
run adb shell getprop ro.build.version.release
run adb shell getprop ro.build.version.sdk
run adb shell getprop ro.product.model
run adb shell getprop ro.fw.mu.headless_system_user
run adb shell am get-current-user
CUR="$(adb shell am get-current-user 2>/dev/null | tr -d '\r')"
run adb shell pm list users
run adb install -r "$APP"
run adb install -r "$TEST"
for u in 0 "$CUR"; do
  run adb shell "pm list packages --user $u | grep -i meltype"
  run adb shell pm path --user "$u" "$PKG"
  run adb shell cmd package resolve-activity --brief --user "$u" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$PKG"
  run adb shell am start -W --user "$u" -n "$PKG/io.github.ninjin0802.keyboard.app.MainActivity"
  run adb shell ime list -a -s --user "$u"
done
run adb shell "dumpsys package $PKG | grep -E 'User [0-9]+:|versionName|installed=|pkgFlags|codePath'"
LINES_MAX=60 run adb shell "dumpsys package $PKG | grep -n -A14 'Activity Resolver Table'"
run adb shell am start -W -n "$PKG/io.github.ninjin0802.keyboard.app.MainActivity"
run adb shell am start -W -n "$PKG/.MainActivity"
LINES_MAX=40 run adb shell "logcat -d -t 400 | grep -iE 'meltype|ActivityTaskManager|PackageManager' | tail -n 40"
echo '=== probe done'
