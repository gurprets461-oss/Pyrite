"""The REPL tab: code.InteractiveConsole on the process's stdin/stdout.

`pip ...` lines typed in the REPL are intercepted by the app and run in a separate
process (pip must not run inside this interpreter), so here we only need to notice
newly installed packages.
"""

import code
import importlib
import os
import sys

import _pyrite


class Console(code.InteractiveConsole):
    def __init__(self):
        super().__init__()
        self._site_mtime = self._mtime()

    def _mtime(self):
        try:
            return os.stat(_pyrite.SITE_PACKAGES).st_mtime_ns
        except (OSError, TypeError):
            return None

    def push(self, line, *args, **kwargs):
        mtime = self._mtime()
        if mtime != self._site_mtime:
            self._site_mtime = mtime
            importlib.invalidate_caches()  # pip installed something
        return super().push(line, *args, **kwargs)


def main():
    # code.interact() would print the banner to stderr, which the console shows as
    # an error color; print it to stdout instead.
    print(f"Python {sys.version} on {sys.platform}")
    print('Type "help", "copyright", "credits" or "license" for more information.')
    print('Type "pip install <package>" to install packages.')
    Console().interact(banner="", exitmsg="")
