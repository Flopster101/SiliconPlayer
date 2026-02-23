#!/bin/bash
set -e

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
NDK_VERSION="29.0.14206865" # From local.properties/source.properties
ANDROID_API=23 # minSdk

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

# Build log/noise controls for external dependencies.
NPROC="$(nproc 2>/dev/null || getconf _NPROCESSORS_ONLN || echo 4)"
DEP_WARN_FLAGS="-w"
DEP_OPT_FLAGS="$DEP_WARN_FLAGS -Ofast"

# Architecture independent variables
# Default ABI pool for "all" targets.
# Keep x86 support available when explicitly requested, but exclude it from "all".
DEFAULT_ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
ABSOLUTE_PATH="$SCRIPT_DIR"
PATCHES_DIR="$ABSOLUTE_PATH/patches/libopenmpt"
PATCHES_DIR_LIBGME="$ABSOLUTE_PATH/patches/libgme"
PATCHES_DIR_LAZYUSF2="$ABSOLUTE_PATH/patches/lazyusf2"
PATCHES_DIR_VIO2SF="$ABSOLUTE_PATH/patches/vio2sf"
PATCHES_DIR_ADPLUG="$ABSOLUTE_PATH/patches/adplug"
PATCHES_DIR_KLYSTRACK="$ABSOLUTE_PATH/patches/klystrack"
PATCHES_DIR_UADE="$ABSOLUTE_PATH/patches/uade"
OPENSSL_DIR="$ABSOLUTE_PATH/openssl"

# -----------------------------------------------------------------------------
# Functions: Generic system dependency installation helpers
# -----------------------------------------------------------------------------
detect_linux_family() {
    if [ ! -r /etc/os-release ]; then
        echo "unknown"
        return
    fi

    # shellcheck disable=SC1091
    . /etc/os-release
    local id="${ID:-}"
    local id_like="${ID_LIKE:-}"

    case "$id" in
        ubuntu|debian)
            echo "debian"
            ;;
        arch|manjaro|endeavouros)
            echo "arch"
            ;;
        *)
            if [[ "$id_like" == *debian* ]]; then
                echo "debian"
            elif [[ "$id_like" == *arch* ]]; then
                echo "arch"
            else
                echo "unknown"
            fi
            ;;
    esac
}

install_dependency_if_missing() {
    local dep_name="$1"
    local binary_check="$2"
    local debian_pkg="$3"
    local arch_pkg="$4"
    local linux_family="$5"

    local bin
    IFS='|' read -r -a _bin_candidates <<< "$binary_check"
    for bin in "${_bin_candidates[@]}"; do
        if command -v "$bin" >/dev/null 2>&1; then
            echo "$dep_name already installed."
            return 0
        fi
    done

    # Backward-compatible fallback for single name calls.
    if command -v "$binary_check" >/dev/null 2>&1; then
        echo "$dep_name already installed."
        return 0
    fi

    case "$linux_family" in
        debian)
            if [ -n "$debian_pkg" ]; then
                echo "$dep_name not found. Installing $debian_pkg (Debian/Ubuntu)..."
                sudo apt-get install -y "$debian_pkg"
            else
                echo "Warning: no Debian package mapping for $dep_name. Skipping."
            fi
            ;;
        arch)
            if [ -n "$arch_pkg" ]; then
                echo "$dep_name not found. Installing $arch_pkg (Arch)..."
                sudo pacman -S --noconfirm "$arch_pkg"
            else
                echo "Warning: no Arch package mapping for $dep_name. Skipping."
            fi
            ;;
        *)
            echo "Warning: unsupported distro. Cannot auto-install $dep_name."
            ;;
    esac
}

ensure_system_dependencies() {
    local linux_family
    linux_family="$(detect_linux_family)"

    # XA assembler: executable name differs by distro/package (`xa` vs `xa65`).
    install_dependency_if_missing "XA assembler" "xa|xa65" "xa65" "xa" "$linux_family"
}

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
        patch_subject="$(sed -n 's/^Subject: \[PATCH[^]]*\] //p' "$patch_file" | head -n 1)"

        # Secondary idempotency check:
        # If the patch subject already exists in git history, treat it as applied.
        # This avoids false negatives from reverse-apply checks when later patches
        # touched nearby context lines.
        if [ -n "$patch_subject" ] && git -C "$PROJECT_PATH" log --format=%s | grep -Fqx "$patch_subject"; then
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
# Function: Apply lazyusf2 patches (idempotent)
# -----------------------------------------------------------------------------
apply_lazyusf2_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/lazyusf2"
    if [ ! -d "$PATCHES_DIR_LAZYUSF2" ]; then
        return
    fi

    for patch_file in "$PATCHES_DIR_LAZYUSF2"/*.patch; do
        [ -e "$patch_file" ] || continue
        local patch_name
        patch_name="$(basename "$patch_file")"
        local patch_subject
        patch_subject="$(sed -n 's/^Subject: \[PATCH[^]]*\] //p' "$patch_file" | head -n 1)"

        # If the patch subject already exists in git history, treat it as applied.
        # This avoids false negatives from reverse-apply checks when later patches
        # in the same series touch nearby context lines from earlier patches.
        if [ -n "$patch_subject" ] && git -C "$PROJECT_PATH" log --format=%s | grep -Fq "$patch_subject"; then
            echo "lazyusf2 patch already applied (subject): $patch_name"
            continue
        fi

        if git -C "$PROJECT_PATH" apply --check --reverse "$patch_file" >/dev/null 2>&1; then
            echo "lazyusf2 patch already applied: $patch_name"
            continue
        fi

        echo "Applying lazyusf2 patch: $patch_name"
        # lazyusf2 upstream mixes CRLF/LF files (notably in usf/), and our
        # patch series is generated with normalized diff lines. Allow
        # whitespace-insensitive matching so git am can apply cleanly.
        git -C "$PROJECT_PATH" am --ignore-whitespace "$patch_file" || {
            echo "Error applying patch $patch_name"
            git -C "$PROJECT_PATH" am --abort
            exit 1
        }
    done
}

# -----------------------------------------------------------------------------
# Function: Apply vio2sf patches (idempotent)
# -----------------------------------------------------------------------------
apply_vio2sf_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/2sf/vio2sf"
    if [ ! -d "$PATCHES_DIR_VIO2SF" ]; then
        return
    fi

    for patch_file in "$PATCHES_DIR_VIO2SF"/*.patch; do
        [ -e "$patch_file" ] || continue
        local patch_name
        patch_name="$(basename "$patch_file")"

        if git -C "$PROJECT_PATH" apply --check --reverse "$patch_file" >/dev/null 2>&1; then
            echo "vio2sf patch already applied: $patch_name"
            continue
        fi

        echo "Applying vio2sf patch: $patch_name"
        git -C "$PROJECT_PATH" am "$patch_file" || {
            echo "Error applying patch $patch_name"
            git -C "$PROJECT_PATH" am --abort
            exit 1
        }
    done
}

# -----------------------------------------------------------------------------
# Function: Apply adplug patches (idempotent)
# -----------------------------------------------------------------------------
apply_adplug_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/adplug"
    if [ ! -d "$PROJECT_PATH" ]; then
        return
    fi
    if [ ! -d "$PATCHES_DIR_ADPLUG" ]; then
        return
    fi

    for patch_file in "$PATCHES_DIR_ADPLUG"/*.patch; do
        [ -e "$patch_file" ] || continue
        local patch_name
        patch_name="$(basename "$patch_file")"

        if git -C "$PROJECT_PATH" apply --check --reverse "$patch_file" >/dev/null 2>&1; then
            echo "adplug patch already applied: $patch_name"
            continue
        fi

        echo "Applying adplug patch: $patch_name"
        git -C "$PROJECT_PATH" am "$patch_file" || {
            echo "Error applying patch $patch_name"
            git -C "$PROJECT_PATH" am --abort
            exit 1
        }
    done
}

# -----------------------------------------------------------------------------
# Function: Apply klystrack (klystron) patches (idempotent)
# -----------------------------------------------------------------------------
apply_klystrack_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/klystrack/klystron"
    if [ ! -d "$PROJECT_PATH" ]; then
        return
    fi
    if [ ! -d "$PATCHES_DIR_KLYSTRACK" ]; then
        return
    fi

    for patch_file in "$PATCHES_DIR_KLYSTRACK"/*.patch; do
        [ -e "$patch_file" ] || continue
        local patch_name
        patch_name="$(basename "$patch_file")"
        local patch_subject
        patch_subject="$(sed -n 's/^Subject: \[PATCH[^]]*\] //p' "$patch_file" | head -n 1)"

        # Secondary idempotency check:
        # If the patch subject already exists in git history, treat it as applied.
        if [ -n "$patch_subject" ] && git -C "$PROJECT_PATH" log --format=%s | grep -Fqx "$patch_subject"; then
            echo "klystrack patch already applied (subject): $patch_name"
            continue
        fi

        # Reliable idempotency check with whitespace-tolerant reverse-apply.
        if git -C "$PROJECT_PATH" apply --check --reverse \
            --ignore-space-change --ignore-whitespace \
            "$patch_file" >/dev/null 2>&1; then
            echo "klystrack patch already applied: $patch_name"
            continue
        fi

        echo "Applying klystrack patch: $patch_name"
        # Use 3-way + whitespace-tolerant apply path for robustness across minor
        # upstream drift and line-ending differences.
        git -C "$PROJECT_PATH" am --3way --ignore-whitespace --whitespace=nowarn "$patch_file" || {
            echo "Error applying patch $patch_name"
            git -C "$PROJECT_PATH" am --abort
            exit 1
        }
    done
}

# -----------------------------------------------------------------------------
# Function: Apply uade patches (idempotent)
# -----------------------------------------------------------------------------
apply_uade_patches() {
    local PROJECT_PATH="$ABSOLUTE_PATH/uade"
    if [ ! -d "$PATCHES_DIR_UADE" ]; then
        return
    fi

    for patch_file in "$PATCHES_DIR_UADE"/*.patch; do
        [ -e "$patch_file" ] || continue
        local patch_name
        patch_name="$(basename "$patch_file")"
        local patch_subject
        patch_subject="$(sed -n 's/^Subject: \[PATCH[^]]*\] //p' "$patch_file" | head -n 1)"

        if [ -n "$patch_subject" ] && git -C "$PROJECT_PATH" log --format=%s | grep -Fqx "$patch_subject"; then
            echo "uade patch already applied (subject): $patch_name"
            continue
        fi

        if git -C "$PROJECT_PATH" apply --check --reverse "$patch_file" >/dev/null 2>&1; then
            echo "uade patch already applied: $patch_name"
            continue
        fi

        echo "Applying uade patch: $patch_name"
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

    cmake -Wno-dev -Wno-deprecated \
        -S "$PROJECT_PATH" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_C_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_CXX_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_C_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_CXX_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_TESTS=OFF \
        -DBUILD_EXAMPLES=OFF \
        -DWITH_OPENMP=OFF \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

    cmake --build "$BUILD_DIR" -j"$NPROC"
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
    local OPENSSL_CROSS_PREFIX=""
    local OPENSSL_CLANG_BIN=""
    local OPENSSL_BUILD_SIGNATURE="openssl-android-static-pic-noasm-v1"
    local OPENSSL_STAMP_FILE="$INSTALL_DIR/lib/.openssl_build_signature"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "OpenSSL source not found at $PROJECT_PATH."
        echo "Clone it first: git clone https://github.com/openssl/openssl.git $PROJECT_PATH"
        return 1
    fi

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libssl.a" ] && [ -f "$INSTALL_DIR/lib/libcrypto.a" ] && [ -f "$INSTALL_DIR/include/openssl/ssl.h" ] && [ -f "$OPENSSL_STAMP_FILE" ]; then
        if [ "$(cat "$OPENSSL_STAMP_FILE")" = "$OPENSSL_BUILD_SIGNATURE" ]; then
            echo "OpenSSL already built for $ABI -> skipping"
            return 0
        fi
    fi

    case "$ABI" in
        "arm64-v8a")
            OPENSSL_TARGET="android-arm64"
            OPENSSL_CROSS_PREFIX="aarch64-linux-android-"
            OPENSSL_CLANG_BIN="$TOOLCHAIN/bin/aarch64-linux-android${ANDROID_API}-clang"
            ;;
        "armeabi-v7a")
            OPENSSL_TARGET="android-arm"
            OPENSSL_CROSS_PREFIX="arm-linux-androideabi-"
            OPENSSL_CLANG_BIN="$TOOLCHAIN/bin/armv7a-linux-androideabi${ANDROID_API}-clang"
            ;;
        "x86_64")
            OPENSSL_TARGET="android-x86_64"
            OPENSSL_CROSS_PREFIX="x86_64-linux-android-"
            OPENSSL_CLANG_BIN="$TOOLCHAIN/bin/x86_64-linux-android${ANDROID_API}-clang"
            ;;
        "x86")
            OPENSSL_TARGET="android-x86"
            OPENSSL_CROSS_PREFIX="i686-linux-android-"
            OPENSSL_CLANG_BIN="$TOOLCHAIN/bin/i686-linux-android${ANDROID_API}-clang"
            ;;
        *)
            echo "Unsupported ABI for OpenSSL: $ABI"
            return 1
            ;;
    esac

    echo "Building OpenSSL for $ABI ($OPENSSL_TARGET)..."
    if [ ! -x "$OPENSSL_CLANG_BIN" ]; then
        echo "Error: expected clang not found for OpenSSL: $OPENSSL_CLANG_BIN"
        return 1
    fi

    mkdir -p "$INSTALL_DIR"
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"

    cd "$PROJECT_PATH"

    make clean >/dev/null 2>&1 || true
    rm -f configdata.pm

    # OpenSSL Android Configure probing is sensitive to env vars and legacy tool names.
    # Export canonical Android vars and provide compat wrappers in an ABI-local PATH prefix.
    local OPENSSL_COMPAT_BIN="$BUILD_DIR/ndk-compat-bin"
    mkdir -p "$OPENSSL_COMPAT_BIN"

    cat > "$OPENSSL_COMPAT_BIN/${OPENSSL_CROSS_PREFIX}gcc" <<EOF
#!/usr/bin/env bash
exec "$OPENSSL_CLANG_BIN" "\$@"
EOF
    cat > "$OPENSSL_COMPAT_BIN/${OPENSSL_CROSS_PREFIX}clang" <<EOF
#!/usr/bin/env bash
exec "$OPENSSL_CLANG_BIN" "\$@"
EOF
    chmod +x "$OPENSSL_COMPAT_BIN/${OPENSSL_CROSS_PREFIX}gcc" "$OPENSSL_COMPAT_BIN/${OPENSSL_CROSS_PREFIX}clang"

    export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
    export ANDROID_NDK="$ANDROID_NDK_HOME"
    export ANDROID_API="$ANDROID_API"
    export PATH="$OPENSSL_COMPAT_BIN:$TOOLCHAIN/bin:$PATH"

    export CFLAGS="-fPIC $DEP_OPT_FLAGS"
    export CXXFLAGS="-fPIC $DEP_OPT_FLAGS"

    perl ./Configure "$OPENSSL_TARGET" \
        --cross-compile-prefix="$OPENSSL_CROSS_PREFIX" \
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

    make -s -j"$NPROC"
    make -s install_sw
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
    local FFMPEG_EXTRA_CFLAGS="-fPIC $DEP_OPT_FLAGS"
    local FFMPEG_AUDIO_DECODERS=""
    local FFMPEG_AUDIO_DEMUXERS=""
    local FFMPEG_VIDEO_CONTAINER_DEMUXERS=""
    local FFMPEG_ENABLED_DEMUXERS=""
    local FFMPEG_AUDIO_PARSERS=""
    local FFMPEG_PROTOCOLS=""

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

    # Audio-native demuxers.
    FFMPEG_AUDIO_DECODERS="aac,aac_fixed,aac_latm,ac3,ac3_fixed,alac,als,amrnb,amrwb,ape,atrac1,atrac3,atrac3p,atrac9,cook,dca,eac3,flac,gsm,gsm_ms,mp1,mp1float,mp2,mp2float,mp3,mp3float,mp3on4,mp3on4float,opus,qdm2,qoa,ralf,shorten,tak,truehd,tta,vorbis,wavpack,wmalossless,wmapro,wmav1,wmav2,pcm_alaw,pcm_mulaw,pcm_s8,pcm_s16be,pcm_s16le,pcm_s24be,pcm_s24le,pcm_s32be,pcm_s32le,pcm_u8,pcm_u16be,pcm_u16le,pcm_f32be,pcm_f32le,pcm_f64be,pcm_f64le"
    FFMPEG_AUDIO_DEMUXERS="aac,ac3,aiff,amr,ape,caf,concat,dsf,ffmetadata,flac,hls,mp3,ogg,qcp,rso,sdp,truehd,tta,vag,voc,w64,wav,wv,xa,xwma"
    # Video/container demuxers that may carry playable audio streams.
    FFMPEG_VIDEO_CONTAINER_DEMUXERS="anm,argo_asf,argo_brp,asf,avi,bethsoftvid,bfi,bink,binka,bmv,c93,ea,ea_cdata,flic,filmstrip,idcin,idf,iv8,ivf,kvag,lmlm4,lxf,matroska,mca,mcc,moflex,mov,mpegps,mpegts,mpegvideo,mxf,mxg,nc,nistsphere,nsv,nut,pmp,pp_bnk,pva,rcwt,redspark,rl2,rm,roq,rpl,rsd,smacker,sol,svag,thp,tiertexseq,tmv,ty,vivo,vmd,vpk,wsaud,wsvqa,wtv,xmv,yop,yuv4mpegpipe"
    FFMPEG_ENABLED_DEMUXERS="$FFMPEG_AUDIO_DEMUXERS${FFMPEG_VIDEO_CONTAINER_DEMUXERS:+,$FFMPEG_VIDEO_CONTAINER_DEMUXERS}"
    FFMPEG_AUDIO_PARSERS="aac,aac_latm,ac3,adx,amr,ape,atrac3,cook,dca,flac,gsm,mpegaudio,opus,tak,vorbis,wavpack"
    FFMPEG_PROTOCOLS="android_content,cache,concat,crypto,data,fd,file,http,https,httpproxy,pipe,subfile,tcp,tls,udp,udplite,unix"

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
        --disable-filters \
        --enable-network \
        --disable-indevs \
        --disable-outdevs \
        --disable-hwaccels \
        --disable-bsfs \
        --disable-decoders \
        --disable-demuxers \
        --disable-parsers \
        --disable-protocols \
        --disable-everything \
        --disable-encoders \
        --disable-muxers \
        --enable-decoder="$FFMPEG_AUDIO_DECODERS" \
        --enable-demuxer="$FFMPEG_ENABLED_DEMUXERS" \
        --enable-parser="$FFMPEG_AUDIO_PARSERS" \
        --enable-protocol="$FFMPEG_PROTOCOLS" \
        --enable-swresample \
        $SOXR_ENABLE_FLAG \
        $OPENSSL_ENABLE_FLAG \
        --enable-jni \
        --extra-cflags="$FFMPEG_EXTRA_CFLAGS" \
        --extra-ldflags="$SOXR_LDFLAGS $OPENSSL_LDFLAGS" \
        --extra-libs="$SOXR_EXTRA_LIBS $OPENSSL_EXTRA_LIBS" \
        $EXTRA_FLAGS || { echo "Error: FFmpeg configure failed!"; exit 1; }

    make -s -j"$NPROC"
    make -s install

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
    local OPENMPT_LIB="$INSTALL_DIR/lib/$ABI/libopenmpt.a"
    local OPENMPT_HEADER="$INSTALL_DIR/include/libopenmpt/libopenmpt.h"
    local OPENMPT_STAMP="$INSTALL_DIR/lib/.libopenmpt_build_stamp"
    local OPENMPT_STAMP_EXPECTED="api=$ANDROID_API"

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$OPENMPT_LIB" ] && [ -f "$OPENMPT_HEADER" ] && \
       [ -f "$OPENMPT_STAMP" ] && [ "$(cat "$OPENMPT_STAMP" 2>/dev/null)" = "$OPENMPT_STAMP_EXPECTED" ]; then
        echo "libopenmpt already built for $ABI -> skipping"
        return 0
    fi

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
        APP_CFLAGS="$DEP_OPT_FLAGS" \
        APP_CPPFLAGS="$DEP_OPT_FLAGS" \
        NDK_LIBS_OUT="$INSTALL_DIR/lib" \
        NDK_OUT="$PROJECT_PATH/obj/$ABI" \
        MPT_WITH_MINIMP3=1 \
        MPT_WITH_STBVORBIS=1 \
        -j"$NPROC"

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

    mkdir -p "$INSTALL_DIR/lib"
    printf '%s\n' "$OPENMPT_STAMP_EXPECTED" > "$OPENMPT_STAMP"
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

    cmake -Wno-dev -Wno-deprecated \
        -S "$PROJECT_PATH" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_C_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_CXX_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_C_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_CXX_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
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

    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"

    # Some libvgm revisions do not install all headers consistently.
    # Ensure required public headers exist in prebuilt include tree.
    mkdir -p "$INSTALL_DIR/include/vgm/player" "$INSTALL_DIR/include/vgm/utils" "$INSTALL_DIR/include/vgm/emu"
    cp "$PROJECT_PATH/player/"*.h "$INSTALL_DIR/include/vgm/player/" 2>/dev/null || true
    cp "$PROJECT_PATH/player/"*.hpp "$INSTALL_DIR/include/vgm/player/" 2>/dev/null || true
    cp "$PROJECT_PATH/utils/"*.h "$INSTALL_DIR/include/vgm/utils/" 2>/dev/null || true
    cp "$PROJECT_PATH/emu/"*.h "$INSTALL_DIR/include/vgm/emu/" 2>/dev/null || true
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

    cmake -Wno-dev -Wno-deprecated \
        -S "$PROJECT_PATH" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_C_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_CXX_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_C_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_CXX_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DGME_BUILD_SHARED=OFF \
        -DGME_BUILD_STATIC=ON \
        -DGME_BUILD_TESTING=OFF \
        -DGME_BUILD_EXAMPLES=OFF \
        -DGME_ZLIB=ON \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

    cmake --build "$BUILD_DIR" -j"$NPROC"
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
                make -s -j"$NPROC" liblazyusf.a \
                    CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
                    AR="$TOOLCHAIN/bin/llvm-ar" \
                    CPU="AArch64" \
                    ARCH="64" \
                    OPTFLAGS="$DEP_OPT_FLAGS" \
                    OBJS_RECOMPILER_64="" \
                    OPTS_AArch64="" \
                    ROPTS_AArch64="-DARCH_MIN_ARM_NEON"
                ;;
            "armeabi-v7a")
                make -s -j"$NPROC" liblazyusf.a \
                    CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
                    AR="$TOOLCHAIN/bin/llvm-ar" \
                    CPU="arm" \
                    ARCH="32" \
                    OPTFLAGS="$DEP_OPT_FLAGS" \
                    FLAGS_32="-fPIC" \
                    OBJS_RECOMPILER_32="" \
                    OPTS_arm="" \
                    ROPTS_arm=""
                ;;
            "x86")
                make -s -j"$NPROC" liblazyusf.a \
                    CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
                    AR="$TOOLCHAIN/bin/llvm-ar" \
                    CPU="x86" \
                    ARCH="32" \
                    OPTFLAGS="$DEP_OPT_FLAGS" \
                    FLAGS_32="-fPIC -msse -mmmx -msse2" \
                    OBJS_RECOMPILER_32="" \
                    OPTS_x86="" \
                    ROPTS_x86="-DARCH_MIN_SSE2"
                ;;
            "x86_64")
                make -s -j"$NPROC" liblazyusf.a \
                    CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
                    AR="$TOOLCHAIN/bin/llvm-ar" \
                    CPU="x86_64" \
                    ARCH="64" \
                    OPTFLAGS="$DEP_OPT_FLAGS" \
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
# Function: Build psflib
# -----------------------------------------------------------------------------
build_psflib() {
    local ABI=$1
    echo "Building psflib for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/psflib"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "psflib source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include/psflib"

    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true
        make -s -j"$NPROC" libpsflib.a \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            CFLAGS="-c -fPIC $DEP_OPT_FLAGS"
    )

    if [ ! -f "$PROJECT_PATH/libpsflib.a" ]; then
        echo "Error: psflib static library not found after build."
        return 1
    fi

    cp "$PROJECT_PATH/libpsflib.a" "$INSTALL_DIR/lib/libpsflib.a"
    cp "$PROJECT_PATH/psflib.h" "$INSTALL_DIR/include/"
    cp "$PROJECT_PATH/psf2fs.h" "$INSTALL_DIR/include/"
    cp "$PROJECT_PATH/psflib.h" "$INSTALL_DIR/include/psflib/"
    cp "$PROJECT_PATH/psf2fs.h" "$INSTALL_DIR/include/psflib/"
}

# -----------------------------------------------------------------------------
# Function: Build vio2sf
# -----------------------------------------------------------------------------
build_vio2sf() {
    local ABI=$1
    echo "Building vio2sf for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/2sf/vio2sf/src/vio2sf"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "vio2sf source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    # libvio2sf is typically used together with psflib, so ensure psflib exists.
    if [ ! -f "$INSTALL_DIR/lib/libpsflib.a" ]; then
        build_psflib "$ABI"
    fi

    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include/vio2sf/desmume"

    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true
        make -s -j"$NPROC" libvio2sf.a \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            CFLAGS="-c -fPIC $DEP_OPT_FLAGS" \
            CXXFLAGS="-c -fPIC $DEP_OPT_FLAGS" \
            OPTS="-O3 -I. -DBARRAY_DECORATE=TWOSF -DRESAMPLER_DECORATE=TWOSF $DEP_OPT_FLAGS"
    )

    if [ ! -f "$PROJECT_PATH/libvio2sf.a" ]; then
        echo "Error: vio2sf static library not found after build."
        return 1
    fi

    cp "$PROJECT_PATH/libvio2sf.a" "$INSTALL_DIR/lib/libvio2sf.a"
    cp "$PROJECT_PATH/desmume/"*.h "$INSTALL_DIR/include/vio2sf/desmume/"
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

    cmake -Wno-dev -Wno-deprecated \
        -S "$PROJECT_PATH" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_C_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_CXX_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_C_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_CXX_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
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

    cmake --build "$BUILD_DIR" --target libfluidsynth -j"$NPROC"

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
# Function: Build libresid
# -----------------------------------------------------------------------------
build_libresid() {
    local ABI=$1
    echo "Building libresid for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/resid"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local CONFIGURE_HOST=""

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "libresid source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libresid.a" ] && \
       [ -f "$INSTALL_DIR/include/resid/sid.h" ] && \
       [ -f "$INSTALL_DIR/include/resid/siddefs.h" ]; then
        echo "libresid already built for $ABI -> skipping"
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
            echo "Unsupported ABI for libresid: $ABI"
            return 1
            ;;
    esac

    if [ ! -f "$PROJECT_PATH/configure" ]; then
        if ! command -v autoreconf >/dev/null 2>&1; then
            echo "Error: libresid needs autotools bootstrap, but 'autoreconf' is missing."
            return 1
        fi

        echo "Bootstrapping libresid with autoreconf..."
        (cd "$PROJECT_PATH" && autoreconf -vfi) || {
            echo "Error: libresid autoreconf failed."
            return 1
        }
    fi

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include/resid"

    (
        cd "$BUILD_DIR"
        "$PROJECT_PATH/configure" \
            --host="$CONFIGURE_HOST" \
            --disable-arch \
            --enable-silent-rules \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC $DEP_OPT_FLAGS" \
            CXXFLAGS="-fPIC $DEP_OPT_FLAGS"

        make --no-print-directory V=0 -j"$NPROC"
    )

    local built_lib
    built_lib="$(find "$BUILD_DIR" -type f -name 'libresid.a' | head -n 1)"
    if [ -z "$built_lib" ]; then
        echo "Error: libresid static library not found after build."
        return 1
    fi
    cp "$built_lib" "$INSTALL_DIR/lib/libresid.a"

    cp "$PROJECT_PATH/"*.h "$INSTALL_DIR/include/resid/" 2>/dev/null || true
    cp "$BUILD_DIR/"*.h "$INSTALL_DIR/include/resid/" 2>/dev/null || true
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

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libresidfp.a" ] && \
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
            --enable-silent-rules \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC $DEP_OPT_FLAGS" \
            CXXFLAGS="-fPIC $DEP_OPT_FLAGS"

        make --no-print-directory V=0 -j"$NPROC"
        make --no-print-directory V=0 install
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
    if [ -d "$ABSOLUTE_PATH/resid" ]; then
        build_libresid "$ABI"
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

        if [ ! -d "$PROJECT_PATH/src/builders/exsid-builder/driver/m4" ] || \
           [ ! -f "$PROJECT_PATH/src/builders/usbsid-builder/driver/src/USBSID.cpp" ]; then
            echo "Error: libsidplayfp submodule dependencies are missing."
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
            --enable-silent-rules \
            --with-usbsid=no \
            --with-exsid=no \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC $DEP_OPT_FLAGS" \
            CXXFLAGS="-fPIC $DEP_OPT_FLAGS"

        make --no-print-directory V=0 -j"$NPROC"
        make --no-print-directory V=0 install
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
# Function: Build sc68 stack (unice68 + file68 + libsc68)
# -----------------------------------------------------------------------------
build_sc68() {
    local ABI=$1
    echo "Building sc68 for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/sc68"
    local CONFIGURE_HOST=""
    local AS68_TOOL="$PROJECT_PATH/as68/as68"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "sc68 source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libsc68.a" ] && \
       [ -f "$INSTALL_DIR/lib/libfile68.a" ] && \
       [ -f "$INSTALL_DIR/lib/libunice68.a" ] && \
       [ -f "$INSTALL_DIR/include/sc68/sc68.h" ]; then
        echo "sc68 already built for $ABI -> skipping"
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
            echo "Unsupported ABI for sc68: $ABI"
            return 1
            ;;
    esac

    if ! command -v autoreconf >/dev/null 2>&1; then
        echo "Error: sc68 build needs autotools bootstrap, but 'autoreconf' is missing."
        return 1
    fi
    if ! command -v hexdump >/dev/null 2>&1; then
        echo "Error: sc68 build needs 'hexdump' to generate trap68.h."
        return 1
    fi

    mkdir -p "$PROJECT_PATH/unice68/m4" "$PROJECT_PATH/file68/m4" "$PROJECT_PATH/libsc68/m4"
    ln -sfn ../vcversion.sh "$PROJECT_PATH/unice68/vcversion.sh"
    ln -sfn ../vcversion.sh "$PROJECT_PATH/file68/vcversion.sh"
    ln -sfn ../vcversion.sh "$PROJECT_PATH/libsc68/vcversion.sh"
    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include"

    (
        cd "$PROJECT_PATH/as68"
        cc -std=gnu89 -O2 \
            -DPACKAGE_VERSION='"build-deps"' \
            -DPACKAGE_URL='"https://sourceforge.net/p/sc68"' \
            -o as68 as68.c error.c expression.c opcode.c word.c
    )

    (
        cd "$PROJECT_PATH/unice68"
        make distclean >/dev/null 2>&1 || true
        rm -rf autom4te.cache
        mkdir -p autom4te.cache
        autoreconf -vfi -I "$PROJECT_PATH/aclocal68"
        ./configure \
            --host="$CONFIGURE_HOST" \
            --prefix="$INSTALL_DIR" \
            --disable-shared \
            --enable-static \
            --disable-unice68-cli \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC $DEP_OPT_FLAGS" \
            CXXFLAGS="-fPIC $DEP_OPT_FLAGS"
        make -s -j"$NPROC"
        make -s install
    )

    (
        cd "$PROJECT_PATH/file68"
        make distclean >/dev/null 2>&1 || true
        rm -rf autom4te.cache
        mkdir -p autom4te.cache
        autoreconf -vfi -I "$PROJECT_PATH/aclocal68"
        PKG_CONFIG_PATH="$INSTALL_DIR/lib/pkgconfig" \
        PKG_CONFIG_LIBDIR="$INSTALL_DIR/lib/pkgconfig" \
        ./configure \
            --host="$CONFIGURE_HOST" \
            --prefix="$INSTALL_DIR" \
            --disable-shared \
            --enable-static \
            --enable-replay-rom=yes \
            --enable-file68-data=no \
            --enable-file=yes \
            --enable-fd=yes \
            --enable-mem=yes \
            --enable-registry=no \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC $DEP_OPT_FLAGS" \
            CXXFLAGS="-fPIC $DEP_OPT_FLAGS"
        make -s -j"$NPROC"
        make -s install
    )

    (
        cd "$PROJECT_PATH/libsc68"
        make distclean >/dev/null 2>&1 || true
        rm -rf autom4te.cache
        mkdir -p autom4te.cache
        autoreconf -vfi -I "$PROJECT_PATH/aclocal68"
        PKG_CONFIG_PATH="$INSTALL_DIR/lib/pkgconfig" \
        PKG_CONFIG_LIBDIR="$INSTALL_DIR/lib/pkgconfig" \
        ./configure \
            --host="$CONFIGURE_HOST" \
            --prefix="$INSTALL_DIR" \
            --disable-shared \
            --enable-static \
            --enable-dialog=no \
            CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC $DEP_OPT_FLAGS" \
            CXXFLAGS="-fPIC $DEP_OPT_FLAGS"

        if grep -Eq '^TWEAK:[[:space:]]*=[[:space:]]*$' asm/version.s; then
            sed -i 's/^TWEAK:[[:space:]]*=.*/TWEAK:\t= 0/' asm/version.s
        fi

        "$AS68_TOOL" asm/trapfunc.s -o trapfunc.bin >/dev/null
        hexdump -ve '1/1 "%d,\n"' trapfunc.bin > sc68/trap68.h
        rm -f trapfunc.bin

        make -s -j"$NPROC"
        make -s install
    )
}

# -----------------------------------------------------------------------------
# Function: Build libbinio
# -----------------------------------------------------------------------------
build_libbinio() {
    local ABI=$1
    echo "Building libbinio for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/libbinio"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local CMAKE_SOURCE="$PROJECT_PATH"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "libbinio source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ ! -f "$CMAKE_SOURCE/CMakeLists.txt" ]; then
        local nested_cmakelists
        nested_cmakelists="$(find "$PROJECT_PATH" -mindepth 2 -maxdepth 3 -type f -name CMakeLists.txt | head -n 1)"
        if [ -n "$nested_cmakelists" ]; then
            CMAKE_SOURCE="$(dirname "$nested_cmakelists")"
            echo "libbinio: using nested CMake source root: $CMAKE_SOURCE"
        fi
    fi

    if [ ! -f "$CMAKE_SOURCE/CMakeLists.txt" ]; then
        echo "Error: libbinio source at $PROJECT_PATH does not contain CMakeLists.txt."
        echo "Ensure submodules are initialized:"
        echo "  git -C \"$ABSOLUTE_PATH\" submodule update --init --recursive"
        return 1
    fi

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

    cmake -Wno-dev -Wno-deprecated \
        -S "$CMAKE_SOURCE" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_C_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_CXX_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_C_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_CXX_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -Dlibbinio_BUILD_SHARED_LIBS=OFF \
        -Dlibbinio_BUILD_DOCUMENTATION=OFF \
        -Dlibbinio_INCLUDE_PACKAGING=ON \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"

    if [ -f "$INSTALL_DIR/lib/liblibbinio.a" ] && [ ! -f "$INSTALL_DIR/lib/libbinio.a" ]; then
        cp "$INSTALL_DIR/lib/liblibbinio.a" "$INSTALL_DIR/lib/libbinio.a"
    fi

    if [ ! -f "$INSTALL_DIR/lib/libbinio.a" ] && [ ! -f "$INSTALL_DIR/lib/liblibbinio.a" ]; then
        local built_lib
        built_lib="$(find "$BUILD_DIR" -type f -name 'lib*binio.a' | head -n 1)"
        if [ -z "$built_lib" ]; then
            echo "Error: libbinio static library not found after build."
            return 1
        fi
        mkdir -p "$INSTALL_DIR/lib"
        cp "$built_lib" "$INSTALL_DIR/lib/libbinio.a"
    fi

    if [ ! -f "$INSTALL_DIR/include/binio.h" ]; then
        mkdir -p "$INSTALL_DIR/include"
        cp "$CMAKE_SOURCE/src/"*.h "$INSTALL_DIR/include/" 2>/dev/null || true
        cp "$BUILD_DIR/src/generated/include/binio.h" "$INSTALL_DIR/include/" 2>/dev/null || true
    fi
}

# -----------------------------------------------------------------------------
# Function: Build adplug
# -----------------------------------------------------------------------------
build_adplug() {
    local ABI=$1
    echo "Building adplug for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/adplug"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "adplug source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ ! -f "$PROJECT_PATH/CMakeLists.txt" ]; then
        echo "Error: adplug source at $PROJECT_PATH does not contain CMakeLists.txt."
        echo "Ensure submodules are initialized:"
        echo "  git -C \"$ABSOLUTE_PATH\" submodule update --init --recursive"
        return 1
    fi

    if [ ! -f "$INSTALL_DIR/lib/libbinio.a" ]; then
        build_libbinio "$ABI"
    fi

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

    cmake -Wno-dev -Wno-deprecated \
        -S "$PROJECT_PATH" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_C_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_CXX_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_C_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_CXX_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -Dadplug_BUILD_SHARED_LIBS=OFF \
        -DADPLUG_PRECOMPILED_HEADERS=OFF \
        -Dlibadplug_BUILD_DOCUMENTATION=OFF \
        -Dadplug_INCLUDE_TEST=OFF \
        -Dadplug_INCLUDE_PACKAGING=ON \
        -DCMAKE_PREFIX_PATH="$INSTALL_DIR" \
        -Dlibbinio_DIR="$INSTALL_DIR/lib/cmake/libbinio" \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"

    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"

    if [ ! -f "$INSTALL_DIR/lib/libadplug.a" ]; then
        local built_lib
        built_lib="$(find "$BUILD_DIR" -type f -name 'libadplug.a' | head -n 1)"
        if [ -z "$built_lib" ]; then
            echo "Error: adplug static library not found after build."
            return 1
        fi
        mkdir -p "$INSTALL_DIR/lib"
        cp "$built_lib" "$INSTALL_DIR/lib/libadplug.a"
    fi

    mkdir -p "$INSTALL_DIR/include/adplug"
    cp "$PROJECT_PATH/src/"*.h "$INSTALL_DIR/include/adplug/" 2>/dev/null || true
    cp "$BUILD_DIR/src/generated/include/adplug/version.h" "$INSTALL_DIR/include/adplug/" 2>/dev/null || true
}

# -----------------------------------------------------------------------------
# Function: Build libzakalwe
# -----------------------------------------------------------------------------
build_libzakalwe() {
    local ABI=$1
    echo "Building libzakalwe for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/libzakalwe"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "libzakalwe source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libzakalwe.a" ] && \
       [ -f "$INSTALL_DIR/include/zakalwe/string.h" ]; then
        echo "libzakalwe already built for $ABI -> skipping"
        return 0
    fi

    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include/zakalwe"

    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true

        CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
        CFLAGS="-fPIC $DEP_OPT_FLAGS" \
        ./configure

        # Build static archive payload target from upstream Makefile.in.
        make --no-print-directory V=0 -j"$NPROC" AR="$TOOLCHAIN/bin/llvm-ar" static_pack.o
    )

    if [ ! -f "$PROJECT_PATH/static_pack.o" ]; then
        echo "Error: libzakalwe static archive payload not found after build."
        return 1
    fi

    cp "$PROJECT_PATH/static_pack.o" "$INSTALL_DIR/lib/libzakalwe.a"
    cp "$PROJECT_PATH/include/zakalwe/"*.h "$INSTALL_DIR/include/zakalwe/" 2>/dev/null || true
}

# -----------------------------------------------------------------------------
# Function: Build bencodetools
# -----------------------------------------------------------------------------
build_bencodetools() {
    local ABI=$1
    echo "Building bencodetools for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/bencodetools"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "bencodetools source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libbencodetools.a" ] && \
       [ -f "$INSTALL_DIR/include/bencodetools/bencode.h" ]; then
        echo "bencodetools already built for $ABI -> skipping"
        return 0
    fi

    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include/bencodetools"

    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true

        CFLAGS="-fPIC $DEP_OPT_FLAGS" \
        LDFLAGS="-L$INSTALL_DIR/lib" \
        ./configure \
            --prefix="$INSTALL_DIR" \
            --without-python \
            --c-compiler="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang"

        make --no-print-directory V=0 -j"$NPROC" compile-c
    )

    if [ ! -f "$PROJECT_PATH/bencode.o" ]; then
        echo "Error: bencodetools object payload not found after build."
        return 1
    fi

    "$TOOLCHAIN/bin/llvm-ar" rcs "$INSTALL_DIR/lib/libbencodetools.a" "$PROJECT_PATH/bencode.o"
    cp "$PROJECT_PATH/include/bencodetools/"*.h "$INSTALL_DIR/include/bencodetools/" 2>/dev/null || true
}

# -----------------------------------------------------------------------------
# Function: Build vasm host tool (required by UADE score build)
# -----------------------------------------------------------------------------
build_vasm_host() {
    echo "Building vasm host tool..."

    local PROJECT_PATH="$ABSOLUTE_PATH/vasm"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "Error: vasm source not found at $PROJECT_PATH."
        return 1
    fi

    if [ "$FORCE_CLEAN" -eq 1 ]; then
        (
            cd "$PROJECT_PATH"
            make --no-print-directory clean >/dev/null 2>&1 || true
        )
    fi

    if [ -x "$PROJECT_PATH/vasmm68k_mot" ]; then
        echo "vasm host tool already built -> skipping"
        return 0
    fi

    (
        cd "$PROJECT_PATH"
        make --no-print-directory -j"$NPROC" CPU=m68k SYNTAX=mot
    )

    if [ ! -x "$PROJECT_PATH/vasmm68k_mot" ]; then
        echo "Error: vasm build succeeded but vasmm68k_mot is missing."
        return 1
    fi
}

# -----------------------------------------------------------------------------
# Function: Build uade (libuade static)
# -----------------------------------------------------------------------------
build_uade() {
    local ABI=$1
    echo "Building uade for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/uade"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local CONFIGURE_HOST=""
    local UADE_DEPS_PREFIX="$BUILD_DIR/uade_deps_prefix"
    local HOST_VASM_WRAPPER_DIR="$BUILD_DIR/.host-tools"
    local HOST_VASM_WRAPPER="$HOST_VASM_WRAPPER_DIR/vasm.vasmm68k-mot"
    local HOST_NATIVE_CC=""
    local UADE_LIB="$INSTALL_DIR/lib/libuade.a"
    local UADE_HEADER="$INSTALL_DIR/include/uade/uade.h"
    local UADE_CORE="$INSTALL_DIR/lib/uade/uadecore"
    local UADE_STAMP="$INSTALL_DIR/lib/.uade_build_stamp"
    local UADE_STAMP_EXPECTED="api=$ANDROID_API"

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$UADE_LIB" ] && [ -f "$UADE_HEADER" ] && [ -f "$UADE_CORE" ] && \
       [ -f "$UADE_STAMP" ] && [ "$(cat "$UADE_STAMP" 2>/dev/null)" = "$UADE_STAMP_EXPECTED" ]; then
        echo "uade already built for $ABI -> skipping"
        return 0
    fi

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "uade source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ ! -f "$PROJECT_PATH/configure" ]; then
        echo "Error: uade source at $PROJECT_PATH does not contain configure."
        return 1
    fi

    if command -v gcc >/dev/null 2>&1; then
        HOST_NATIVE_CC="$(command -v gcc)"
    elif command -v cc >/dev/null 2>&1; then
        HOST_NATIVE_CC="$(command -v cc)"
    else
        echo "Error: no host C compiler found (gcc/cc) for UADE helper tools."
        return 1
    fi

    # UADE build depends on host vasm + libzakalwe + bencode-tools.
    build_vasm_host || return 1

    # UADE libuade build depends on libzakalwe + bencode-tools.
    # Auto-build dependencies when missing, or when clean mode is requested.
    if [ "$FORCE_CLEAN" -eq 1 ] || [ ! -f "$INSTALL_DIR/lib/libzakalwe.a" ] || \
       [ ! -f "$INSTALL_DIR/include/zakalwe/string.h" ]; then
        build_libzakalwe "$ABI"
    fi
    if [ "$FORCE_CLEAN" -eq 1 ] || [ ! -f "$INSTALL_DIR/lib/libbencodetools.a" ] || \
       [ ! -f "$INSTALL_DIR/include/bencodetools/bencode.h" ]; then
        build_bencodetools "$ABI"
    fi
    if [ ! -f "$INSTALL_DIR/lib/libzakalwe.a" ] || [ ! -f "$INSTALL_DIR/include/zakalwe/string.h" ] || \
       [ ! -f "$INSTALL_DIR/lib/libbencodetools.a" ] || [ ! -f "$INSTALL_DIR/include/bencodetools/bencode.h" ]; then
        echo "Error: uade dependencies still missing for $ABI after attempted build."
        return 1
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
            echo "Unsupported ABI for uade: $ABI"
            return 1
            ;;
    esac

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include/uade"
    mkdir -p "$UADE_DEPS_PREFIX/lib" "$UADE_DEPS_PREFIX/include"
    mkdir -p "$HOST_VASM_WRAPPER_DIR"

    # UADE configure looks for shared-lib names when resolving prefixes.
    # Provide local ABI-scoped shim names pointing to our static archives.
    ln -sfn "$INSTALL_DIR/lib/libzakalwe.a" "$UADE_DEPS_PREFIX/lib/libzakalwe.so"
    ln -sfn "$INSTALL_DIR/lib/libbencodetools.a" "$UADE_DEPS_PREFIX/lib/libbencodetools.so"
    ln -sfn "$INSTALL_DIR/include/zakalwe" "$UADE_DEPS_PREFIX/include/zakalwe"
    ln -sfn "$INSTALL_DIR/include/bencodetools" "$UADE_DEPS_PREFIX/include/bencodetools"

    cat > "$HOST_VASM_WRAPPER" <<EOF
#!/usr/bin/env bash
exec "$ABSOLUTE_PATH/vasm/vasmm68k_mot" "\$@"
EOF
    chmod +x "$HOST_VASM_WRAPPER"

    (
        cd "$BUILD_DIR"
        PATH="$HOST_VASM_WRAPPER_DIR:$PATH" \
        CC="$HOST_NATIVE_CC" \
        CXX="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++" \
        AR="$TOOLCHAIN/bin/llvm-ar" \
        CFLAGS="-fPIC $DEP_OPT_FLAGS" \
        LDFLAGS="-L$INSTALL_DIR/lib" \
        "$PROJECT_PATH/configure" \
            --srcdir="$PROJECT_PATH" \
            --host="$CONFIGURE_HOST" \
            --prefix="$INSTALL_DIR" \
            --pkg-config=false \
            --without-uade123 \
            --without-uadesimple \
            --without-uadefs \
            --without-write-audio \
            --without-avx2 \
            --bencode-tools-prefix="$UADE_DEPS_PREFIX" \
            --libzakalwe-prefix="$UADE_DEPS_PREFIX" \
            --target-cc="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang" \
            --target-ar="$TOOLCHAIN/bin/llvm-ar" \
            --target-objcopy="$TOOLCHAIN/bin/llvm-objcopy"

        PATH="$HOST_VASM_WRAPPER_DIR:$PATH" make --no-print-directory V=0 -j"$NPROC" staticlibuade uadecore score
    )

    if [ ! -f "$BUILD_DIR/src/frontends/common/libuade.a" ]; then
        echo "Error: uade static library not found after build."
        return 1
    fi

    cp "$BUILD_DIR/src/frontends/common/libuade.a" "$INSTALL_DIR/lib/libuade.a"
    cp "$BUILD_DIR/src/frontends/include/uade/"*.h "$INSTALL_DIR/include/uade/" 2>/dev/null || true

    if [ ! -f "$BUILD_DIR/src/uadecore" ]; then
        echo "Error: uadecore binary not found after build."
        return 1
    fi
    if [ ! -f "$BUILD_DIR/amigasrc/score/score" ]; then
        echo "Error: UADE score binary not found after build."
        return 1
    fi

    mkdir -p "$INSTALL_DIR/lib/uade" "$INSTALL_DIR/share/uade"
    cp "$BUILD_DIR/src/uadecore" "$INSTALL_DIR/lib/uade/uadecore"
    cp "$BUILD_DIR/amigasrc/score/score" "$INSTALL_DIR/share/uade/score"
    cp "$PROJECT_PATH/uaerc" "$INSTALL_DIR/share/uade/uaerc"
    cp "$PROJECT_PATH/eagleplayer.conf" "$INSTALL_DIR/share/uade/eagleplayer.conf"
    rm -rf "$INSTALL_DIR/share/uade/players"
    cp -R "$PROJECT_PATH/players" "$INSTALL_DIR/share/uade/players"

    mkdir -p "$INSTALL_DIR/lib"
    printf '%s\n' "$UADE_STAMP_EXPECTED" > "$UADE_STAMP"
}

# -----------------------------------------------------------------------------
# Function: Build hivelytracker replayer (libhivelytracker static)
# -----------------------------------------------------------------------------
build_hivelytracker() {
    local ABI=$1
    echo "Building hivelytracker for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/hivelytracker"
    local REPLAYER_DIR="$PROJECT_PATH/Replayer_Windows"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local TARGET_CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "hivelytracker source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ ! -f "$REPLAYER_DIR/hvl_replay.c" ] || [ ! -f "$REPLAYER_DIR/hvl_tables.c" ] || \
       [ ! -f "$REPLAYER_DIR/hvl_replay.h" ] || [ ! -f "$REPLAYER_DIR/hvl_tables.h" ]; then
        echo "Error: hivelytracker replayer sources not found in $REPLAYER_DIR."
        return 1
    fi

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libhivelytracker.a" ] && \
       [ -f "$INSTALL_DIR/include/hivelytracker/hvl_replay.h" ] && \
       [ -f "$INSTALL_DIR/include/hivelytracker/hvl_tables.h" ]; then
        echo "hivelytracker already built for $ABI -> skipping"
        return 0
    fi

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include/hivelytracker"

    # Upstream headers rely on tentative globals in headers; keep common-symbol
    # behavior to avoid duplicate-definition link issues with modern clang.
    # Hively uses "typedef char int8" and requires signed semantics for waveform math.
    # Android NDK defaults plain char to unsigned on ARM, so force signed char here.
    "$TARGET_CC" -c "$REPLAYER_DIR/hvl_replay.c" \
        -o "$BUILD_DIR/hvl_replay.o" \
        -fPIC -fcommon -fsigned-char $DEP_OPT_FLAGS
    "$TARGET_CC" -c "$REPLAYER_DIR/hvl_tables.c" \
        -o "$BUILD_DIR/hvl_tables.o" \
        -fPIC -fcommon -fsigned-char $DEP_OPT_FLAGS

    "$TOOLCHAIN/bin/llvm-ar" rcs "$INSTALL_DIR/lib/libhivelytracker.a" \
        "$BUILD_DIR/hvl_replay.o" \
        "$BUILD_DIR/hvl_tables.o"
    "$TOOLCHAIN/bin/llvm-ranlib" "$INSTALL_DIR/lib/libhivelytracker.a"

    cp "$REPLAYER_DIR/hvl_replay.h" "$INSTALL_DIR/include/hivelytracker/"
    cp "$REPLAYER_DIR/hvl_tables.h" "$INSTALL_DIR/include/hivelytracker/"
}

# -----------------------------------------------------------------------------
# Function: Build klystrack replay engine (libklystrack static)
# -----------------------------------------------------------------------------
build_klystrack() {
    local ABI=$1
    echo "Building klystrack for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/klystrack"
    local KLYSTRON_PATH="$PROJECT_PATH/klystron"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local SDL_SHIM_DIR="$BUILD_DIR/sdl_compat"
    local TARGET_CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang"

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "klystrack source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    # Do not auto-clone missing deps; fail fast so they can be provisioned explicitly.
    if [ ! -f "$KLYSTRON_PATH/src/lib/ksnd.c" ] || [ ! -d "$KLYSTRON_PATH/src/snd" ]; then
        echo "Error: missing required klystrack dependency 'klystron' at $KLYSTRON_PATH."
        echo "Please populate external/klystrack/klystron first, then rerun build_deps."
        return 1
    fi

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libklystrack.a" ] && \
       [ -f "$INSTALL_DIR/include/klystrack/ksnd.h" ]; then
        echo "klystrack already built for $ABI -> skipping"
        return 0
    fi

    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR" "$SDL_SHIM_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include/klystrack"

    # Build against a local SDL compatibility surface so CI does not depend on host SDL headers.
    cat > "$SDL_SHIM_DIR/SDL.h" <<'EOF'
#ifndef SILICONPLAYER_KLYSTRACK_SDL_H
#define SILICONPLAYER_KLYSTRACK_SDL_H

#include <stddef.h>
#include <stdint.h>

typedef uint8_t Uint8;
typedef int8_t Sint8;
typedef uint16_t Uint16;
typedef int16_t Sint16;
typedef uint32_t Uint32;
typedef int32_t Sint32;
typedef uint64_t Uint64;
typedef int64_t Sint64;

typedef struct SDL_mutex SDL_mutex;
typedef struct SDL_RWops SDL_RWops;

typedef struct SDL_AudioSpec {
    int freq;
    Uint16 format;
    Uint8 channels;
    Uint8 silence;
    Uint16 samples;
    Uint16 padding;
    Uint32 size;
    void (*callback)(void *userdata, Uint8 *stream, int len);
    void *userdata;
} SDL_AudioSpec;

#define AUDIO_S16SYS 0x8010

Uint32 SDL_GetTicks(void);
void SDL_Delay(Uint32 ms);
SDL_mutex* SDL_CreateMutex(void);
void SDL_DestroyMutex(SDL_mutex* mutex);
int SDL_LockMutex(SDL_mutex* mutex);
int SDL_UnlockMutex(SDL_mutex* mutex);
size_t SDL_RWread(SDL_RWops* context, void* ptr, size_t size, size_t maxnum);
int SDL_OpenAudio(void* desired, void* obtained);
void SDL_PauseAudio(int pauseOn);
void SDL_CloseAudio(void);

#endif
EOF

    cat > "$SDL_SHIM_DIR/SDL_endian.h" <<'EOF'
#ifndef SILICONPLAYER_KLYSTRACK_SDL_ENDIAN_H
#define SILICONPLAYER_KLYSTRACK_SDL_ENDIAN_H

#include "SDL.h"

static inline Uint16 SDL_Swap16(Uint16 value) {
    return (Uint16)((value << 8) | (value >> 8));
}

static inline Uint32 SDL_Swap32(Uint32 value) {
    return ((value & 0x000000FFu) << 24) |
           ((value & 0x0000FF00u) << 8) |
           ((value & 0x00FF0000u) >> 8) |
           ((value & 0xFF000000u) >> 24);
}

#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__)
#define SDL_SwapLE16(X) ((Uint16)(X))
#define SDL_SwapLE32(X) ((Uint32)(X))
#else
#define SDL_SwapLE16(X) SDL_Swap16((Uint16)(X))
#define SDL_SwapLE32(X) SDL_Swap32((Uint32)(X))
#endif

#endif
EOF

    local common_flags
    common_flags="-fPIC $DEP_OPT_FLAGS -DNOSDL_MIXER -DSTEREOOUTPUT -DUSESDLMUTEXES -I$SDL_SHIM_DIR -I$KLYSTRON_PATH/src"

    local src
    for src in "$KLYSTRON_PATH"/src/snd/*.c "$KLYSTRON_PATH"/src/lib/ksnd.c; do
        local obj
        obj="$BUILD_DIR/$(basename "$src" .c).o"
        "$TARGET_CC" -c "$src" -o "$obj" $common_flags
    done

    "$TOOLCHAIN/bin/llvm-ar" rcs "$INSTALL_DIR/lib/libklystrack.a" "$BUILD_DIR"/*.o
    "$TOOLCHAIN/bin/llvm-ranlib" "$INSTALL_DIR/lib/libklystrack.a"

    cp "$KLYSTRON_PATH/src/lib/ksnd.h" "$INSTALL_DIR/include/klystrack/"
}

# -----------------------------------------------------------------------------
# Function: Build Furnace headless engine (libfurnace shared)
# -----------------------------------------------------------------------------
build_furnace() {
    local ABI=$1
    echo "Building furnace (headless) for $ABI..."

    local INSTALL_DIR="$ABSOLUTE_PATH/../app/src/main/cpp/prebuilt/$ABI"
    local PROJECT_PATH="$ABSOLUTE_PATH/furnace"
    local BUILD_DIR="$PROJECT_PATH/build_android_${ABI}"
    local CMAKE_FILE="$PROJECT_PATH/CMakeLists.txt"
    local MOBILE_FLAG_OLD="if (ANDROID AND NOT TERMUX)"
    local MOBILE_FLAG_NEW="if (ANDROID AND NOT TERMUX AND BUILD_GUI)"
    local BUILT_LIB=""

    if [ ! -d "$PROJECT_PATH" ]; then
        echo "furnace source not found at $PROJECT_PATH (skipping)."
        return 0
    fi

    if [ ! -f "$CMAKE_FILE" ]; then
        echo "Error: furnace source at $PROJECT_PATH does not contain CMakeLists.txt."
        echo "Ensure submodules are initialized:"
        echo "  git -C \"$ABSOLUTE_PATH\" submodule update --init --recursive"
        return 1
    fi

    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libfurnace.so" ]; then
        echo "furnace already built for $ABI -> skipping"
        return 0
    fi

    mkdir -p "$BUILD_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include/furnace"

    # Furnace defines IS_MOBILE on Android and then expects SDL2. For headless
    # builds we patch this define guard in source once (idempotent).
    if grep -Fq "$MOBILE_FLAG_NEW" "$CMAKE_FILE"; then
        echo "furnace mobile define already headless-safe."
    elif grep -Fq "$MOBILE_FLAG_OLD" "$CMAKE_FILE"; then
        sed -i "s/if (ANDROID AND NOT TERMUX)/if (ANDROID AND NOT TERMUX AND BUILD_GUI)/" "$CMAKE_FILE"
        echo "Patched furnace CMakeLists for headless Android build."
    else
        echo "Warning: furnace mobile define pattern not found; continuing without patch."
    fi

    if ! cmake -Wno-dev -Wno-deprecated \
        -S "$PROJECT_PATH" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_C_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_CXX_FLAGS="$DEP_OPT_FLAGS" \
        -DCMAKE_C_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_CXX_FLAGS_RELEASE="$DEP_OPT_FLAGS -DNDEBUG" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_GUI=OFF \
        -DUSE_SDL2=OFF \
        -DUSE_SNDFILE=ON \
        -DWITH_LOCALE=OFF \
        -DUSE_MOMO=OFF \
        -DUSE_RTMIDI=OFF \
        -DUSE_BACKWARD=OFF \
        -DWITH_OGG=OFF \
        -DWITH_MPEG=OFF \
        -DWITH_JACK=OFF \
        -DWITH_PORTAUDIO=OFF \
        -DWITH_RENDER_SDL=OFF \
        -DWITH_RENDER_OPENGL=OFF \
        -DWITH_RENDER_OPENGL1=OFF \
        -DWITH_RENDER_DX11=OFF \
        -DWITH_RENDER_DX9=OFF \
        -DWITH_RENDER_METAL=OFF \
        -DUSE_GLES=OFF \
        -DUSE_FREETYPE=OFF \
        -DWITH_DEMOS=OFF \
        -DWITH_INSTRUMENTS=OFF \
        -DWITH_WAVETABLES=OFF \
        -DNO_INTRO=ON \
        -DWARNINGS_ARE_ERRORS=OFF; then
        return 1
    fi

    if ! cmake --build "$BUILD_DIR" --target furnace -j"$NPROC"; then
        return 1
    fi

    BUILT_LIB="$BUILD_DIR/libfurnace.so"
    if [ ! -f "$BUILT_LIB" ]; then
        BUILT_LIB="$(find "$BUILD_DIR" -type f -name 'libfurnace.so' | head -n 1)"
    fi
    if [ -z "$BUILT_LIB" ] || [ ! -f "$BUILT_LIB" ]; then
        echo "Error: furnace shared library not found after build."
        return 1
    fi

    cp "$BUILT_LIB" "$INSTALL_DIR/lib/libfurnace.so"

    mkdir -p "$INSTALL_DIR/include/furnace/engine" "$INSTALL_DIR/include/furnace/audio"
    cp "$PROJECT_PATH/src/"*.h "$INSTALL_DIR/include/furnace/" 2>/dev/null || true
    cp "$PROJECT_PATH/src/engine/"*.h "$INSTALL_DIR/include/furnace/engine/" 2>/dev/null || true
    cp "$PROJECT_PATH/src/audio/"*.h "$INSTALL_DIR/include/furnace/audio/" 2>/dev/null || true
}

# -----------------------------------------------------------------------------
# Argument Parsing
# -----------------------------------------------------------------------------
usage() {
    echo "Usage: $0 <abi|all> <lib|all[,lib2,...]> [clean]"
    echo "  ABI: all, arm64-v8a, armeabi-v7a, x86_64 (x86 supported only when explicitly requested)"
    echo "  LIB: all, libsoxr, openssl, ffmpeg, libopenmpt, libvgm, libgme, libresid, libresidfp, libsidplayfp, lazyusf2, psflib, vio2sf, fluidsynth, sc68, libbinio, adplug, libzakalwe, bencodetools, vasm, uade, hivelytracker, klystrack, furnace"
    echo "  clean (optional): force rebuild (bypass already-built skip checks)"
    echo "  Aliases: sox/soxr, gme, resid/residfp, sid/sidplayfp, usf/lazyusf, psf, 2sf/twosf, fluid/libfluidsynth, libsc68, binio, libadplug, zakalwe, bencode, assembler/vasm, libuade, hvl/hively, kly/kt, fur"
}

if [ "$#" -eq 1 ]; then
    echo "Error: missing second argument."
    usage
    exit 1
fi

if [ "$#" -gt 3 ]; then
    echo "Error: too many arguments."
    usage
    exit 1
fi

TARGET_ABI=${1:-all}
TARGET_LIB=${2:-all}
FORCE_CLEAN=0

if [ "$#" -eq 3 ]; then
    case "$3" in
        clean|--clean)
            FORCE_CLEAN=1
            ;;
        *)
            echo "Error: invalid third argument '$3'. Expected 'clean'."
            usage
            exit 1
            ;;
    esac
fi

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
        resid)
            echo "libresid"
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
        psf|psflib)
            echo "psflib"
            ;;
        2sf|twosf|vio2sf)
            echo "vio2sf"
            ;;
        fluid|fluidsynth|libfluidsynth)
            echo "fluidsynth"
            ;;
        sc68|libsc68)
            echo "sc68"
            ;;
        binio|libbinio)
            echo "libbinio"
            ;;
        adplug|libadplug)
            echo "adplug"
            ;;
        zakalwe|libzakalwe)
            echo "libzakalwe"
            ;;
        bencode|bencodetools|libbencodetools)
            echo "bencodetools"
            ;;
        assembler|vasm)
            echo "vasm"
            ;;
        uade|libuade)
            echo "uade"
            ;;
        hvl|hively|hivelytracker|libhivelytracker)
            echo "hivelytracker"
            ;;
        kly|kt|klystrack|libklystrack)
            echo "klystrack"
            ;;
        fur|furnace|libfurnace)
            echo "furnace"
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
        all|libsoxr|openssl|ffmpeg|libopenmpt|libvgm|libgme|libresid|libresidfp|libsidplayfp|lazyusf2|psflib|vio2sf|fluidsynth|sc68|libbinio|adplug|libzakalwe|bencodetools|vasm|uade|hivelytracker|klystrack|furnace)
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

if [ "$FORCE_CLEAN" -eq 1 ]; then
    echo "Clean mode enabled: forcing rebuilds (skip checks disabled)."
fi

# Resolve effective ABI list for this invocation.
ABIS=("${DEFAULT_ABIS[@]}")
if [ "$TARGET_ABI" = "x86" ]; then
    ABIS=("x86")
fi

# -----------------------------------------------------------------------------
# Pre-build setup (Apply patches once)
# -----------------------------------------------------------------------------
if target_has_lib "libsidplayfp"; then
    ensure_system_dependencies
fi

if target_has_lib "libopenmpt"; then
    apply_libopenmpt_patches
fi

if target_has_lib "libvgm"; then
    apply_libvgm_patches
fi

if target_has_lib "libgme"; then
    apply_libgme_patches
fi

if target_has_lib "lazyusf2"; then
    apply_lazyusf2_patches
fi

if target_has_lib "vio2sf"; then
    apply_vio2sf_patches
fi

if target_has_lib "adplug"; then
    apply_adplug_patches
fi

if target_has_lib "klystrack"; then
    apply_klystrack_patches
fi

if target_has_lib "uade"; then
    apply_uade_patches
fi

if target_has_lib "vasm"; then
    build_vasm_host
fi

# -----------------------------------------------------------------------------
# Main Loop
# -----------------------------------------------------------------------------
processed_abis=0
for ABI in "${ABIS[@]}"; do
    if [ "$TARGET_ABI" != "all" ] && [ "$TARGET_ABI" != "$ABI" ]; then
        continue
    fi
    processed_abis=$((processed_abis + 1))

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

    CC_BIN="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang"
    CXX_BIN="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang++"

    export CC="$CC_BIN -fPIC"
    export CXX="$CXX_BIN -fPIC"
    export AR="$TOOLCHAIN/bin/llvm-ar"
    export LD="$TOOLCHAIN/bin/ld"
    export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$TOOLCHAIN/bin/llvm-strip"
    export NM="$TOOLCHAIN/bin/llvm-nm"

    echo "CC is set to: $CC"
    echo "Dependency flags: $DEP_OPT_FLAGS"

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

    if target_has_lib "libresid"; then
        build_libresid "$ABI"
    fi

    if target_has_lib "libsidplayfp"; then
        build_libsidplayfp "$ABI"
    fi

    if target_has_lib "lazyusf2"; then
        build_lazyusf2 "$ABI"
    fi

    if target_has_lib "psflib"; then
        build_psflib "$ABI"
    fi

    if target_has_lib "vio2sf"; then
        build_vio2sf "$ABI"
    fi

    if target_has_lib "fluidsynth"; then
        build_fluidsynth "$ABI"
    fi

    if target_has_lib "sc68"; then
        build_sc68 "$ABI"
    fi

    if target_has_lib "libbinio"; then
        build_libbinio "$ABI"
    fi

    if target_has_lib "adplug"; then
        build_adplug "$ABI"
    fi

    if target_has_lib "libzakalwe"; then
        build_libzakalwe "$ABI"
    fi

    if target_has_lib "bencodetools"; then
        build_bencodetools "$ABI"
    fi

    if target_has_lib "uade"; then
        build_uade "$ABI"
    fi

    if target_has_lib "hivelytracker"; then
        build_hivelytracker "$ABI"
    fi

    if target_has_lib "klystrack"; then
        build_klystrack "$ABI"
    fi

    if target_has_lib "furnace"; then
        build_furnace "$ABI"
    fi
done

if [ "$processed_abis" -eq 0 ]; then
    echo "Error: no ABI was processed for TARGET_ABI='$TARGET_ABI'."
    exit 1
fi

echo "Build complete!"
