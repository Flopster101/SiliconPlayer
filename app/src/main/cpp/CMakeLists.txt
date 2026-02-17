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
        AudioEngine.cpp
        decoders/FFmpegDecoder.cpp
        decoders/DecoderRegistry.cpp
        decoders/LibOpenMPTDecoder.cpp
        decoders/VGMDecoder.cpp
        decoders/GmeDecoder.cpp
        decoders/LibSidPlayFpDecoder.cpp
        decoders/CPConvStub.c
)

# Android 15+ prefers 16KB page-aligned native libraries.
if (ANDROID)
    target_link_options(
            siliconplayer
            PRIVATE
            "-Wl,-z,max-page-size=16384"
            "-Wl,-z,common-page-size=16384"
    )
endif()

include_directories(${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

    # Find Android libraries
    find_library(log-lib log)
    find_library(android-lib android)
    find_library(aaudio-lib aaudio)
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

    # Import libsidplayfp + libresidfp (Static)
    add_library(libsidplayfp STATIC IMPORTED)
    set_target_properties(libsidplayfp PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsidplayfp.a")

    add_library(libresidfp STATIC IMPORTED)
    set_target_properties(libresidfp PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresidfp.a")

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
            libsidplayfp
            libresidfp
            z # zlib is needed for openmpt/ffmpeg/vgm
    )

    if (HAS_OPENSSL_PREBUILT)
        target_link_libraries(siliconplayer libssl libcrypto ${dl-lib})
    endif()
