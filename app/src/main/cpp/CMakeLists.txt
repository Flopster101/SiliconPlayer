cmake_minimum_required(VERSION 3.22.1)

project("siliconplayer")

# Define the library name
set(CMAKE_CXX_STANDARD 20)

# -----------------------------------------------------------------------------
# LibOpenMPT Configuration (Paused due to build complexity)
# -----------------------------------------------------------------------------
# set(LIBOPENMPT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/libopenmpt)
# ... sources ...

# -----------------------------------------------------------------------------
# Main Native Library
# -----------------------------------------------------------------------------

add_library(
        siliconplayer
        SHARED
        SiliconPlayerNative.cpp
        AudioTrackJniBridge.cpp
        AudioEngine.cpp
        AudioEngineStream.cpp
        AudioEngineRender.cpp
        AudioEngineCoreOptions.cpp
        AudioEngineMetadata.cpp
        AudioEngineTransport.cpp
        AudioEnginePipeline.cpp
        AudioEngineEffects.cpp
        decoders/sid/ReSidBuilder.cpp
        decoders/sid/ReSidEmu.cpp
        decoders/FFmpegDecoder.cpp
        decoders/DecoderRegistry.cpp
        decoders/LibOpenMPTDecoder.cpp
        decoders/VGMDecoder.cpp
        decoders/GmeDecoder.cpp
        decoders/LibSidPlayFpDecoder.cpp
        decoders/LazyUsf2Decoder.cpp
        decoders/Vio2sfDecoder.cpp
        decoders/Sc68Decoder.cpp
        decoders/AdPlugDecoder.cpp
        decoders/UadeDecoder.cpp
        decoders/HivelyTrackerDecoder.cpp
        decoders/KlystrackDecoder.cpp
        decoders/FurnaceDecoder.cpp
        decoders/SdlCompat.c
        decoders/CPConvStub.c
)

# Furnace exported headers rely on this define for SFWrapper-dependent structs.
target_compile_definitions(siliconplayer PRIVATE HAVE_SNDFILE)

# Android 15+ prefers 16KB page-aligned native libraries.
if (ANDROID)
    target_compile_options(
            siliconplayer
            PRIVATE
            "-fvisibility=hidden"
            "-fvisibility-inlines-hidden"
    )
    target_link_options(
            siliconplayer
            PRIVATE
            "-Wl,-z,max-page-size=16384"
            "-Wl,-z,common-page-size=16384"
            "-Wl,--exclude-libs,ALL"
    )
endif()

include_directories(${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)
# Expose full libopenmpt headers (including libopenmpt_ext.hpp) from the submodule tree.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/libopenmpt)
# ReSID backend implementation requires libsidplayfp internal SID emulation headers.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/libsidplayfp/src)
# Furnace headers depend on bundled fmt headers through ta-log.h.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/furnace/extern/fmt/include)
# Furnace engine headers also reference bundled blip_buf headers directly.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/furnace/extern/blip_buf)
# Furnace SFWrapper type declarations require bundled libsndfile public headers.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/furnace/extern/libsndfile-modified/include)

    # Find Android libraries
    find_library(log-lib log)
    find_library(android-lib android)
    find_library(aaudio-lib aaudio)
    find_library(opensles-lib OpenSLES)
    find_library(mediandk-lib mediandk)
    find_library(dl-lib dl)

    set(PREBUILT_DIR ${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI})

    # Import libopenmpt (Static)
    add_library(libopenmpt STATIC IMPORTED)
    set_target_properties(libopenmpt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/${ANDROID_ABI}/libopenmpt.a")

    # Import FFmpeg libraries (Static) - Order matters for static linking!
    add_library(libavformat STATIC IMPORTED)
    set_target_properties(libavformat PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavformat.a")

    add_library(libavcodec STATIC IMPORTED)
    set_target_properties(libavcodec PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavcodec.a")

    add_library(libavutil STATIC IMPORTED)
    set_target_properties(libavutil PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavutil.a")

    add_library(libswresample STATIC IMPORTED)
    set_target_properties(libswresample PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libswresample.a")

    add_library(libsoxr STATIC IMPORTED)
    set_target_properties(libsoxr PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsoxr.a")

    # Import libvgm libraries (Static)
    add_library(libvgm-utils STATIC IMPORTED)
    set_target_properties(libvgm-utils PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-utils.a")

    add_library(libvgm-emu STATIC IMPORTED)
    set_target_properties(libvgm-emu PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-emu.a")

    add_library(libvgm-player STATIC IMPORTED)
    set_target_properties(libvgm-player PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-player.a")

    # Import libgme (Static)
    add_library(libgme STATIC IMPORTED)
    set_target_properties(libgme PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libgme.a")

    # Import lazyusf2 (Static)
    add_library(liblazyusf2 STATIC IMPORTED)
    set_target_properties(liblazyusf2 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/liblazyusf2.a")

    # Import psflib + vio2sf (Static)
    add_library(libpsflib STATIC IMPORTED)
    set_target_properties(libpsflib PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libpsflib.a")

    add_library(libvio2sf STATIC IMPORTED)
    set_target_properties(libvio2sf PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvio2sf.a")

    # Import sc68 stack (Static)
    add_library(libsc68 STATIC IMPORTED)
    set_target_properties(libsc68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsc68.a")

    add_library(libfile68 STATIC IMPORTED)
    set_target_properties(libfile68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfile68.a")

    add_library(libunice68 STATIC IMPORTED)
    set_target_properties(libunice68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libunice68.a")

    # Import adplug stack (Static)
    add_library(libadplug STATIC IMPORTED)
    set_target_properties(libadplug PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libadplug.a")

    set(LIBBINIO_LOCATION "${PREBUILT_DIR}/lib/libbinio.a")
    if (NOT EXISTS "${LIBBINIO_LOCATION}")
        set(LIBBINIO_LOCATION "${PREBUILT_DIR}/lib/liblibbinio.a")
    endif()
    add_library(libbinio STATIC IMPORTED)
    set_target_properties(libbinio PROPERTIES IMPORTED_LOCATION "${LIBBINIO_LOCATION}")

    # Import UADE stack (Static)
    add_library(libuade STATIC IMPORTED)
    set_target_properties(libuade PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libuade.a")

    add_library(libzakalwe STATIC IMPORTED)
    set_target_properties(libzakalwe PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libzakalwe.a")

    add_library(libbencodetools STATIC IMPORTED)
    set_target_properties(libbencodetools PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libbencodetools.a")

    # Import hivelytracker (Static)
    add_library(libhivelytracker STATIC IMPORTED)
    set_target_properties(libhivelytracker PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libhivelytracker.a")

    # Import klystrack replayer (Static)
    add_library(libklystrack STATIC IMPORTED)
    set_target_properties(libklystrack PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libklystrack.a")

    # Import furnace headless engine (Shared)
    add_library(libfurnace SHARED IMPORTED)
    set_target_properties(libfurnace PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfurnace.so")

    # Import libsidplayfp + libresidfp (Static)
    add_library(libsidplayfp STATIC IMPORTED)
    set_target_properties(libsidplayfp PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsidplayfp.a")

    add_library(libresidfp STATIC IMPORTED)
    set_target_properties(libresidfp PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresidfp.a")

    add_library(libresid STATIC IMPORTED)
    set_target_properties(libresid PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresid.a")

    set(HAS_OPENSSL_PREBUILT FALSE)
    if (EXISTS "${PREBUILT_DIR}/lib/libssl.a" AND EXISTS "${PREBUILT_DIR}/lib/libcrypto.a")
        add_library(libssl STATIC IMPORTED)
        set_target_properties(libssl PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libssl.a")

        add_library(libcrypto STATIC IMPORTED)
        set_target_properties(libcrypto PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libcrypto.a")
        set(HAS_OPENSSL_PREBUILT TRUE)
    endif()

    # Link libraries
    target_link_libraries(
            siliconplayer
            ${log-lib}
            ${android-lib}
            ${aaudio-lib}
            ${opensles-lib}
            ${mediandk-lib}
            libopenmpt
            libavformat
            libavcodec
            libavutil
            libswresample
            libsoxr
            libvgm-player
            libvgm-emu
            libvgm-utils
            libgme
            liblazyusf2
            libvio2sf
            libpsflib
            libsc68
            libfile68
            libunice68
            libadplug
            libbinio
            libuade
            libzakalwe
            libbencodetools
            libhivelytracker
            libklystrack
            "-Wl,--start-group"
            libsidplayfp
            libresidfp
            libresid
            "-Wl,--end-group"
            libfurnace
            z # zlib is needed for openmpt/ffmpeg/vgm
            m # libbinio can require libm symbols on some toolchains
    )

    if (HAS_OPENSSL_PREBUILT)
        target_link_libraries(siliconplayer libssl libcrypto ${dl-lib})
    endif()
