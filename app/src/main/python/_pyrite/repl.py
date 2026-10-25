"""The REPL tab: code.InteractiveConsole on the process's stdin/stdout."""

import code
import sys


def main():
    banner = f"Python {sys.version} on {sys.platform}\nType \"help\", \"copyright\", \"credits\" or \"license\" for more information."
    code.interact(banner=banner, exitmsg="")
