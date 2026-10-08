#!/usr/bin/env bash
# CI fixture data only. Never clear app storage or uninstall during this check.
set -euo pipefail
collect_state() {
  adb logcat -d -b crash > upgrade/restore-crashes.txt || true
  adb shell dumpsys activity activities > upgrade/restore-activity-state.txt || true
  adb shell run-as com.woojik.aircallai cat no_backup/debug-last-crash.txt > upgrade/restore-saved-crash.txt 2>/dev/null || true
  if [[ -s upgrade/restore-saved-crash.txt ]]; then cat upgrade/restore-saved-crash.txt; fi
}
trap collect_state EXIT
# Wait for the actual debug Activity stop callback before simulating process death.
# run-as signals only this disposable debuggable fixture; the system task is retained.
background_and_kill() {
  local phase="$1"
  adb shell input keyevent KEYCODE_HOME
  for attempt in $(seq 1 10); do
    adb shell run-as com.woojik.aircallai cat no_backup/debug-main-launch.txt > "upgrade/background-$phase.txt"
    if tail -n 1 "upgrade/background-$phase.txt" | grep -q 'main-stopped; finishing=false'; then break; fi
    sleep 1
  done
  cat "upgrade/background-$phase.txt"
  tail -n 1 "upgrade/background-$phase.txt" | grep -q 'main-stopped; finishing=false'
  adb shell dumpsys activity activities > "upgrade/task-$phase.txt"
  local app_pid
  app_pid=$(adb shell pidof com.woojik.aircallai | tr -d '\r')
  [[ "$app_pid" =~ ^[0-9]+$ ]]
  adb shell run-as com.woojik.aircallai kill -9 "$app_pid"
  for attempt in $(seq 1 10); do
    if ! adb shell pidof com.woojik.aircallai > /dev/null; then break; fi
    sleep 1
  done
  if adb shell pidof com.woojik.aircallai > /dev/null; then echo 'Fixture process did not exit'; return 1; fi
}
# Leave a real old-version screen in the system task, with all seeded data intact.
adb shell am force-stop com.woojik.aircallai
adb shell pm grant com.woojik.aircallai android.permission.POST_NOTIFICATIONS
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.woojik.aircallai/.ui.MainActivity --es aircall.screen models
for attempt in $(seq 1 10); do
  adb shell rm -f /sdcard/update-window.xml
  if adb shell uiautomator dump /sdcard/update-window.xml > /dev/null &&
      adb pull /sdcard/update-window.xml upgrade/before-update.xml > /dev/null &&
      python scripts/verify_update_ui.py upgrade/before-update.xml --screen models; then break; fi
  sleep 1
done
python scripts/verify_update_ui.py upgrade/before-update.xml --screen models
background_and_kill before-update
adb shell dumpsys activity activities > upgrade/task-before-update.txt
adb install -r upgrade/current/aircall-dev.apk
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.woojik.aircallai/.ui.MainActivity
for attempt in $(seq 1 10); do
  adb shell rm -f /sdcard/update-window.xml
  if adb shell uiautomator dump /sdcard/update-window.xml > /dev/null &&
      adb pull /sdcard/update-window.xml upgrade/after-update.xml > /dev/null &&
      python scripts/verify_update_ui.py upgrade/after-update.xml --screen main; then break; fi
  sleep 1
done
python scripts/verify_update_ui.py upgrade/after-update.xml --screen main
adb shell run-as com.woojik.aircallai cat no_backup/debug-main-launch.txt > upgrade/update-launch-trace.txt
# Separately prove a cold-process restart really receives saved Activity state.
background_and_kill after-update
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.woojik.aircallai/.ui.MainActivity
for attempt in $(seq 1 10); do
  adb shell rm -f /sdcard/update-window.xml
  if adb shell uiautomator dump /sdcard/update-window.xml > /dev/null &&
      adb pull /sdcard/update-window.xml upgrade/after-process-death.xml > /dev/null &&
      python scripts/verify_update_ui.py upgrade/after-process-death.xml --screen main; then break; fi
  sleep 1
done
python scripts/verify_update_ui.py upgrade/after-process-death.xml --screen main
adb shell run-as com.woojik.aircallai cat no_backup/debug-main-launch.txt > upgrade/process-restore-trace.txt
cat upgrade/process-restore-trace.txt
grep -q 'stage=activity-restored' upgrade/process-restore-trace.txt
adb shell am force-stop com.woojik.aircallai
