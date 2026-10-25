#!/usr/bin/env bash
# Installs the debug APK and test APK on a device and runs test classes with
# `am instrument` (no Gradle or SDK needed, so it works on hosts without one).
#
# Usage: scripts/device-tests.sh <serial> <apk-dir> <test class>[,<test class>...] [key=value...]
# Extra key=value pairs become instrumentation arguments (-e key value).
# Writes device-summary.txt (one line per test), device-errors.txt and, if a test saved
# one, plot-screenshot.png. Exit status 0 only if every test passed.
set -uo pipefail
serial=$1 apks=$2 classes=$3
shift 3
extra=()
for kv in "$@"; do extra+=(-e "${kv%%=*}" "${kv#*=}"); done
adb() { command adb -s "$serial" "$@"; }

adb install -r -t "$apks/app-debug.apk"
adb install -r -t "$apks/app-debug-androidTest.apk"
adb logcat -c || true
timeout "${PYRITE_TEST_TIMEOUT:-40m}" command adb -s "$serial" shell am instrument -w -r \
    -e class "$classes" "${extra[@]}" dev.gurpret.pyrite.test/androidx.test.runner.AndroidJUnitRunner \
    | tr -d '\r' | tee am-instrument.log
python3 "$(dirname "$0")/am-instrument-summary.py" am-instrument.log > device-summary.txt
status=$?
cat device-summary.txt
adb logcat -d -s PyriteScience:I PyriteWheels:I 2>/dev/null | grep -E 'Pyrite(Science|Wheels)' | tee -a device-summary.txt || true
if [ $status -ne 0 ]; then
    adb logcat -d -v time 2>/dev/null \
        | grep -E 'pyrite|PythonService|PythonThread|PythonInstaller|RunSession|TestRunner|AndroidRuntime|DEBUG|libc' \
        | tail -n 200 > device-errors.txt || true
fi
adb exec-out run-as dev.gurpret.pyrite cat files/plot-screenshot.png > plot-screenshot.png 2>/dev/null \
    && [ -s plot-screenshot.png ] || rm -f plot-screenshot.png
exit $status
