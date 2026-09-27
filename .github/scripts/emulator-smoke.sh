#!/usr/bin/env bash
# Runs inside android-emulator-runner once the emulator reports boot-complete.
# Installs the debug + test APKs, runs the instrumented tests, then plays a
# little with `adb input` and captures screenshots and logcat into smoke/.
set -uo pipefail

APP_ID="${APP_ID:?}"
MAIN_ACTIVITY="${MAIN_ACTIVITY:?}"
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p smoke
failed=0

# sys.boot_completed can flip before the package and storage services are
# usable (installs then die inside StorageManagerService). Wait for them.
adb wait-for-device
for _ in $(seq 1 90); do
  if adb shell pm path android >/dev/null 2>&1 && adb shell cmd package list packages >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
sleep 15
adb logcat -c || true
adb shell settings put system screen_off_timeout 1800000 || true
adb shell wm dismiss-keyguard || true

install() {
  for attempt in 1 2 3 4 5; do
    if adb install --no-streaming -r -t "$1"; then
      return 0
    fi
    echo "install of $1 failed (attempt $attempt); retrying" >&2
    sleep 10
  done
  return 1
}

if install "$APK" && install "$TEST_APK"; then
  adb shell am instrument -w -r "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke/instrument.txt
  if ! grep -q "^OK (" smoke/instrument.txt; then
    echo "::error title=Instrumented tests failed::see smoke/instrument.txt" >&2
    failed=1
  fi
else
  echo "::error title=Install failed::could not install the APKs on the emulator" >&2
  failed=1
fi

# A short real session: title screen, then a run driven by adb input.
adb shell am start -W -n "$APP_ID/$MAIN_ACTIVITY"
sleep 6
adb exec-out screencap -p > smoke/title.png
adb shell am force-stop "$APP_ID"
adb shell am start -W -n "$APP_ID/$MAIN_ACTIVITY" --ez autostart true
sleep 4
adb exec-out screencap -p > smoke/rooftop.png
adb shell input swipe 300 1900 700 1900 1500
adb shell input tap 540 1700
adb shell input tap 540 1700
adb shell input swipe 540 1800 540 1500 60
adb shell input swipe 300 1900 750 1900 2500
adb exec-out screencap -p > smoke/playing.png
adb shell input swipe 540 1600 540 1900 60
sleep 1
adb exec-out screencap -p > smoke/hiding.png
adb shell input swipe 700 1900 250 1900 3000
adb shell input tap 540 1700
sleep 1
adb exec-out screencap -p > smoke/playing-later.png
if ! adb shell pidof "$APP_ID" > smoke/pid.txt; then
  echo "::error title=App not running::the game process died during the adb session" >&2
  failed=1
fi
adb shell dumpsys activity activities > smoke/activities.txt || true
adb logcat -d > smoke/logcat.txt || true
exit "$failed"
