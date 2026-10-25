#!/usr/bin/env bash
# Builds one package's Android wheel (CPython 3.15) from its PyPI sdist with cibuildwheel.
#
# Usage: wheels/build.sh <package> <arm64_v8a|x86_64> [output-dir]
# Needs ANDROID_HOME (cibuildwheel installs the NDK version Python wants) and Linux
# x86_64 or macOS. Versions come from wheels/versions.txt.
set -euo pipefail

pkg=$1
arch=$2
out=$(mkdir -p "${3:-wheelhouse}" && cd "${3:-wheelhouse}" && pwd)
here=$(cd "$(dirname "$0")" && pwd)
version=$(awk -v p="$pkg" '$1 == p { print $2 }' "$here/versions.txt")
[ -n "$version" ] || { echo "$pkg isn't in versions.txt" >&2; exit 1; }

work=$(mktemp -d)
sdist=$(python3 "$here/fetch_sdist.py" "$pkg" "$version" "$work")

export CIBW_PLATFORM=android
export CIBW_ARCHS=$arch
export CIBW_BUILD="cp315-*"
export CIBW_BUILD_VERBOSITY=1
# Upstream before-all scripts (e.g. Pillow's) live outside the sdist.
export CIBW_BEFORE_ALL=""
# Each project's own test command assumes a source checkout or a desktop; the app's
# instrumented tests exercise these wheels on a device instead.
export CIBW_TEST_COMMAND=""

case $pkg in
    numpy)
        # No BLAS/LAPACK for Android: numpy's own fallback routines. Skip the
        # upstream before-build, which downloads scipy-openblas.
        export CIBW_BEFORE_BUILD=""
        # The extra cross file supplies the long double format numpy can't probe.
        export CIBW_CONFIG_SETTINGS="setup-args=-Dblas=none setup-args=-Dlapack=none setup-args=--cross-file=$here/android-longdouble.cross build-dir=build"
        ;;
    pillow)
        deps=$work/pillow-deps
        export CIBW_BEFORE_BUILD="bash $here/pillow-deps.sh"
        export CIBW_ENVIRONMENT="PILLOW_DEPS=$deps ZLIB_ROOT=$deps JPEG_ROOT=$deps FREETYPE_ROOT=$deps WEBP_ROOT=$deps"
        # Like upstream's iOS build: no system-path guessing, no raqm/imagequant.
        export CIBW_CONFIG_SETTINGS="raqm=disable imagequant=disable platform-guessing=disable"
        ;;
    matplotlib)
        # Upstream's before-build only cleans a stale build dir. FreeType, Qhull and
        # the text-shaping libraries are built from meson subprojects.
        export CIBW_BEFORE_BUILD=""
        ;;
    contourpy)
        export CIBW_BEFORE_BUILD="bash $here/contourpy-before-build.sh"
        ;;
    kiwisolver) ;;
    *)
        echo "no build recipe for $pkg" >&2
        exit 1
        ;;
esac

python3 -m cibuildwheel --output-dir "$out" "$sdist"
rm -rf "$work"
ls -l "$out"
