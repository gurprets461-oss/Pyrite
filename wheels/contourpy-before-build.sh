#!/usr/bin/env bash
# cibuildwheel before-build for contourpy (runs in the unpacked sdist).
#
# Its extension's link line ends with a bare `-Wl,-rpath,` followed by the path to
# libpython, so libpython isn't linked and, with Android's -Wl,--no-undefined, every
# Python symbol is undefined. Name libpython explicitly; LDFLAGS already has -L for it.
set -euo pipefail
lib=python$(python3 -c "import sysconfig; print(sysconfig.get_config_var('LDVERSION'))")
if ! grep -q -- "-l$lib" src/meson.build; then
    sed -i "s/  dependencies: \[pybind11_dep\],/  dependencies: [pybind11_dep],\n  link_args: ['-l$lib'],/" src/meson.build
fi
grep -n "link_args" src/meson.build
