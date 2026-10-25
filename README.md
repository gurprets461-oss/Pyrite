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
- **Packages**: pip from PyPI. Pure-Python packages work, and so do packages that publish
  Android wheels (e.g. markupsafe, aiohttp). See NOTES.md for what's been tested.

## Build

Needs Linux or macOS with Java 17+, curl, unzip and python3:

```sh
scripts/setup-sdk.sh          # Android SDK, NDK, CMake (non-interactive)
scripts/fetch-python.sh       # python.org Android packages + bundled wheels, checksum-verified
./gradlew assembleDebug test  # app/build/outputs/apk/debug/app-debug.apk
```

Instrumented tests: `scripts/setup-sdk.sh --with-emulator`, start an emulator, then
`./gradlew connectedDebugAndroidTest`. CI runs everything on every push.

ABIs: arm64-v8a and x86_64 (what python.org ships). minSdk 24.

Development notes, architecture and gotchas are in [NOTES.md](NOTES.md); milestones in
[PLAN.md](PLAN.md).
