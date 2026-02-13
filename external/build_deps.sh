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

# -----------------------------------------------------------------------------
# Function: Build FFmpeg
# -----------------------------------------------------------------------------
build_ffmpeg() {
    local ABI=$1
    echo "Building FFmpeg for $ABI..."
    
    local BUILD_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    
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
        --enable-jni \
        --enable-mediacodec \
        --extra-cflags="-fPIC" \
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
    
    if [ "$TARGET_LIB" == "all" ] || [ "$TARGET_LIB" == "ffmpeg" ]; then
        build_ffmpeg "$ABI"
    fi
    
    if [ "$TARGET_LIB" == "all" ] || [ "$TARGET_LIB" == "libopenmpt" ]; then
        build_libopenmpt "$ABI"
    fi
done

echo "Build complete!"
