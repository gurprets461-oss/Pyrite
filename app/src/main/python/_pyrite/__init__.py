"""Pyrite's helpers inside the embedded interpreter. Called by the app, not user code."""

import os
import site
import sys

# Where pip installs packages; set by bootstrap().
SITE_PACKAGES = None


def bootstrap(site_packages):
    """Runs once, right after the interpreter starts."""
    global SITE_PACKAGES
    SITE_PACKAGES = site_packages
    # pip installs here (outside the extracted stdlib, so app updates don't wipe it).
    # addsitedir also processes .pth files, which some packages rely on.
    os.makedirs(site_packages, exist_ok=True)
    site.addsitedir(site_packages)


def prepare_main(path, args):
    """Sets up argv, sys.path[0] and the cwd the way `python path args...` would."""
    path = os.path.abspath(path)
    folder = os.path.dirname(path)
    sys.argv = [path, *args]
    sys.path.insert(0, folder)
    os.chdir(folder)
