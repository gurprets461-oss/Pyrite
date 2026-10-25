# PLAN.md — Pyrite milestones

Work top to bottom. Check items off only when the "Done when" checks pass by running them.

## M0 — Skeleton
- [ ] Compose app, single activity, builds for arm64-v8a + x86_64
- [ ] `scripts/setup-sdk.sh`: installs SDK, NDK, build tools non-interactively
- [ ] `scripts/fetch-python.sh`: downloads the official Android embeddable packages, verifies checksums, unpacks into the layout the CPython docs/testbed use
- [ ] GitHub Actions: build + unit tests on push
**Done when:** fresh clone → `scripts/setup-sdk.sh && scripts/fetch-python.sh && ./gradlew assembleDebug` succeeds.

## M1 — Hello, interpreter
- [ ] C JNI bridge (CMake) loads libpython, configures home/paths, runs a string
- [ ] Stdlib extracted from assets on first launch, with version stamp
- [ ] Debug screen: text box + Run button + output
**Done when:** instrumented test runs `import sys, json, sqlite3, ssl; print(sum(range(10)))` and asserts output `45` with no exception.

## M2 — Runner process
- [ ] `PythonService` in `:py` process; init + execution on one dedicated thread
- [ ] fds 0/1/2 piped to the UI; stdout vs stderr distinguished
- [ ] `input()` round-trips through the UI
- [ ] Stop (kill process) and Ctrl+C (KeyboardInterrupt, then kill after 2 s)
- [ ] Tracebacks shown; `sys.exit(n)` shows exit code; native crash in `:py` doesn't take down the UI
**Done when:** instrumented tests pass for: print; `input()` echo; `while True: pass` stopped within 3 s; `1/0` shows a traceback; `import os; os.abort()` → UI survives and reports the crash.

## M3 — Console + REPL
- [ ] Console view: scrollback (cap ~10k lines), monospace, input line, copy text, basic ANSI colors
- [ ] REPL tab via `code.InteractiveConsole`: multi-line blocks, `>>>` / `...` prompts, up/down history
**Done when:** tests pass: REPL defines a multi-line function and calls it; `print("\033[31mred\033[0m")` renders red.

## M4 — Editor
- [ ] sora-editor with Python highlighting, line numbers, undo/redo, auto-indent after `:`
- [ ] Extra-keys row above the keyboard: Tab, `: ( ) [ ] { } " ' # = _`, arrow keys
- [ ] Run button: autosave → run in `:py` → switch to console
- [ ] Find/replace; font size setting
**Done when:** on emulator/device: type an indented script using the keys row, run it, see output; kill and reopen the app and the file is still there.

## M5 — Files & projects
- [ ] File browser for `filesDir/projects`: new / rename / delete file and folder
- [ ] Runs use the script's folder as cwd and `sys.path[0]`; imports between sibling files work
- [ ] Import/export via Storage Access Framework
**Done when:** a project with `main.py` importing `helpers.py` runs; a file written with `open("out.txt", "w")` shows up in the browser.

## M6 — pip
- [ ] pip available in-app (ensurepip or bundled wheel), installing to `filesDir/site-packages`
- [ ] Packages screen (install / uninstall / list); `pip` also usable from the console
- [ ] Pure-Python packages work. Then try binary wheels with Android tags from PyPI and record which work in NOTES.md
**Done when:** `pip install requests rich` succeeds and `requests.get("https://example.com").status_code == 200`.

## M7 — Pydroid-feel polish
- [ ] Autocomplete + signature help via jedi
- [ ] Syntax-error underlines (compile on typing pause)
- [ ] Bundled example scripts; dark/light theme; settings screen
- [ ] Recent files; restore last session
**Done when:** typing `os.path.` shows a completion list; a syntax error is underlined without running.

## Later (not v1)
matplotlib/Pillow output rendered as images; numpy etc. where Android wheels exist; full terminal emulator view; Git integration; GUI toolkits.
