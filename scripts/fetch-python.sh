#!/usr/bin/env bash
# Downloads the official CPython Android embeddable packages from python.org,
# verifies their SHA-256 checksums, and unpacks them into the same layout the
# CPython source tree uses for its testbed:
#
#   cross-build/<triplet>/prefix/{include,lib}/...
#
# Re-running is cheap: packages are cached in .cache/downloads and only
# re-extracted when the version or checksum changes.
#
# To move to a new Python release, update PY_VERSION and the checksums below.
# The checksums are published by python.org's release API, e.g.:
#   curl -s 'https://www.python.org/api/v2/downloads/release_file/?release=<id>' \
#     | python3 -c 'import json,sys; [print(f["url"], f["sha256_sum"]) for f in json.load(sys.stdin) if "android" in f["url"]]'
set -euo pipefail

PY_VERSION=3.15.1
declare -A SHA256=(
    [aarch64-linux-android]=04e47e4b058845faed3f7260b3ffb7e33321126c9bc1859523891dfa791b9ab4
    [x86_64-linux-android]=0633782596c9252464ae4e14743ed31a3788173a415ceba5a1c5c2122e41bf32
)
BASE_URL=${PYTHON_ANDROID_BASE_URL:-https://www.python.org/ftp/python}

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CACHE=${PYRITE_DOWNLOAD_CACHE:-$ROOT/.cache/downloads}
DEST=$ROOT/cross-build

log() { echo "fetch-python: $*" >&2; }

sha256_of() {
    if command -v sha256sum >/dev/null; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

mkdir -p "$CACHE" "$DEST"

for triplet in "${!SHA256[@]}"; do
    expected=${SHA256[$triplet]}
    file="python-$PY_VERSION-$triplet.tar.gz"
    archive="$CACHE/$file"
    target="$DEST/$triplet"
    stamp="$target/.pyrite-stamp"

    if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$PY_VERSION $expected" ]; then
        log "$triplet: $PY_VERSION already unpacked"
        continue
    fi

    if [ -f "$archive" ] && [ "$(sha256_of "$archive")" = "$expected" ]; then
        log "$triplet: using cached $file"
    else
        log "$triplet: downloading $BASE_URL/$PY_VERSION/$file"
        curl -fSL --retry 4 --retry-delay 2 -o "$archive.part" "$BASE_URL/$PY_VERSION/$file"
        mv "$archive.part" "$archive"
    fi

    actual=$(sha256_of "$archive")
    if [ "$actual" != "$expected" ]; then
        log "$triplet: CHECKSUM MISMATCH for $file"
        log "  expected $expected"
        log "  actual   $actual"
        rm -f "$archive"
        exit 1
    fi
    log "$triplet: sha256 OK"

    rm -rf "$target"
    mkdir -p "$target"
    tar -xzf "$archive" -C "$target"
    if [ ! -f "$target/prefix/lib/libpython${PY_VERSION%.*}.so" ]; then
        log "$triplet: unexpected package layout (no prefix/lib/libpython${PY_VERSION%.*}.so)"
        exit 1
    fi
    echo "$PY_VERSION $expected" > "$stamp"
    log "$triplet: unpacked into ${target#"$ROOT"/}"
done

echo "$PY_VERSION" > "$DEST/python-version.txt"
log "done: Python $PY_VERSION for ${!SHA256[*]}"
