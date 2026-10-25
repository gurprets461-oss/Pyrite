#!/usr/bin/env bash
# Builds Pillow's C libraries for Android as static libraries in $PILLOW_DEPS.
#
# Runs as cibuildwheel's before-build, so CC, CFLAGS, AR and CMAKE_TOOLCHAIN_FILE
# already target Android. zlib is Android's own libz.so (a stable NDK library that
# auditwheel allows), so only its headers and link stub are copied in.
set -euo pipefail

DEPS=${PILLOW_DEPS:?}
if [ -e "$DEPS/.done-$CIBW_HOST_TRIPLET" ]; then exit 0; fi
rm -rf "$DEPS"
mkdir -p "$DEPS/include" "$DEPS/lib"
work=$(mktemp -d)

JPEGTURBO=3.2.0
FREETYPE=2.14.3
LIBWEBP=1.6.0

fetch() {  # url... : downloads the first URL that works and unpacks it into $work
    local url
    for url in "$@"; do
        if curl -fsSL --retry 3 "$url" -o "$work/src.tar.gz"; then
            tar -xzf "$work/src.tar.gz" -C "$work" && rm "$work/src.tar.gz"
            return
        fi
    done
    echo "download failed: $*" >&2
    exit 1
}

cmake_install() {  # source dir, extra cmake args...
    local src=$1
    shift
    cmake -S "$src" -B "$src/_build" -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$DEPS" -DCMAKE_INSTALL_LIBDIR=lib \
        -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON "$@"
    cmake --build "$src/_build" -j"$(nproc)"
    cmake --install "$src/_build"
}

# zlib: the NDK's headers and libz.so stub for this API level.
libz=$($CC -print-file-name=libz.so)
case $libz in
    /*/usr/lib/*) sysroot=${libz%/usr/lib/*} ;;
    *) echo "can't find the NDK's libz.so (got '$libz')" >&2; exit 1 ;;
esac
cp "$sysroot/usr/include/zlib.h" "$sysroot/usr/include/zconf.h" "$DEPS/include/"
ln -sf "$libz" "$DEPS/lib/libz.so"

fetch "https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/$JPEGTURBO/libjpeg-turbo-$JPEGTURBO.tar.gz"
cmake_install "$work/libjpeg-turbo-$JPEGTURBO" -DENABLE_SHARED=OFF -DENABLE_STATIC=ON \
    -DWITH_TURBOJPEG=OFF -DWITH_TOOLS=OFF -DWITH_TESTS=OFF

fetch "https://download.savannah.gnu.org/releases/freetype/freetype-$FREETYPE.tar.gz" \
    "https://downloads.sourceforge.net/project/freetype/freetype2/$FREETYPE/freetype-$FREETYPE.tar.gz"
cmake_install "$work/freetype-$FREETYPE" -DFT_DISABLE_ZLIB=TRUE -DFT_DISABLE_BZIP2=TRUE \
    -DFT_DISABLE_PNG=TRUE -DFT_DISABLE_HARFBUZZ=TRUE -DFT_DISABLE_BROTLI=TRUE

fetch "https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-$LIBWEBP.tar.gz"
# On Android, libwebp builds the NDK's cpufeatures library for runtime CPU detection.
cmake_install "$work/libwebp-$LIBWEBP" -DANDROID_NDK="${CC%/toolchains/*}" -DWEBP_BUILD_ANIM_UTILS=OFF -DWEBP_BUILD_CWEBP=OFF \
    -DWEBP_BUILD_DWEBP=OFF -DWEBP_BUILD_GIF2WEBP=OFF -DWEBP_BUILD_IMG2WEBP=OFF \
    -DWEBP_BUILD_VWEBP=OFF -DWEBP_BUILD_WEBPINFO=OFF -DWEBP_BUILD_WEBPMUX=OFF \
    -DWEBP_BUILD_EXTRAS=OFF -DWEBP_BUILD_LIBWEBPMUX=ON
# Pillow links webp, webpmux and webpdemux only (it adds sharpyuv just on iOS), so
# fold libsharpyuv and cpufeatures into libwebp.a to resolve their symbols.
for extra in sharpyuv cpufeatures-webp; do
    mkdir "$work/$extra"
    (cd "$work/$extra" && $AR x "$DEPS/lib/lib$extra.a" && $AR rcs "$DEPS/lib/libwebp.a" ./*.o)
done

ls -l "$DEPS/lib" "$DEPS/include"
rm -rf "$work"
touch "$DEPS/.done-$CIBW_HOST_TRIPLET"
