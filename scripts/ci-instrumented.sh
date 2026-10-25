#!/usr/bin/env bash
# Runs the instrumented tests on the connected emulator/device, then prints each
# test's result and the interesting part of logcat (always), so failures and hangs
# are debuggable from the CI log alone. Exit status is the test run's.
set -uo pipefail

adb logcat -c || true
timeout "${PYRITE_TEST_TIMEOUT:-25m}" ./gradlew connectedDebugAndroidTest "$@" 2>&1 | tee instrumented.log
status=${PIPESTATUS[0]}

if [ $status -ne 0 ]; then
    echo "logcat (Pyrite-related, last 250 lines):"
    adb logcat -d -v time 2>/dev/null \
        | grep -E 'pyrite|PythonService|PythonThread|PythonInstaller|RunSession|AnalysisClient|TestRunner|AndroidRuntime|DEBUG|libc|faulthandler' \
        | grep -v -E 'Blockstore|Icing|AppInfoHelper|ProximityAuth|AiAiEcho' \
        | tail -n 250 | tee logcat-tail.txt
    echo "Build/test errors:"
    grep -E '^e: |error:|What went wrong|FAILED' -A4 instrumented.log | head -100 | tee test-errors.txt || true
    { echo "--- logcat"; cat logcat-tail.txt 2>/dev/null; } >> test-errors.txt
fi
echo "Instrumented test results:"
python3 "$(dirname "$0")/junit-summary.py" app/build/outputs/androidTest-results/connected 2>&1 | tee test-summary.txt || true
# Exploratory results some tests log (e.g. which Android wheels work).
adb logcat -d -s PyriteWheels:I PyriteScience:I PyriteScience:W 2>/dev/null | grep -E 'Pyrite(Wheels|Science)' | tee -a test-summary.txt || true
exit $status
