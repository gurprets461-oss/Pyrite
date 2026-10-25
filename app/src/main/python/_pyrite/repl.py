"""The REPL tab: code.InteractiveConsole on the process's stdin/stdout."""

import code
import sys


def main():
    # code.interact() would print the banner to stderr, which the console shows as
    # an error color; print it to stdout instead.
    print(f"Python {sys.version} on {sys.platform}")
    print('Type "help", "copyright", "credits" or "license" for more information.')
    code.interact(banner="", exitmsg="")
