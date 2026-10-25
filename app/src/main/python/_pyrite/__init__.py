"""Pyrite's helpers inside the embedded interpreter. Called by the app, not user code."""

import os
import site
import sys
import uuid

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


# The console shows an image when it sees this escape sequence (OSC) on stdout/stderr:
# ESC ] pyrite-image ; <absolute path> BEL
IMAGE_OSC = "\x1b]pyrite-image;"


def new_image_path(suffix=".png"):
    """A fresh file path for an image the console will show (in the app's cache)."""
    folder = os.environ.get("PYRITE_IMAGE_DIR") or os.path.join(
        os.environ.get("TMPDIR", "/tmp"), "pyrite-images"
    )
    os.makedirs(folder, exist_ok=True)
    return os.path.join(folder, uuid.uuid4().hex + suffix)


def show_image(path):
    """Shows an image file (PNG, JPEG, ...) in the console, after the output so far."""
    for stream in (sys.stdout, sys.__stdout__):
        try:
            stream.flush()
        except (AttributeError, OSError, ValueError):
            pass
    # sys.__stdout__: the console, even if the program redirected sys.stdout.
    sys.__stdout__.write(f"{IMAGE_OSC}{os.path.abspath(path)}\a")
    sys.__stdout__.flush()


def prepare_main(path, args):
    """Sets up argv, sys.path[0] and the cwd the way `python path args...` would."""
    path = os.path.abspath(path)
    folder = os.path.dirname(path)
    sys.argv = [path, *args]
    sys.path.insert(0, folder)
    os.chdir(folder)
