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
- M7 done: `PolishTest` types `os.path.` key by key and sees sora's completion window
  (jedi via `InterpreterEnvironment` in `:analysis`); `def broken(:` gets an error
  underline + SyntaxError bar with nothing run, cleared once fixed; dark theme from
  settings, example opened, back to main.py via Recent, all persisted.
- M0–M7 done (CI run 40209763735: 30 instrumented tests, 0 failed).
- pip now uses the user scheme (`--user`, PYTHONUSERBASE in filesDir) instead of
  `--target`; `PackagesTest.d_installingTwiceInstallsNothing` (CI run 40211098571).
- Current: **M8**. Its acceptance test passes on an arm64 device: CI run 40214530501,
  `ScienceTest` 4/4 on redroid (Android 14 arm64-v8a) — numpy 2.5.4 aarch64 wheel,
  `numpy.arange(10).sum() == 45`, `plt.plot([1,2,3]); plt.show()` -> 960x720 image in the
  console — and 35/35 on the x86_64 emulator. Left: GitHub Pages isn't enabled yet, so
  CI installed from a local copy of `gh-pages`; once the repo owner enables Pages, the
  same tests use https://gurprets461-oss.github.io/Pyrite/simple/ directly (the stand-in
  script steps aside when that URL answers). See "Scientific wheels (M8)".

## pip / Android wheels (M6)
- pip = CPython's bundled ensurepip wheel (pip 26.2.2), unpacked into the user
  site-packages on first use, run in-process in the `:pip` process with `--user`
  added to `install` (see `_pyrite/pip.py`). `PYTHONUSERBASE=filesDir/python-user`
  (set before `Py_Initialize`), so packages land in
  `filesDir/python-user/lib/python3.15/site-packages`. The app creates that folder
  before startup: site.py only adds the user site to sys.path if it exists, and then
  puts it ahead of the bundled site-packages. pip sees installed packages there, so a
  second `pip install requests` is all "Requirement already satisfied"
  (`PackagesTest.d_installingTwiceInstallsNothing`). The old `--target` folder
  `filesDir/site-packages` is moved into the user site on first start.
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

## Scientific wheels (M8)
- beeware.org/mobile-wheels (its data: `results.json` on the beeware/mobile-wheels
  gh-pages branch, updated 2026-10-25): no Android wheels on PyPI for numpy, pillow,
  matplotlib, contourpy or kiwisolver; fonttools is pure Python. 17 of the top 360
  binary packages have Android wheels. So all five are built here.
- `.github/workflows/wheels.yml` (runs on pushes to `wheels/**`): one job per package
  and ABI, `wheels/build.sh <pkg> <arm64_v8a|x86_64>` = cibuildwheel 4.3.4 on the PyPI
  sdist, target `cp315-android_*` (Python 3.15.0, ABI-compatible with the app's 3.15.1),
  versions in `wheels/versions.txt`. Each takes 2-6 min on ubuntu-24.04.
  - numpy: `-Dblas=none -Dlapack=none`; `wheels/android-longdouble.cross` sets
    `longdouble_format = 'IEEE_QUAD_LE'` (128-bit quad on Android arm64 *and* x86_64),
    because numpy's probe runs a test program, which can't run when cross-compiling.
  - contourpy: its link line had a bare `-Wl,-rpath,` followed by libpython's path, so
    libpython wasn't linked and Android's `-Wl,--no-undefined` failed on every Py*
    symbol. `wheels/contourpy-before-build.sh` adds `link_args: ['-lpython3.15']`.
  - Pillow: upstream has no Android dependency build. `wheels/pillow-deps.sh` builds
    static libjpeg-turbo, freetype and libwebp (libwebp needs `-DANDROID_NDK` for the
    NDK's cpufeatures; sharpyuv and cpufeatures are folded into libwebp.a because
    Pillow only links webp/webpmux/webpdemux) and uses the NDK's libz.so (allowed by
    auditwheel). raqm/imagequant off, no tiff/lcms2/openjpeg/avif yet.
  - kiwisolver, matplotlib: as-is (matplotlib builds FreeType, Qhull, HarfBuzz and
    libraqm as meson subprojects). C++ wheels bundle a renamed libc++_shared.
- The publish job merges new wheels with the ones on gh-pages, writes a PEP 503 index
  (`wheels/make_index.py`) and force-pushes a one-commit `gh-pages` branch:
  https://gurprets461-oss.github.io/Pyrite/simple/. GitHub Pages has to be switched on
  once by the repo owner (Settings > Pages > Deploy from a branch > gh-pages, / root):
  the workflow token gets 403 from the Pages API and pushing gh-pages doesn't enable it.
  While Pages is off, CI serves a copy of gh-pages from the runner
  (`scripts/wheel-index-standin.sh`) and logs a warning.
- App side: `pip.conf` is packed into the stdlib zip root, i.e. `sys.prefix/pip.conf`,
  pip's "site" config: `extra-index-url = <index>` and `prefer-binary = true` (Android
  can't build sdists). The URL comes from Gradle (`-Ppyrite.wheelIndex=...` overrides)
  and is `BuildConfig.WHEEL_INDEX_URL`.
- plt.show(): `MPLBACKEND=module://_pyrite.mpl_backend`. Its `show()` saves each figure
  as PNG (150 dpi: 960x720 by default) in `cacheDir/console-images` and writes
  `ESC ] pyrite-image ; <path> BEL` to stdout (`_pyrite.show_image`). `AnsiParser` hands
  OSC payloads to `ConsoleBuffer`, which adds an image line; `ConsoleView` decodes it
  off the main thread. Shown figures are closed (like a notebook). Old images are
  deleted when the app's ViewModel starts.
- arm64 device: GitHub's `ubuntu-24.04-arm` runner has no /dev/kvm and Google ships no
  linux-aarch64 emulator. redroid (`redroid/redroid:14.0.0-latest`, Android 14 / API 34
  arm64-v8a in Docker) works there after `apt install linux-modules-extra-$(uname -r)`
  and `modprobe binder_linux`; it boots in ~10 s. CI job `arm64` installs the APKs and
  runs `ScienceTest` with `am instrument` (`scripts/redroid-start.sh`,
  `scripts/device-tests.sh`), and keeps a screenshot of the plot.

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
  makes it safe across processes. `ensureUserSite()` creates the user site-packages
  (`filesDir/python-user/lib/python3.15/site-packages`) that pip `--user` installs to.
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
- pip silently ignores a plain-http index unless its host is passed with
  `--trusted-host` (the CI stand-in index needs it).
- With no matching wheel, pip falls back to the sdist and tries to run `sys.executable`
  to build it, which doesn't exist on Android. `_pyrite/pip.py` turns that into a clear
  "Android can't build packages from source" error.

## Open problems
- GitHub Pages isn't enabled for the repo (needs the owner: Settings > Pages > Deploy
  from a branch > gh-pages, / root). Until then the app's extra index returns 404 and
  pip only sees PyPI.
- `PolishTest.typingOsPathDotShowsCompletions` timed out once waiting for the completion
  window (CI run 40213618581) and passed in the runs before and after; watch for it.
- SAF import/export (Files tab) is implemented but not covered by automated tests: it
  needs the system file picker. Verify by hand on a device.
