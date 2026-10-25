# NOTES.md — Pyrite working notes

Resume from here. PLAN.md has the milestones; AGENTS.md has the rules and decisions.

## Status
- M0 done: CI green; `fresh-setup.yml` proved the acceptance command from an empty SDK dir.
- M1 done: `InterpreterTest` (in-process) passes on the API 35 emulator, incl. the `45` check.
- Current: **M2 — Runner process** (`PythonService` in `:py`, `RunSession` client, `RunnerTest`).

## Architecture (so far)
- `app/src/main/cpp/pyrite.c`: JNI bridge. `redirectStdio` (dup2 onto 0/1/2), `initialize`
  (PyConfig: home, non-empty `executable` so CPython does NOT send stdout to logcat,
  `buffered_stdio=0`, faulthandler on), `runString`/`runFile` (SystemExit -> status,
  tracebacks via PyErr_Print, KeyboardInterrupt -> 130), `interrupt` (pthread_kill SIGINT
  to the Python thread: wakes blocking syscalls too), `installCrashHandlers` (fatal
  signal -> message + `_exit(128+sig)`, so no system crash dialog), `finalizePython`,
  `detachStdio`.
- `python/PythonThread`: the single thread that inits Python and runs everything.
- `python/PythonInstaller`: extracts `assets/python/stdlib.zip` to `filesDir/python`,
  stamp = "<version> <sha256>" (written by Gradle task `PackPythonStdlib`); file lock
  makes it safe across processes. pip target: `filesDir/site-packages` (`_pyrite.bootstrap`).
- `app/src/main/python/_pyrite/`: helpers inside the interpreter (bootstrap, prepare_main, repl).
- Build: `app/build.gradle.kts` reads `cross-build/<triplet>/prefix`; libpython +
  `lib*_python.so` -> jniLibs; stdlib (both ABIs merged, minus test/idlelib/tkinter/
  turtledemo/config-*) -> one zip asset.
- `runner/`: `PythonService` (`:py`, AIDL `IPythonService`), `RunSession` (bind, pipes,
  death recipient, exit reasons via ApplicationExitInfo on API 30+), `Runner` (one run at a
  time; next run waits for the previous process to die).
- M1's `InProcessPython` (Python in the calling process, marker-synced output) remains for
  `InterpreterTest` only.

## Build & test

Fresh machine (Linux/macOS, Java 17+, curl, unzip, python3):

```sh
scripts/setup-sdk.sh            # SDK + NDK + CMake; writes local.properties
scripts/fetch-python.sh         # python.org Android packages -> cross-build/<triplet>/prefix
./gradlew assembleDebug test    # APK at app/build/outputs/apk/debug/app-debug.apk
```

Instrumented tests need an emulator or device:

```sh
scripts/setup-sdk.sh --with-emulator
./gradlew connectedDebugAndroidTest
```

CI (`.github/workflows/ci.yml`) runs build + unit tests, then instrumented tests on an
API 35 x86_64 emulator (GitHub runners have KVM) via `scripts/ci-instrumented.sh`, which
prints one PASS/FAIL line per test plus filtered logcat. `fresh-setup.yml` re-checks the M0
acceptance command against an empty SDK directory whenever `scripts/` changes.

Without the Android SDK (e.g. the Claude sandbox), Android-free logic + tests still run:

```sh
gradle -p tools/jvm-unit-tests test   # sources listed in tools/jvm-unit-tests/*.txt
```

The C bridge can be syntax-checked with host clang against Python 3.15 headers (e.g. from
`com.chaquo.python:target:3.15.0-0:x86_64@zip` on Maven Central) plus a stub `android/log.h`.

## Pinned versions
| What | Version | Where |
|---|---|---|
| Python | 3.15.1 (python.org Android embeddable) | `scripts/fetch-python.sh` (+ sha256) |
| AGP / Gradle | 9.4.1 / 9.8.2 | `gradle/libs.versions.toml`, wrapper |
| Kotlin | 2.4.21 (AGP built-in Kotlin) | `gradle/libs.versions.toml` |
| Compose BOM | 2026.09.00 | `gradle/libs.versions.toml` |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 | `app/build.gradle.kts` |
| NDK | 27.3.13750724 (same as CPython's Android build) | `scripts/setup-sdk.sh` |
| CMake | 3.31.5 | `scripts/setup-sdk.sh` |

## Environment notes (Claude Code cloud sandbox)
- The sandbox's network policy blocks `dl.google.com` (SDK, NDK, Google Maven: AGP,
  AndroidX), `www.python.org`, and `downloads.gradle.org`. So nothing Android builds
  locally there; all building and testing runs in GitHub Actions, and results are read
  back through the GitHub API. No `/dev/kvm` locally either.
- Allowing those three hosts in the environment's network settings would enable local
  builds (instrumented tests still need CI: no KVM).
- Reading CI from the sandbox: GitHub MCP `actions_list` / `get_job_logs` (logs only exist
  once a job completes; the run status can lag, so don't mistake a slow status for a hang).
- Maven Central sometimes answers 429; `repo1.maven.org` + retry works.

## Open problems
- (none yet)
