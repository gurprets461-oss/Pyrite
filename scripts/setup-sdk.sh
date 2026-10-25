#!/usr/bin/env bash
# Installs the Android SDK pieces Pyrite needs, non-interactively:
# command-line tools, platform-tools, platform, build-tools, NDK and CMake.
#
#   scripts/setup-sdk.sh                 # SDK in $ANDROID_HOME, or ~/Android/Sdk
#   scripts/setup-sdk.sh --with-emulator # also the emulator + an x86_64 system image
#
# Reuses whatever is already installed (e.g. on GitHub's runners) and writes
# local.properties so Gradle finds the SDK. Needs: curl, unzip, python3, Java 17+.
set -euo pipefail

CMDLINE_TOOLS_VERSION=23.0
PACKAGES=(
    "platform-tools"
    "platforms;android-37.0"
    "build-tools;37.0.0"
    # Same NDK CPython's Android build uses (Platforms/Android/android-env.sh).
    "ndk;27.3.13750724"
    "cmake;3.31.5"
)
EMULATOR_PACKAGES=(
    "emulator"
    "system-images;android-35;google_apis;x86_64"
)

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
WITH_EMULATOR=0
for arg in "$@"; do
    case $arg in
        --with-emulator) WITH_EMULATOR=1 ;;
        *) echo "unknown argument: $arg" >&2; exit 2 ;;
    esac
done

log() { echo "setup-sdk: $*" >&2; }

if ! command -v java >/dev/null; then
    log "Java 17+ is required (e.g. apt install openjdk-21-jdk-headless)"
    exit 1
fi

mkdir -p "$SDK"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
    case "$(uname -s)" in
        Linux) host=linux ;;
        Darwin) host=macosx ;;
        *) log "unsupported host OS $(uname -s)"; exit 1 ;;
    esac
    log "installing cmdline-tools $CMDLINE_TOOLS_VERSION into $SDK"
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT
    # The repository manifest lists each archive with its SHA-1, which is what
    # sdkmanager itself verifies against.
    curl -fsSL --retry 4 -o "$tmp/repo.xml" https://dl.google.com/android/repository/repository2-3.xml
    read -r zip sha1 < <(python3 - "$tmp/repo.xml" "$CMDLINE_TOOLS_VERSION" "$host" <<'EOF'
import sys, xml.etree.ElementTree as ET
path, version, host = sys.argv[1:]
for pkg in ET.parse(path).getroot().iter("remotePackage"):
    if pkg.get("path") != f"cmdline-tools;{version}":
        continue
    for archive in pkg.iter("archive"):
        os_el = archive.find("host-os")
        if os_el is not None and os_el.text == host:
            comp = archive.find("complete")
            print(comp.findtext("url"), comp.findtext("checksum"))
            sys.exit(0)
sys.exit(f"cmdline-tools;{version} for {host} not found in repository manifest")
EOF
    )
    if [ -z "${zip:-}" ] || [ -z "${sha1:-}" ]; then
        log "could not find cmdline-tools $CMDLINE_TOOLS_VERSION in the repository manifest"
        exit 1
    fi
    curl -fSL --retry 4 -o "$tmp/$zip" "https://dl.google.com/android/repository/$zip"
    actual=$( (command -v sha1sum >/dev/null && sha1sum "$tmp/$zip" || shasum -a 1 "$tmp/$zip") | cut -d' ' -f1)
    if [ "$actual" != "$sha1" ]; then
        log "checksum mismatch for $zip: expected $sha1, got $actual"
        exit 1
    fi
    unzip -q "$tmp/$zip" -d "$tmp/unzipped"
    mkdir -p "$SDK/cmdline-tools"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$tmp/unzipped/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

log "accepting licenses"
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true

want=("${PACKAGES[@]}")
if [ "$WITH_EMULATOR" = 1 ]; then
    want+=("${EMULATOR_PACKAGES[@]}")
fi
log "installing: ${want[*]}"
"$SDKMANAGER" --sdk_root="$SDK" --install "${want[@]}" > >(grep -v -E '^\[=*' || true)

# Gradle reads sdk.dir from local.properties when ANDROID_HOME isn't exported.
props="$ROOT/local.properties"
if ! grep -qs '^sdk.dir=' "$props"; then
    echo "sdk.dir=$SDK" >> "$props"
fi

log "done. SDK at $SDK"
if [ -z "${ANDROID_HOME:-}" ]; then
    log "tip: export ANDROID_HOME=$SDK"
fi
