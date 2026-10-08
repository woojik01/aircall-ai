#!/usr/bin/env bash
# CI fixture data only. Never clear app storage or uninstall during this check.
set -euo pipefail
collect_state() {
  adb logcat -d -b crash > upgrade/restore-crashes.txt || true
  adb shell dumpsys activity activities > upgrade/restore-activity-state.txt || true
  adb shell run-as com.woojik.aircallai cat no_backup/debug-last-crash.txt > upgrade/restore-saved-crash.txt 2>/dev/null || true
}
trap collect_state EXIT
# Leave a real old-version screen in the system task, with all seeded data intact.
adb shell am force-stop com.woojik.aircallai
adb shell pm grant com.woojik.aircallai android.permission.POST_NOTIFICATIONS
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.woojik.aircallai/.ui.MainActivity --es aircall.screen models
for attempt in $(seq 1 10); do
  adb shell uiautomator dump /sdcard/update-window.xml > /dev/null
  adb pull /sdcard/update-window.xml upgrade/before-update.xml > /dev/null
  if python scripts/verify_update_ui.py upgrade/before-update.xml --screen models; then break; fi
  sleep 1
done
python scripts/verify_update_ui.py upgrade/before-update.xml --screen models
adb shell input keyevent KEYCODE_HOME
adb shell am kill com.woojik.aircallai
for attempt in $(seq 1 10); do
  if ! adb shell pidof com.woojik.aircallai > /dev/null; then break; fi
  sleep 1
done
if adb shell pidof com.woojik.aircallai > /dev/null; then echo 'Old background process was not killed'; exit 1; fi
adb shell dumpsys activity activities > upgrade/task-before-update.txt
adb install -r upgrade/current/aircall-dev.apk
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.woojik.aircallai/.ui.MainActivity
for attempt in $(seq 1 10); do
  adb shell uiautomator dump /sdcard/update-window.xml > /dev/null
  adb pull /sdcard/update-window.xml upgrade/after-update.xml > /dev/null
  if python scripts/verify_update_ui.py upgrade/after-update.xml --screen main; then break; fi
  sleep 1
done
python scripts/verify_update_ui.py upgrade/after-update.xml --screen main
adb shell run-as com.woojik.aircallai cat no_backup/debug-main-launch.txt > upgrade/update-launch-trace.txt
# Separately prove a cold-process restart really receives saved Activity state.
adb shell input keyevent KEYCODE_HOME
adb shell am kill com.woojik.aircallai
for attempt in $(seq 1 10); do
  if ! adb shell pidof com.woojik.aircallai > /dev/null; then break; fi
  sleep 1
done
if adb shell pidof com.woojik.aircallai > /dev/null; then echo 'New background process was not killed'; exit 1; fi
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.woojik.aircallai/.ui.MainActivity
for attempt in $(seq 1 10); do
  adb shell uiautomator dump /sdcard/update-window.xml > /dev/null
  adb pull /sdcard/update-window.xml upgrade/after-process-death.xml > /dev/null
  if python scripts/verify_update_ui.py upgrade/after-process-death.xml --screen main; then break; fi
  sleep 1
done
python scripts/verify_update_ui.py upgrade/after-process-death.xml --screen main
adb shell run-as com.woojik.aircallai cat no_backup/debug-main-launch.txt > upgrade/process-restore-trace.txt
cat upgrade/process-restore-trace.txt
grep -q 'stage=activity-restored' upgrade/process-restore-trace.txt
adb shell am force-stop com.woojik.aircallai
