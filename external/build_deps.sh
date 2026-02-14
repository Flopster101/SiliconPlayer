#!/bin/bash
set -e

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
NDK_VERSION="29.0.14206865" # From local.properties/source.properties
ANDROID_API=26 # minSdk

# Auto-detect NDK if not set
if [ -z "$ANDROID_NDK_HOME" ]; then
    if [ -d "/opt/android-ndk" ]; then
        export ANDROID_NDK_HOME="/opt/android-ndk"
    elif [ -d "$HOME/Android/Sdk/ndk/$NDK_VERSION" ]; then
        export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/$NDK_VERSION"
    else
        echo "Error: ANDROID_NDK_HOME not set and could not be found."
        echo "Please export ANDROID_NDK_HOME=/path/to/ndk"
        exit 1
    fi
fi

echo "Using NDK: $ANDROID_NDK_HOME"

# Host detection (linux-x86_64, darwin-x86_64, etc)
HOST_TAG="linux-x86_64"
if [[ "$OSTYPE" == "darwin"* ]]; then
    HOST_TAG="darwin-x86_64"
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
SYSROOT="$TOOLCHAIN/sysroot"
export PATH="$TOOLCHAIN/bin:$PATH"

# Architecture independent variables
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
ABSOLUTE_PATH="$SCRIPT_DIR"
PATCHES_DIR="$ABSOLUTE_PATH/patches/libopenmpt"

# -----------------------------------------------------------------------------
# Function: Apply libopenmpt patches (idempotent)
# -----------------------------------------------------------------------------
apply_libopenmpt_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libopenmpt"
    if [ ! -d "$PATCHES_DIR" ]; then
        return
    fi

    for patch_file in "$PATCHES_DIR"/*.patch; do
        [ -e "$patch_file" ] || continue
        local patch_name
        patch_name="$(basename "$patch_file")"

        # Check if patch is already applied by looking for the commit subject in git log
        # Extract subject from patch file (Subject: ...)
        local subject
        subject=$(grep "^Subject: " "$patch_file" | sed 's/^Subject: \[PATCH[^]]*\] //')
        
        if git -C "$PROJECT_PATH" log -1 --grep="$subject" >/dev/null 2>&1; then
             echo "libopenmpt patch already applied: $patch_name"
        else
             echo "Applying libopenmpt patch: $patch_name"
             git -C "$PROJECT_PATH" am "$patch_file" || {
                 echo "Error applying patch $patch_name"
                 git -C "$PROJECT_PATH" am --abort
                 exit 1
             }
        fi
    done
}

# -----------------------------------------------------------------------------
# Function: Build libsoxr (optional, if source is present)
# -----------------------------------------------------------------------------
build_libsoxr() {
    local ABI=$1
    local PROJECT_PATH="$ABSOLUTE_PATH/libsoxr"
    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "libsoxr source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    echo "Building libsoxr for $ABI..."
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

    cmake \
        -S "$PROJECT_PATH" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_TESTS=OFF \
        -DBUILD_EXAMPLES=OFF \
        -DWITH_OPENMP=OFF \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

    cmake --build "$BUILD_DIR" -j$(nproc)
    cmake --install "$BUILD_DIR"

    if [ ! -f "$INSTALL_DIR/lib/libsoxr.a" ]; then
        local built_lib
        built_lib="$(find "$BUILD_DIR" -type f -name libsoxr.a | head -n 1)"
        if [ -n "$built_lib" ]; then
            mkdir -p "$INSTALL_DIR/lib"
            cp "$built_lib" "$INSTALL_DIR/lib/libsoxr.a"
        fi
    fi
}

# -----------------------------------------------------------------------------
# Function: Build FFmpeg
# -----------------------------------------------------------------------------
build_ffmpeg() {
    local ABI=$1
    echo "Building FFmpeg for $ABI..."

    local BUILD_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local SOXR_CFLAGS=""
    local SOXR_LDFLAGS=""
    local SOXR_EXTRA_LIBS=""
    local SOXR_ENABLE_FLAG=""
    local FFMPEG_EXTRA_CFLAGS="-fPIC"

    mkdir -p "$BUILD_DIR"

    cd "$ABSOLUTE_PATH/ffmpeg"

    # Always clean
    make clean >/dev/null 2>&1 || true
    make distclean >/dev/null 2>&1 || true

    # Configure flags
    EXTRA_FLAGS=""
    if [ "$ABI" = "x86" ] || [ "$ABI" = "x86_64" ]; then
        EXTRA_FLAGS="--disable-asm"
    fi
    if [ "$ABI" = "arm64-v8a" ]; then
        # Disable ASM for arm64 to avoid relocation R_AARCH64_ADR_PREL_PG_HI21 errors
        # in tx_float_neon.S when linking static lib into shared lib.
        EXTRA_FLAGS="--disable-asm"
    fi

    export ASFLAGS="-fPIC"

    if [ -f "$BUILD_DIR/lib/libsoxr.a" ] && [ -f "$BUILD_DIR/include/soxr.h" ]; then
        echo "libsoxr detected for $ABI -> enabling FFmpeg libsoxr support"
        SOXR_ENABLE_FLAG="--enable-libsoxr"
        SOXR_CFLAGS="-I$BUILD_DIR/include"
        SOXR_LDFLAGS="-L$BUILD_DIR/lib"
        SOXR_EXTRA_LIBS="-lsoxr -lm"
        FFMPEG_EXTRA_CFLAGS="$FFMPEG_EXTRA_CFLAGS $SOXR_CFLAGS"
    else
        echo "libsoxr not available for $ABI -> FFmpeg will use built-in swr resampler only"
    fi

    ./configure \
        --target-os=android \
        --arch=$ARCH \
        --cpu=$CPU \
        --cc="$CC" \
        --cxx="$CXX" \
        --ar="$AR" \
        --strip="$STRIP" \
        --nm="$NM" \
        --prefix="$BUILD_DIR" \
        --enable-cross-compile \
        --sysroot="$SYSROOT" \
        --enable-static \
        --disable-shared \
        --enable-pic \
        --disable-doc \
        --disable-programs \
        --disable-avdevice \
        --disable-swscale \
        --disable-avfilter \
        --disable-network \
        --disable-hwaccels \
        --disable-encoders \
        --disable-muxers \
        --disable-decoder=h264 \
        --disable-decoder=hevc \
        --disable-decoder=vp8 \
        --disable-decoder=vp9 \
        --disable-decoder=av1 \
        --disable-decoder=mpeg4 \
        --disable-decoder=mpeg1video \
        --disable-decoder=mpeg2video \
        --enable-swresample \
        --enable-gpl \
        --enable-version3 \
        $SOXR_ENABLE_FLAG \
        --enable-jni \
        --enable-mediacodec \
        --extra-cflags="$FFMPEG_EXTRA_CFLAGS" \
        --extra-ldflags="$SOXR_LDFLAGS" \
        --extra-libs="$SOXR_EXTRA_LIBS" \
        $EXTRA_FLAGS || { echo "Error: FFmpeg configure failed!"; exit 1; }

    make -j$(nproc)
    make install

    cd ..
}

# -----------------------------------------------------------------------------
# Function: Build libopenmpt
# -----------------------------------------------------------------------------
build_libopenmpt() {
    local ABI=$1
    echo "Building libopenmpt for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/libopenmpt"

    mkdir -p "$INSTALL_DIR"
    mkdir -p "$INSTALL_DIR"

    # Copy Android.mk/Application.mk to root if not present
    if [ ! -f "$PROJECT_PATH/Android.mk" ]; then
        echo "Copying Android.mk to libopenmpt root..."
        cp "$PROJECT_PATH/build/android_ndk/Android.mk" "$PROJECT_PATH/"
        cp "$PROJECT_PATH/build/android_ndk/Application.mk" "$PROJECT_PATH/"

        # Patch to static library
        sed -i 's/BUILD_SHARED_LIBRARY/BUILD_STATIC_LIBRARY/g' "$PROJECT_PATH/Android.mk"
    fi

    # Use ndk-build
    "$ANDROID_NDK_HOME/ndk-build" \
        -C "$PROJECT_PATH" \
        NDK_PROJECT_PATH="$PROJECT_PATH" \
        NDK_APPLICATION_MK="$PROJECT_PATH/Application.mk" \
        APP_BUILD_SCRIPT="$PROJECT_PATH/Android.mk" \
        APP_ABI="$ABI" \
        APP_PLATFORM="android-$ANDROID_API" \
        NDK_LIBS_OUT="$INSTALL_DIR/lib" \
        NDK_OUT="$PROJECT_PATH/obj/$ABI" \
        MPT_WITH_MINIMP3=1 \
        MPT_WITH_STBVORBIS=1 \
        -j$(nproc)

    # Copy static library manually
    # ndk-build puts static libs in obj/local/$ABI/libopenmpt.a (relative to NDK_OUT)
    # Our NDK_OUT is $PROJECT_PATH/obj/$ABI
    # So it should be at $PROJECT_PATH/obj/$ABI/local/$ABI/libopenmpt.a
    mkdir -p "$INSTALL_DIR/lib/$ABI"
    cp "$PROJECT_PATH/obj/$ABI/local/$ABI/libopenmpt.a" "$INSTALL_DIR/lib/$ABI/" || echo "Failed to copy libopenmpt.a"

    # Copy headers manually since ndk-build might not install them nicely
    # libopenmpt headers are in libopenmpt/ directory
    mkdir -p "$INSTALL_DIR/include/libopenmpt"
    cp "$ABSOLUTE_PATH/libopenmpt/libopenmpt/libopenmpt.h" "$INSTALL_DIR/include/libopenmpt/"
    cp "$ABSOLUTE_PATH/libopenmpt/libopenmpt/libopenmpt.hpp" "$INSTALL_DIR/include/libopenmpt/"
    cp "$ABSOLUTE_PATH/libopenmpt/libopenmpt/libopenmpt_config.h" "$INSTALL_DIR/include/libopenmpt/"
    cp "$ABSOLUTE_PATH/libopenmpt/libopenmpt/libopenmpt_version.h" "$INSTALL_DIR/include/libopenmpt/"
    # Also stream callbacks?
    cp "$ABSOLUTE_PATH/libopenmpt/libopenmpt/libopenmpt_stream_callbacks_file.h" "$INSTALL_DIR/include/libopenmpt/" || true
    cp "$ABSOLUTE_PATH/libopenmpt/libopenmpt/libopenmpt_stream_callbacks_fd.h" "$INSTALL_DIR/include/libopenmpt/" || true
}

# -----------------------------------------------------------------------------
# Argument Parsing
# -----------------------------------------------------------------------------
TARGET_ABI=${1:-all}
TARGET_LIB=${2:-all}

normalize_lib_name() {
    local lib="$1"
    case "$lib" in
        sox|soxr)
            echo "libsoxr"
            ;;
        *)
            echo "$lib"
            ;;
    esac
}

target_has_lib() {
    local wanted
    wanted="$(normalize_lib_name "$1")"

    if [ "$TARGET_LIB" = "all" ]; then
        return 0
    fi

    IFS=',' read -r -a requested_libs <<< "$TARGET_LIB"
    for raw in "${requested_libs[@]}"; do
        local item
        item="$(echo "$raw" | xargs)"
        item="$(normalize_lib_name "$item")"
        if [ "$item" = "$wanted" ]; then
            return 0
        fi
    done
    return 1
}

# -----------------------------------------------------------------------------
# Pre-build setup (Apply patches once)
# -----------------------------------------------------------------------------
if target_has_lib "libopenmpt"; then
    apply_libopenmpt_patches
fi

# -----------------------------------------------------------------------------
# Main Loop
# -----------------------------------------------------------------------------
for ABI in "${ABIS[@]}"; do
    if [ "$TARGET_ABI" != "all" ] && [ "$TARGET_ABI" != "$ABI" ]; then
        continue
    fi

    echo "========================================"
    echo "Processing ABI: $ABI"
    echo "========================================"

    # Setup Architecture specific flags
    case $ABI in
        "arm64-v8a")
            ARCH="aarch64"
            CPU="armv8-a"
            TRIPLE="aarch64-linux-android"
            ;;
        "armeabi-v7a")
            ARCH="arm"
            CPU="armv7-a"
            TRIPLE="armv7a-linux-androideabi"
            ;;
        "x86_64")
            ARCH="x86_64"
            CPU="x86-64"
            TRIPLE="x86_64-linux-android"
            ;;
        "x86")
            ARCH="x86"
            CPU="i686"
            TRIPLE="i686-linux-android"
            ;;
    esac

    export CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang -fPIC"
    export CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++ -fPIC"
    export AR="$TOOLCHAIN/bin/llvm-ar"
    export LD="$TOOLCHAIN/bin/ld"
    export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$TOOLCHAIN/bin/llvm-strip"
    export NM="$TOOLCHAIN/bin/llvm-nm"

    echo "CC is set to: $CC"

    if target_has_lib "libsoxr"; then
        build_libsoxr "$ABI"
    fi

    if target_has_lib "ffmpeg"; then
        build_ffmpeg "$ABI"
    fi

    if target_has_lib "libopenmpt"; then
        build_libopenmpt "$ABI"
    fi
done

echo "Build complete!"
