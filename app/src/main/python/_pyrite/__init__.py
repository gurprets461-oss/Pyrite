"""Pyrite's helpers inside the embedded interpreter. Called by the app, not user code."""

import os
import site
import sys

# The user site-packages folder pip installs into (PYTHONUSERBASE); set by bootstrap().
SITE_PACKAGES = None


def bootstrap(site_packages):
    """Runs once, right after the interpreter starts."""
    global SITE_PACKAGES
    SITE_PACKAGES = site_packages
    # The app creates this folder before startup, so site.py has already put it on
    # sys.path (ahead of the bundled site-packages). Cover the case where it didn't.
    os.makedirs(site_packages, exist_ok=True)
    if site_packages not in sys.path:
        site.addsitedir(site_packages)


def prepare_main(path, args):
    """Sets up argv, sys.path[0] and the cwd the way `python path args...` would."""
    path = os.path.abspath(path)
    folder = os.path.dirname(path)
    sys.argv = [path, *args]
    sys.path.insert(0, folder)
    os.chdir(folder)
