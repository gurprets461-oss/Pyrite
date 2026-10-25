# NOTES.md — Pyrite working notes

Resume from here. PLAN.md has the milestones; AGENTS.md has the rules and decisions.

## Status
- M0 done: CI green; `fresh-setup.yml` proved the acceptance command from an empty SDK dir.
- M1 done: `InterpreterTest` (in-process) passes on the API 35 emulator, incl. the `45` check.
- M2 done: `RunnerTest` (18 tests incl. stop < 3 s, Ctrl+C, os.abort()) green.
- M3 done: `ConsoleUiTest` (REPL multi-line function + history, ANSI red) green.
- M4 done: `EditorTest` types `for i in range(3):` + Enter (auto-indent) + `print(i * 2)` via
  key events and the keys row, runs it, sees 0/2/4; a second test in a fresh process
  (test orchestrator) finds the file restored.
- M5 done: `FilesTest` builds proj/{helpers,main}.py through the Files UI, runs it
  (sibling import, cwd == sys.path[0]), sees out.txt in the browser, renames + deletes it.
- M6 done: `PackagesTest` installs `requests rich` from the Packages tab, then
  `requests.get("https://example.com")` -> 200; `pip list` from the REPL; Android wheels.
- Current: **M7** (jedi completions, syntax underline, settings/examples/recent/session).

## pip / Android wheels (M6)
- pip = CPython's bundled ensurepip wheel (pip 26.2.2), unpacked into
  `filesDir/site-packages` on first use, run in-process in the `:pip` process with
  `--target <site-packages> --upgrade` added to `install` (see `_pyrite/pip.py`).
  Never run pip inside the REPL's interpreter: pip >= 26.3 rejects imports made after it
  starts. REPL `pip ...` lines are intercepted by `ReplController` and sent to `:pip`.
- sdists can't build (no subprocess/compiler on Android): only wheels install.
- Tested on the API 35 x86_64 emulator (2026-10-25), all via PyPI:
  | Package | Result |
  |---|---|
  | requests, rich (+ deps) | pure wheels, install + work (`requests.get` 200) |
  | markupsafe 3.0.4 | cp315 android_24_x86_64 wheel, `_speedups` C ext imports |
  | multidict 7.2.1 | cp315 android_24_x86_64 wheel, C ext imports (no arm64 wheel on PyPI) |
  | aiohttp 3.14.5 | cp315 android wheel + deps (`--only-binary :all:`), `_http_parser` imports |
  | charset-normalizer 3.5.2 | no cp315 android wheel; pure-Python wheel used |
- No Android wheels on PyPI yet (checked 2026-10-25): numpy, pillow, cryptography,
  pydantic-core, regex, lxml, pyyaml, msgpack, cffi, orjson, psutil, pandas, scipy.

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
- Processes: `:py` (script runs, one per run), `:repl` (REPL), `:pip` (pip), `:analysis`
  (jedi + syntax checks, long-lived). All are `PythonService` subclasses; `Runner` keeps
  one session per process slot (`Runner.serviceFor`).
- `console/`: `AnsiParser` + `ConsoleBuffer` (pure Kotlin, 10k-line cap, `\r` redraw,
  tabs), `ConsoleState` (Compose state + `CommandHistory`), `ConsoleView` (LazyColumn).
- `editor/`: `EditorController` owns the sora `CodeEditor` view (kept across tab switches;
  the activity handles config changes), keys row, find/replace via `EditorSearcher`.
  `PythonLanguage` = TextMate language + jedi completions (`AnalysisClient`, JSON lines
  to `_pyrite/analysis.py`). Syntax check = `compile()` in `:analysis` after a 600 ms
  pause -> `DiagnosticRegion`s via a registered `DiagnosticProvider`.
- `files/`: `ProjectStore` (atomic writes), `FileOps` (browser ops confined to
  `filesDir/projects`), `FileTransfer` (SAF import/export), `FilesScreen`.
- `packages/`: `SitePackages` (reads `*.dist-info/METADATA`), `PipCommand` (REPL pip line
  parsing), `PackagesScreen`/`PackagesController`.
- `settings/`: `Settings` (SharedPreferences "pyrite": font size, theme, completion,
  last file/tab/cursor, recent files), `SettingsDialog`.
- jedi 0.20.1 + parso 0.8.7 wheels are fetched (pinned sha256) by `fetch-python.sh` into
  `cross-build/wheels` and unpacked into the bundled site-packages by `PackPythonStdlib`
  (django-stubs dropped). parso clamps to its 3.14 grammar, which parses 3.15 fine.

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
prints one PASS/FAIL line per test plus filtered logcat. The last job, **Report**, prints
just the build errors / test summary / logcat tail: read that job's log first.
Instrumented tests use the AndroidX test orchestrator (one process per test). `fresh-setup.yml` re-checks the M0
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
  Read the **Report** job's log (short). Large logs get saved to a local file you can grep.
- In `build.gradle.kts`, `java.` resolves to Gradle's `java` extension: import JDK classes.
- Maven Central sometimes answers 429; `repo1.maven.org` + retry works.

## Gotchas found so far
- Android's `FileDescriptor.out/err` are dups taken at startup: after `dup2()` they still
  point at /dev/null. Write to the real fd 1/2 via `StdFds` (ParcelFileDescriptor.fromFd).
- CPython on Android redirects sys.stdout/stderr to logcat when `sys.executable` is empty;
  we set a (never executed) executable path to keep them on fds 1/2.
- A process that dies while still bound with BIND_AUTO_CREATE gets restarted by the
  system; `RunSession` unbinds as soon as the death recipient fires.
- Android may hand a new binding a cached `:py`/`:repl`/`:analysis` process whose Python
  already ran (e.g. after the UI process died). `IPythonService.isFresh()` exposes a
  per-process flag; `RunSession` kills a stale process and binds again.
- jedi picks a "same environment" when sys.executable's basename starts with "python",
  then tries to spawn it. Always pass `environment=jedi.InterpreterEnvironment()`.
- Input written to a `RunSession` before the service connects is queued, not dropped.

## Open problems
- SAF import/export (Files tab) is implemented but not covered by automated tests: it
  needs the system file picker. Verify by hand on a device.
- pip `--target` reinstalls dependencies on each install (pip ignores already-installed
  packages in target mode); cached wheels make it cheap, but it's not "already satisfied".
