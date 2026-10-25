# Pyrite

An offline Python 3 IDE for Android: code editor, Run button, console with working
`input()`, REPL, file manager, and `pip install`. It embeds the official CPython
Android build from python.org (Python 3.15).

## Features
- **Editor**: syntax highlighting, line numbers, undo/redo, auto-indent, a keys row for
  symbols, find/replace, font size, jedi completions and signature help, syntax errors
  underlined as you type.
- **Run**: each run gets a fresh interpreter in its own process. Stop, Ctrl+C
  (KeyboardInterrupt, then kill after 2 s), exit codes, tracebacks; a native crash only
  takes down the run, never the app.
- **Console and REPL**: ANSI colors, 10k-line scrollback, copy, input history; the REPL
  also accepts `pip install ...`.
- **Files**: projects in app storage; scripts run in their own folder, so sibling imports
  and relative file paths work; import/export through Android's file picker.
- **Packages**: pip from PyPI (user installs, so already-installed dependencies are
  reused). Pure-Python packages work, and so do packages that publish Android wheels
  (e.g. markupsafe, aiohttp). numpy, Pillow, matplotlib, contourpy and kiwisolver come
  from Pyrite's own wheel index (below), which pip uses as an extra index.
- **Plots**: `plt.show()` draws matplotlib figures as images in the console.

## Android wheel index

`.github/workflows/wheels.yml` builds CPython 3.15 Android wheels (arm64-v8a, x86_64)
with cibuildwheel for packages that don't publish them on PyPI yet, and publishes them
as a PEP 503 simple index on GitHub Pages (`gh-pages` branch):
https://gurprets461-oss.github.io/Pyrite/simple/. Recipes are in `wheels/`; versions in
`wheels/versions.txt`. GitHub Pages must be enabled once in the repository settings
(Deploy from a branch: `gh-pages`, `/`).

## Build

Needs Linux or macOS with Java 17+, curl, unzip and python3:

```sh
scripts/setup-sdk.sh          # Android SDK, NDK, CMake (non-interactive)
scripts/fetch-python.sh       # python.org Android packages + bundled wheels, checksum-verified
./gradlew assembleDebug test  # app/build/outputs/apk/debug/app-debug.apk
```

Instrumented tests: `scripts/setup-sdk.sh --with-emulator`, start an emulator, then
`./gradlew connectedDebugAndroidTest`. CI runs everything on every push, on an x86_64
emulator and (the numpy/matplotlib tests) on an arm64 Android 14 container (redroid) on
GitHub's arm64 runner.

ABIs: arm64-v8a and x86_64 (what python.org ships). minSdk 24.

Development notes, architecture and gotchas are in [NOTES.md](NOTES.md); milestones in
[PLAN.md](PLAN.md).
