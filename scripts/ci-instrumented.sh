#!/usr/bin/env bash
# Runs the instrumented tests on the connected emulator/device, then prints each
# test's result and the interesting part of logcat (always), so failures and hangs
# are debuggable from the CI log alone. Exit status is the test run's.
set -uo pipefail

adb logcat -c || true
timeout "${PYRITE_TEST_TIMEOUT:-25m}" ./gradlew connectedDebugAndroidTest --stacktrace "$@"
status=$?

echo "::group::logcat (Pyrite-related)"
adb logcat -d -v time 2>/dev/null \
    | grep -E 'pyrite|Pyrite|PythonService|PythonThread|PythonInstaller|RunSession|TestRunner|AndroidRuntime|DEBUG|libc|python|faulthandler|ActivityManager.*(pyrite|:py)' \
    | tail -n 400
echo "::endgroup::"

echo "Instrumented test results:"
python3 "$(dirname "$0")/junit-summary.py" app/build/outputs/androidTest-results/connected || true
exit $status
