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
PATCHES_DIR_LIBGME="$ABSOLUTE_PATH/patches/libgme"
OPENSSL_DIR="$ABSOLUTE_PATH/openssl"

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
        local patch_subject
        patch_subject="$(sed -n 's/^Subject: \[PATCH\] //p' "$patch_file" | head -n 1)"

        # Secondary idempotency check:
        # If the patch subject already exists in git history, treat it as applied.
        # This avoids false negatives from reverse-apply checks when later patches
        # touched nearby context lines.
        if [ -n "$patch_subject" ] && git -C "$PROJECT_PATH" log --format=%s | grep -Fxq "$patch_subject"; then
            echo "libopenmpt patch already applied (subject): $patch_name"
            continue
        fi

        # Reliable idempotency check:
        # If reverse-apply check succeeds, patch content is already present.
        if git -C "$PROJECT_PATH" apply --check --reverse "$patch_file" >/dev/null 2>&1; then
            echo "libopenmpt patch already applied: $patch_name"
            continue
        fi

        echo "Applying libopenmpt patch: $patch_name"
        git -C "$PROJECT_PATH" am "$patch_file" || {
            echo "Error applying patch $patch_name"
            git -C "$PROJECT_PATH" am --abort
            exit 1
        }
    done
}

# -----------------------------------------------------------------------------
# Function: Apply libvgm patches (idempotent)
# -----------------------------------------------------------------------------
apply_libvgm_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libvgm"
    local PATCHES_DIR_VGM="$ABSOLUTE_PATH/patches/libvgm"
    
    if [ ! -d "$PATCHES_DIR_VGM" ]; then
        return
    fi

    for patch_file in "$PATCHES_DIR_VGM"/*.patch; do
        [ -e "$patch_file" ] || continue
        local patch_name
        patch_name="$(basename "$patch_file")"

        # Reliable idempotency check:
        # If reverse-apply check succeeds, patch content is already present.
        if git -C "$PROJECT_PATH" apply --check --reverse "$patch_file" >/dev/null 2>&1; then
            echo "libvgm patch already applied: $patch_name"
            continue
        fi

        echo "Applying libvgm patch: $patch_name"
        git -C "$PROJECT_PATH" am "$patch_file" || {
            echo "Error applying patch $patch_name"
            git -C "$PROJECT_PATH" am --abort
            exit 1
        }
    done
}

# -----------------------------------------------------------------------------
# Function: Apply libgme patches (idempotent)
# -----------------------------------------------------------------------------
apply_libgme_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libgme"
    if [ ! -d "$PATCHES_DIR_LIBGME" ]; then
        return
    fi

    for patch_file in "$PATCHES_DIR_LIBGME"/*.patch; do
        [ -e "$patch_file" ] || continue
        local patch_name
        patch_name="$(basename "$patch_file")"

        # If reverse-apply check succeeds, patch content is already present.
        if git -C "$PROJECT_PATH" apply --check --reverse "$patch_file" >/dev/null 2>&1; then
            echo "libgme patch already applied: $patch_name"
            continue
        fi

        echo "Applying libgme patch: $patch_name"
        git -C "$PROJECT_PATH" am "$patch_file" || {
            echo "Error applying patch $patch_name"
            git -C "$PROJECT_PATH" am --abort
            exit 1
        }
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
# Function: Build OpenSSL (required for FFmpeg HTTPS/TLS)
# -----------------------------------------------------------------------------
build_openssl() {
    local ABI=$1
    local PROJECT_PATH="$OPENSSL_DIR"
    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local OPENSSL_TARGET=""
    local OPENSSL_BUILD_SIGNATURE="openssl-android-static-pic-noasm-v1"
    local OPENSSL_STAMP_FILE="$INSTALL_DIR/lib/.openssl_build_signature"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "OpenSSL source not found at $PROJECT_PATH."
        echo "Clone it first: git clone https://github.com/openssl/openssl.git $PROJECT_PATH"
        return 1
    fi

    if [ -f "$INSTALL_DIR/lib/libssl.a" ] && [ -f "$INSTALL_DIR/lib/libcrypto.a" ] && [ -f "$INSTALL_DIR/include/openssl/ssl.h" ] && [ -f "$OPENSSL_STAMP_FILE" ]; then
        if [ "$(cat "$OPENSSL_STAMP_FILE")" = "$OPENSSL_BUILD_SIGNATURE" ]; then
            echo "OpenSSL already built for $ABI -> skipping"
            return 0
        fi
    fi

    case "$ABI" in
        "arm64-v8a")
            OPENSSL_TARGET="android-arm64"
            ;;
        "armeabi-v7a")
            OPENSSL_TARGET="android-arm"
            ;;
        "x86_64")
            OPENSSL_TARGET="android-x86_64"
            ;;
        "x86")
            OPENSSL_TARGET="android-x86"
            ;;
        *)
            echo "Unsupported ABI for OpenSSL: $ABI"
            return 1
            ;;
    esac

    echo "Building OpenSSL for $ABI ($OPENSSL_TARGET)..."

    mkdir -p "$INSTALL_DIR"
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"

    cd "$PROJECT_PATH"

    make clean >/dev/null 2>&1 || true
    rm -f configdata.pm

    export CFLAGS="-fPIC"
    export CXXFLAGS="-fPIC"

    perl ./Configure "$OPENSSL_TARGET" \
        no-tests \
        no-asm \
        no-shared \
        no-module \
        no-engine \
        no-apps \
        no-docs \
        no-ui-console \
        --prefix="$INSTALL_DIR" \
        --openssldir="$INSTALL_DIR/ssl" \
        -D__ANDROID_API__="$ANDROID_API" || {
            echo "Error: OpenSSL configure failed!"
            exit 1
        }

    make -j$(nproc)
    make install_sw
    mkdir -p "$INSTALL_DIR/lib"
    echo "$OPENSSL_BUILD_SIGNATURE" > "$OPENSSL_STAMP_FILE"

    cd "$ABSOLUTE_PATH"
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
    local OPENSSL_CFLAGS=""
    local OPENSSL_LDFLAGS=""
    local OPENSSL_EXTRA_LIBS=""
    local OPENSSL_ENABLE_FLAG=""
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

    if [ -f "$BUILD_DIR/lib/libssl.a" ] && [ -f "$BUILD_DIR/lib/libcrypto.a" ] && [ -f "$BUILD_DIR/include/openssl/ssl.h" ]; then
        echo "OpenSSL detected for $ABI -> enabling FFmpeg HTTPS/TLS support"
        OPENSSL_ENABLE_FLAG="--enable-openssl"
        OPENSSL_CFLAGS="-I$BUILD_DIR/include"
        OPENSSL_LDFLAGS="-L$BUILD_DIR/lib"
        OPENSSL_EXTRA_LIBS="-lssl -lcrypto -ldl -lz"
        FFMPEG_EXTRA_CFLAGS="$FFMPEG_EXTRA_CFLAGS $OPENSSL_CFLAGS"
    else
        echo "OpenSSL not available for $ABI -> FFmpeg HTTPS/TLS protocols will be unavailable"
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
        --enable-network \
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
        $SOXR_ENABLE_FLAG \
        $OPENSSL_ENABLE_FLAG \
        --enable-jni \
        --enable-mediacodec \
        --extra-cflags="$FFMPEG_EXTRA_CFLAGS" \
        --extra-ldflags="$SOXR_LDFLAGS $OPENSSL_LDFLAGS" \
        --extra-libs="$SOXR_EXTRA_LIBS $OPENSSL_EXTRA_LIBS" \
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

    # Always Copy Android.mk/Application.mk to root to ensure we have latest source list
    echo "Copying Android.mk to libopenmpt root..."
    cp "$PROJECT_PATH/build/android_ndk/Android.mk" "$PROJECT_PATH/"
    cp "$PROJECT_PATH/build/android_ndk/Application.mk" "$PROJECT_PATH/"

    # Patch to static library
    sed -i 's/BUILD_SHARED_LIBRARY/BUILD_STATIC_LIBRARY/g' "$PROJECT_PATH/Android.mk"

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
# Function: Build libvgm
# -----------------------------------------------------------------------------
build_libvgm() {
    local ABI=$1
    echo "Building libvgm for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/libvgm"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "libvgm source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

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
        -DLIBRARY_TYPE=STATIC \
        -DBUILD_LIBAUDIO=OFF \
        -DBUILD_LIBEMU=ON \
        -DBUILD_LIBPLAYER=ON \
        -DBUILD_TESTS=OFF \
        -DBUILD_PLAYER=OFF \
        -DBUILD_VGM2WAV=OFF \
        -DUSE_SANITIZERS=OFF \
        -DUTIL_CHARSET_CONV=OFF \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

    cmake --build "$BUILD_DIR" -j$(nproc)
    cmake --install "$BUILD_DIR"
}

# -----------------------------------------------------------------------------
# Function: Build libgme
# -----------------------------------------------------------------------------
build_libgme() {
    local ABI=$1
    echo "Building libgme for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/libgme"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "libgme source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

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
        -DGME_BUILD_SHARED=OFF \
        -DGME_BUILD_STATIC=ON \
        -DGME_BUILD_TESTING=OFF \
        -DGME_BUILD_EXAMPLES=OFF \
        -DGME_ZLIB=ON \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

    cmake --build "$BUILD_DIR" -j$(nproc)
    cmake --install "$BUILD_DIR"
}

# -----------------------------------------------------------------------------
# Function: Build lazyusf2
# -----------------------------------------------------------------------------
build_lazyusf2() {
    local ABI=$1
    echo "Building lazyusf2 for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/lazyusf2"
    local LIB_OUTPUT=""

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "lazyusf2 source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include/lazyusf2"

    echo "lazyusf2: using Makefile build for ABI ($ABI)."
    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true

        case "$ABI" in
            "arm64-v8a")
                make -j$(nproc) liblazyusf.a \
                    CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
                    AR="$TOOLCHAIN/bin/llvm-ar" \
                    CPU="AArch64" \
                    ARCH="64" \
                    OBJS_RECOMPILER_64="" \
                    OPTS_AArch64="" \
                    ROPTS_AArch64="-DARCH_MIN_ARM_NEON"
                ;;
            "armeabi-v7a")
                make -j$(nproc) liblazyusf.a \
                    CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
                    AR="$TOOLCHAIN/bin/llvm-ar" \
                    CPU="arm" \
                    ARCH="32" \
                    FLAGS_32="-fPIC" \
                    OBJS_RECOMPILER_32="" \
                    OPTS_arm="" \
                    ROPTS_arm=""
                ;;
            "x86")
                make -j$(nproc) liblazyusf.a \
                    CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
                    AR="$TOOLCHAIN/bin/llvm-ar" \
                    CPU="x86" \
                    ARCH="32" \
                    FLAGS_32="-fPIC -msse -mmmx -msse2" \
                    OBJS_RECOMPILER_32="" \
                    OPTS_x86="" \
                    ROPTS_x86="-DARCH_MIN_SSE2"
                ;;
            "x86_64")
                make -j$(nproc) liblazyusf.a \
                    CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
                    AR="$TOOLCHAIN/bin/llvm-ar" \
                    CPU="x86_64" \
                    ARCH="64" \
                    FLAGS_64="-fPIC" \
                    OBJS_RECOMPILER_64="" \
                    OPTS_x86_64="" \
                    ROPTS_x86_64="-DARCH_MIN_SSE2"
                ;;
            *)
                echo "Unsupported ABI for lazyusf2: $ABI"
                return 1
                ;;
        esac
    )

    LIB_OUTPUT="$PROJECT_PATH/liblazyusf.a"

    if [ -z "$LIB_OUTPUT" ] || [ ! -f "$LIB_OUTPUT" ]; then
        echo "Error: lazyusf2 static library not found after build."
        return 1
    fi
    cp "$LIB_OUTPUT" "$INSTALL_DIR/lib/liblazyusf2.a"
    cp "$LIB_OUTPUT" "$INSTALL_DIR/lib/liblazyusf.a"

    while IFS= read -r header_path; do
        local rel_path
        rel_path="${header_path#"$PROJECT_PATH"/}"
        mkdir -p "$INSTALL_DIR/include/lazyusf2/$(dirname "$rel_path")"
        cp "$header_path" "$INSTALL_DIR/include/lazyusf2/$rel_path"
    done < <(find "$PROJECT_PATH" -type f -name '*.h')
}

# -----------------------------------------------------------------------------
# Function: Build FluidSynth
# -----------------------------------------------------------------------------
build_fluidsynth() {
    local ABI=$1
    echo "Building FluidSynth for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/fluidsynth"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "FluidSynth source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include"

    cmake \
        -S "$PROJECT_PATH" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -Dosal=cpp11 \
        -Denable-alsa=OFF \
        -Denable-aufile=OFF \
        -Denable-dbus=OFF \
        -Denable-ipv6=OFF \
        -Denable-jack=OFF \
        -Denable-ladspa=OFF \
        -Denable-libinstpatch=OFF \
        -Denable-libsndfile=OFF \
        -Denable-midishare=OFF \
        -Denable-network=OFF \
        -Denable-oss=OFF \
        -Denable-dsound=OFF \
        -Denable-wasapi=OFF \
        -Denable-waveout=OFF \
        -Denable-winmidi=OFF \
        -Denable-sdl3=OFF \
        -Denable-pulseaudio=OFF \
        -Denable-pipewire=OFF \
        -Denable-readline=OFF \
        -Denable-threads=ON \
        -Denable-openmp=OFF \
        -Denable-native-dls=OFF \
        -Denable-limiter=OFF \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

    cmake --build "$BUILD_DIR" --target libfluidsynth -j$(nproc)

    if [ -f "$BUILD_DIR/src/libfluidsynth.a" ]; then
        cp "$BUILD_DIR/src/libfluidsynth.a" "$INSTALL_DIR/lib/"
    else
        local built_lib
        built_lib="$(find "$BUILD_DIR" -type f -name 'libfluidsynth.a' | head -n 1)"
        if [ -z "$built_lib" ]; then
            echo "Error: FluidSynth static library not found after build."
            return 1
        fi
        cp "$built_lib" "$INSTALL_DIR/lib/"
    fi

    mkdir -p "$INSTALL_DIR/include/fluidsynth"
    cp "$PROJECT_PATH/include/fluidsynth/"*.h "$INSTALL_DIR/include/fluidsynth/" 2>/dev/null || true
    cp "$BUILD_DIR/include/fluidsynth/"*.h "$INSTALL_DIR/include/fluidsynth/" 2>/dev/null || true
    cp "$BUILD_DIR/include/fluidsynth.h" "$INSTALL_DIR/include/" 2>/dev/null || true
}

# -----------------------------------------------------------------------------
# Function: Build libresidfp
# -----------------------------------------------------------------------------
build_libresidfp() {
    local ABI=$1
    echo "Building libresidfp for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/libresidfp"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local CONFIGURE_HOST=""

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "libresidfp source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ -f "$INSTALL_DIR/lib/libresidfp.a" ] && \
       [ -f "$INSTALL_DIR/include/residfp/residfp.h" ] && \
       [ -f "$INSTALL_DIR/lib/pkgconfig/libresidfp.pc" ]; then
        echo "libresidfp already built for $ABI -> skipping"
        return 0
    fi

    case "$ABI" in
        "arm64-v8a")
            CONFIGURE_HOST="aarch64-linux-android"
            ;;
        "armeabi-v7a")
            CONFIGURE_HOST="arm-linux-androideabi"
            ;;
        "x86_64")
            CONFIGURE_HOST="x86_64-linux-android"
            ;;
        "x86")
            CONFIGURE_HOST="i686-linux-android"
            ;;
        *)
            echo "Unsupported ABI for libresidfp: $ABI"
            return 1
            ;;
    esac

    if [ ! -f "$PROJECT_PATH/configure" ]; then
        if ! command -v autoreconf >/dev/null 2>&1; then
            echo "Error: libresidfp needs autotools bootstrap, but 'autoreconf' is missing."
            return 1
        fi

        echo "Bootstrapping libresidfp with autoreconf..."
        (cd "$PROJECT_PATH" && autoreconf -vfi) || {
            echo "Error: libresidfp autoreconf failed."
            return 1
        }
    fi

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

    (
        cd "$BUILD_DIR"
        "$PROJECT_PATH/configure" \
            --host="$CONFIGURE_HOST" \
            --prefix="$INSTALL_DIR" \
            --disable-shared \
            --enable-static \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC" \
            CXXFLAGS="-fPIC"

        make -j$(nproc)
        make install
    )

    if [ ! -f "$INSTALL_DIR/lib/libresidfp.a" ]; then
        local built_lib
        built_lib="$(find "$BUILD_DIR" -type f -name 'libresidfp.a' | head -n 1)"
        if [ -z "$built_lib" ]; then
            echo "Error: libresidfp static library not found after build."
            return 1
        fi
        mkdir -p "$INSTALL_DIR/lib"
        cp "$built_lib" "$INSTALL_DIR/lib/"
    fi
}

# -----------------------------------------------------------------------------
# Function: Build libsidplayfp
# -----------------------------------------------------------------------------
build_libsidplayfp() {
    local ABI=$1
    echo "Building libsidplayfp for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/libsidplayfp"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local CONFIGURE_HOST=""

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "libsidplayfp source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    # Build libresidfp first when available so sidplayfp can enable RESIDFP.
    if [ -d "$ABSOLUTE_PATH/libresidfp" ]; then
        build_libresidfp "$ABI"
    fi

    case "$ABI" in
        "arm64-v8a")
            CONFIGURE_HOST="aarch64-linux-android"
            ;;
        "armeabi-v7a")
            CONFIGURE_HOST="arm-linux-androideabi"
            ;;
        "x86_64")
            CONFIGURE_HOST="x86_64-linux-android"
            ;;
        "x86")
            CONFIGURE_HOST="i686-linux-android"
            ;;
        *)
            echo "Unsupported ABI for libsidplayfp: $ABI"
            return 1
            ;;
    esac

    if [ ! -f "$PROJECT_PATH/configure" ]; then
        if ! command -v autoreconf >/dev/null 2>&1; then
            echo "Error: libsidplayfp needs autotools bootstrap, but 'autoreconf' is missing."
            return 1
        fi

        # libsidplayfp autotools expects these submodules to exist.
        # Do not create placeholders; fail hard if missing.
        if [ ! -d "$PROJECT_PATH/src/builders/exsid-builder/driver/m4" ] || \
           [ ! -d "$PROJECT_PATH/src/builders/usbsid-builder/driver/m4" ]; then
            echo "Error: libsidplayfp submodules are missing."
            echo "Run:"
            echo "  git -C \"$PROJECT_PATH\" submodule update --init --recursive"
            return 1
        fi

        echo "Bootstrapping libsidplayfp with autoreconf..."
        (cd "$PROJECT_PATH" && autoreconf -vfi) || {
            echo "Error: libsidplayfp autoreconf failed."
            echo "Hint: verify submodules are initialized:"
            echo "  git -C \"$PROJECT_PATH\" submodule update --init --recursive"
            return 1
        }
    fi

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

    (
        cd "$BUILD_DIR"
        PKG_CONFIG_PATH="$INSTALL_DIR/lib/pkgconfig" \
        PKG_CONFIG_LIBDIR="$INSTALL_DIR/lib/pkgconfig" \
        RESIDFP_CFLAGS="-I$INSTALL_DIR/include" \
        RESIDFP_LIBS="-L$INSTALL_DIR/lib -lresidfp" \
        "$PROJECT_PATH/configure" \
            --host="$CONFIGURE_HOST" \
            --prefix="$INSTALL_DIR" \
            --disable-shared \
            --enable-static \
            --disable-tests \
            --enable-tests=no \
            --enable-testsuite=no \
            --with-usbsid=no \
            --with-exsid=no \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC" \
            CXXFLAGS="-fPIC"

        make -j$(nproc)
        make install
    )

    if [ -f "$INSTALL_DIR/lib/libsidplayfp.a" ]; then
        :
    else
        local built_lib
        built_lib="$(find "$BUILD_DIR" -type f -name 'libsidplayfp.a' | head -n 1)"
        if [ -z "$built_lib" ]; then
            echo "Error: libsidplayfp static library not found after build."
            return 1
        fi
        mkdir -p "$INSTALL_DIR/lib"
        cp "$built_lib" "$INSTALL_DIR/lib/"
    fi
}

# -----------------------------------------------------------------------------
# Argument Parsing
# -----------------------------------------------------------------------------
usage() {
    echo "Usage: $0 <abi|all> <lib|all[,lib2,...]>"
    echo "  ABI: all, arm64-v8a, armeabi-v7a, x86_64, x86"
    echo "  LIB: all, libsoxr, openssl, ffmpeg, libopenmpt, libvgm, libgme, libresidfp, libsidplayfp, lazyusf2, fluidsynth"
    echo "  Aliases: sox/soxr, gme, residfp, sid/sidplayfp, usf/lazyusf, fluid/libfluidsynth"
}

if [ "$#" -eq 1 ]; then
    echo "Error: missing second argument."
    usage
    exit 1
fi

if [ "$#" -gt 2 ]; then
    echo "Error: too many arguments."
    usage
    exit 1
fi

TARGET_ABI=${1:-all}
TARGET_LIB=${2:-all}

normalize_lib_name() {
    local lib="$1"
    case "$lib" in
        openssl)
            echo "openssl"
            ;;
        sox|soxr)
            echo "libsoxr"
            ;;
        gme)
            echo "libgme"
            ;;
        residfp)
            echo "libresidfp"
            ;;
        sid|sidplayfp)
            echo "libsidplayfp"
            ;;
        usf|lazyusf|lazyusf2)
            echo "lazyusf2"
            ;;
        fluid|fluidsynth|libfluidsynth)
            echo "fluidsynth"
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

is_valid_abi() {
    local abi="$1"
    case "$abi" in
        all|arm64-v8a|armeabi-v7a|x86_64|x86)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_valid_lib() {
    local lib="$1"
    case "$lib" in
        all|libsoxr|openssl|ffmpeg|libopenmpt|libvgm|libgme|libresidfp|libsidplayfp|lazyusf2|fluidsynth)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

if ! is_valid_abi "$TARGET_ABI"; then
    echo "Error: invalid ABI '$TARGET_ABI'."
    usage
    exit 1
fi

if [ "$TARGET_LIB" != "all" ]; then
    IFS=',' read -r -a requested_libs <<< "$TARGET_LIB"
    for raw in "${requested_libs[@]}"; do
        item="$(echo "$raw" | xargs)"
        item="$(normalize_lib_name "$item")"
        if ! is_valid_lib "$item"; then
            echo "Error: invalid lib '$raw'."
            usage
            exit 1
        fi
    done
fi

# -----------------------------------------------------------------------------
# Pre-build setup (Apply patches once)
# -----------------------------------------------------------------------------
if target_has_lib "libopenmpt"; then
    apply_libopenmpt_patches
fi

if target_has_lib "libvgm"; then
    apply_libvgm_patches
fi

if target_has_lib "libgme"; then
    apply_libgme_patches
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

    if target_has_lib "openssl"; then
        build_openssl "$ABI"
    fi

    if target_has_lib "ffmpeg"; then
        build_ffmpeg "$ABI"
    fi

    if target_has_lib "libopenmpt"; then
        build_libopenmpt "$ABI"
    fi

    if target_has_lib "libvgm"; then
        build_libvgm "$ABI"
    fi

    if target_has_lib "libgme"; then
        build_libgme "$ABI"
    fi

    if target_has_lib "libresidfp"; then
        build_libresidfp "$ABI"
    fi

    if target_has_lib "libsidplayfp"; then
        build_libsidplayfp "$ABI"
    fi

    if target_has_lib "lazyusf2"; then
        build_lazyusf2 "$ABI"
    fi

    if target_has_lib "fluidsynth"; then
        build_fluidsynth "$ABI"
    fi
done

echo "Build complete!"
