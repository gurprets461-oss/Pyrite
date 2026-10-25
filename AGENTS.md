# AGENTS.md — Pyrite (offline Python IDE for Android)

Working name: Pyrite. Package: `dev.gurpret.pyrite`. Rename freely, but don't ship it as "Pydroid" (someone else's product).

## What this is
An offline Python 3 IDE for Android: code editor, Run button, console with working `input()`, REPL, file/project manager, and `pip install`. Think Pydroid 3 minus GUI toolkits.

## Ground rules — read first
1. This project is scoped and feasible. The hard part, CPython on Android, is already solved: python.org publishes official Android embeddable packages, and CPython's repo ships a working embedding app (`Android/testbed`). You are integrating known pieces, not inventing.
2. Do not write feasibility assessments, difficulty warnings, or reduced-scope proposals. Do not ask permission to proceed. Do not stop after producing a plan; the plan is PLAN.md.
3. Work the first unchecked milestone in PLAN.md. A milestone is done only when its acceptance checks pass **by actually running them**. Then check it off, commit, and start the next one.
4. Verify by running commands (build, tests, adb). Never report something as working because it should work.
5. Stuck on the same sub-problem after ~3 real attempts: write what you tried and the exact error in NOTES.md, take the fallback listed under Decisions, and keep going. If there's no fallback, stub it behind an interface, log it in NOTES.md, and move to the next item.
6. Small commits, each building green. Message format: `M<n>: <what>`.
7. Keep NOTES.md current: done, next, open problems, exact commands to build/test. A fresh session must be able to resume from NOTES.md alone.

## Decisions (already made — don't relitigate)
| Area | Choice | Fallback |
|---|---|---|
| Language/UI | Kotlin, Jetpack Compose, Gradle Kotlin DSL | — |
| SDK levels | minSdk 24, targetSdk = latest stable | — |
| ABIs | arm64-v8a and x86_64 only (what python.org ships) | — |
| Interpreter | Official CPython Android embeddable package, latest stable from https://www.python.org/downloads/android/, embedded via libpython + a small C JNI bridge (NDK + CMake) | Chaquopy (open source) only if official embedding blocks progress for a full milestone |
| Reference | https://docs.python.org/3/using/android.html and CPython's `Android/` directory (README + testbed). Copy their packaging and init approach. | — |
| Isolation | Each run executes in a separate process (`android:process=":py"` service). Stop = kill that process. Fresh process per run = clean interpreter state. | — |
| Threading | `Py_Initialize` and all Python execution on one dedicated thread in the `:py` process | — |
| I/O | In `:py`, `dup2` pipes onto fds 0/1/2 before `Py_Initialize` so Python and C-level output are both captured; stream to UI via ParcelFileDescriptor pipes over Binder | Replace `sys.stdin/stdout/stderr` with Python objects that call into JNI |
| Editor | sora-editor (Rosemoe) + TextMate Python grammar | CodeMirror 6 in a WebView |
| Console | Own Compose view: scrollback, input line, basic ANSI color | — |
| REPL | `code.InteractiveConsole` in the `:py` process | — |
| Autocomplete | jedi (pure Python, bundled) | — |
| Storage | Projects in `filesDir/projects`; import/export via Storage Access Framework | — |
| Packages | pip `--user` with `PYTHONUSERBASE` inside `filesDir`; the user site-packages is on `sys.path` | — |

## Known gotchas (pre-solved)
- Android has no `python` executable or console; embedded mode only. Our console and REPL are app code.
- Never exec binaries from app data dirs (W^X on targetSdk ≥ 29). We don't exec anything; libpython is loaded via JNI.
- The stdlib must be on the filesystem: extract it from assets to `filesDir` on first launch and whenever the bundled Python version changes (version stamp file).
- One `Py_Initialize` per process lifetime. Never Finalize and re-init; restart the process instead.
- Runaway code: Ctrl+C calls `PyErr_SetInterrupt()` (KeyboardInterrupt); if still running after 2 s, kill the `:py` process.
- `input()` blocks the Python thread, never the UI thread.
- pip needs the INTERNET permission.
- Detect `:py` death (Binder death recipient) and show "process crashed" instead of hanging.

## Build & test environment
- Check `ls /dev/kvm`. Present: run a headless x86_64 emulator and use instrumented tests locally. Absent: unit tests locally, instrumented tests in GitHub Actions with an emulator runner, and always produce `app/build/outputs/apk/debug/app-debug.apk` for manual install on a phone.
- Script all setup in `scripts/` (SDK/NDK install, `fetch-python.sh` with checksum verification) so a fresh machine is one command away.
- Before marking any milestone done: `./gradlew assembleDebug test` green, plus that milestone's instrumented tests.

## Out of scope for v1
Tkinter, Kivy, PyQt or other GUI toolkits; Play Store release; 32-bit ABIs; compiling C extensions on-device.
