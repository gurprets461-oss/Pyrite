"""pip inside the app.

Android can't exec a `python` binary, so pip runs in-process. It comes from the wheel
CPython bundles for ensurepip, unpacked into the user site-packages folder the first
time it's needed. Installs go there too (`--user`, with PYTHONUSERBASE inside the
app's files dir), since the stdlib folder is replaced whenever the app updates.
Unlike `--target`, the user scheme lets pip see what's already installed, so
dependencies that are already satisfied aren't reinstalled.
"""

import glob
import importlib
import importlib.util
import os
import sys

import _pyrite

_LOCATION_OPTIONS = ("-t", "--target", "--user", "--prefix", "--root")


def ensure_pip():
    """Unpacks the bundled pip wheel into site-packages if pip isn't importable yet."""
    if importlib.util.find_spec("pip") is not None:
        return
    import ensurepip
    import zipfile

    bundled = os.path.join(os.path.dirname(ensurepip.__file__), "_bundled")
    wheels = sorted(glob.glob(os.path.join(bundled, "pip-*.whl")))
    if not wheels:
        raise RuntimeError(f"no bundled pip wheel in {bundled}")
    with zipfile.ZipFile(wheels[-1]) as z:
        z.extractall(_pyrite.SITE_PACKAGES)
    importlib.invalidate_caches()


def prepare_args(args):
    """Makes `pip install` a user install unless the user chose a location."""
    args = list(args)
    if args and args[0] == "uninstall" and "-y" not in args and "--yes" not in args:
        args.insert(1, "-y")  # there's no terminal to answer pip's prompt
    if args and args[0] == "install" and not any(
        a == opt or a.startswith(opt + "=") for a in args for opt in _LOCATION_OPTIONS
    ):
        # Console scripts can't be run on Android anyway, so skip pip's PATH warning.
        args[1:1] = ["--user", "--no-warn-script-location"]
    return args


def _explain_source_builds():
    """Turns pip's attempt to build an sdist into a clear error.

    Building runs `sys.executable` in a subprocess, which doesn't exist on Android, so
    pip would fail with a bare "No such file or directory: .../bin/python3".
    """
    try:
        from pip._internal.build_env import installer
        from pip._internal.exceptions import InstallationError
    except ImportError:
        return  # a pip version laid out differently: keep its own error
    original = installer.call_subprocess

    def call_subprocess(cmd, *args, **kwargs):
        if cmd and str(cmd[0]) == sys.executable:
            raise InstallationError(
                "No wheel for Android matches, and Android can't build packages from "
                f"source ({kwargs.get('command_desc', 'building')}). Only packages with "
                "Android wheels or pure-Python wheels can be installed."
            )
        return original(cmd, *args, **kwargs)

    installer.call_subprocess = call_subprocess


def main(args):
    """Runs pip with the given argument list; returns its exit status."""
    ensure_pip()
    from pip._internal.cli.main import main as pip_main

    _explain_source_builds()

    try:
        status = pip_main(prepare_args(args))
    finally:
        # Make freshly installed packages importable in this interpreter.
        importlib.invalidate_caches()
    return status
