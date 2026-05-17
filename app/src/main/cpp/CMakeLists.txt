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
        ChannelScopeSharedState.cpp
        ChannelScopeTrigger.cpp
        AudioTrackJniBridge.cpp
        AudioEngine.cpp
        AudioEngineStream.cpp
        AudioEngineRender.cpp
        AudioEngineCoreOptions.cpp
        AudioEngineMetadata.cpp
        AudioEngineTransport.cpp
        AudioEnginePipeline.cpp
        AudioEngineEffects.cpp
        effects/openmpt_dsp/OpenMptDspEffects.cpp
        decoders/DecoderPluginLoader.cpp
        decoders/DecoderRegistry.cpp
        decoders/SdlCompat.c
        decoders/CPConvStub.c
)

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
    find_library(opensles-lib OpenSLES)
    find_library(mediandk-lib mediandk)
    find_library(dl-lib dl)

    set(PREBUILT_DIR ${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI})

    # Import libopenmpt (Shared)
    add_library(libopenmpt SHARED IMPORTED)
    set_target_properties(libopenmpt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/${ANDROID_ABI}/libopenmpt.so")

    # Import FFmpeg libraries (Shared)
    add_library(libavformat SHARED IMPORTED)
    set_target_properties(libavformat PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavformat.so")

    add_library(libavcodec SHARED IMPORTED)
    set_target_properties(libavcodec PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavcodec.so")

    add_library(libavutil SHARED IMPORTED)
    set_target_properties(libavutil PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavutil.so")

    add_library(libswresample SHARED IMPORTED)
    set_target_properties(libswresample PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libswresample.so")

    add_library(libsoxr SHARED IMPORTED)
    set_target_properties(libsoxr PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsoxr.so")

    # Import libvgm libraries (Shared)
    add_library(libvgm-utils SHARED IMPORTED)
    set_target_properties(libvgm-utils PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-utils.so")

    add_library(libvgm-emu SHARED IMPORTED)
    set_target_properties(libvgm-emu PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-emu.so")

    add_library(libvgm-player SHARED IMPORTED)
    set_target_properties(libvgm-player PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-player.so")

    # Import libgme (Shared)
    add_library(libgme SHARED IMPORTED)
    set_target_properties(libgme PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libgme.so")

    # Import lazyusf2 (Static)
    add_library(liblazyusf2 STATIC IMPORTED)
    set_target_properties(liblazyusf2 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/liblazyusf2.a")

    # Import psflib + vio2sf (Shared)
    add_library(libpsflib SHARED IMPORTED)
    set_target_properties(libpsflib PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libpsflib.so")

    add_library(libvio2sf SHARED IMPORTED)
    set_target_properties(libvio2sf PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvio2sf.so")

    # Import sc68 stack (Shared)
    add_library(libsc68 SHARED IMPORTED)
    set_target_properties(libsc68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsc68.so")

    add_library(libfile68 SHARED IMPORTED)
    set_target_properties(libfile68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfile68.so")

    add_library(libunice68 SHARED IMPORTED)
    set_target_properties(libunice68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libunice68.so")

    # Import adplug stack (Shared)
    add_library(libadplug SHARED IMPORTED)
    set_target_properties(libadplug PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libadplug.so")

    set(LIBBINIO_LOCATION "${PREBUILT_DIR}/lib/libbinio.so")
    if (NOT EXISTS "${LIBBINIO_LOCATION}")
        set(LIBBINIO_LOCATION "${PREBUILT_DIR}/lib/liblibbinio.so")
    endif()
    add_library(libbinio SHARED IMPORTED)
    set_target_properties(libbinio PROPERTIES IMPORTED_LOCATION "${LIBBINIO_LOCATION}")

    # Import UADE stack (Shared)
    add_library(libuade SHARED IMPORTED)
    set_target_properties(libuade PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libuade.so")

    add_library(libzakalwe SHARED IMPORTED)
    set_target_properties(libzakalwe PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libzakalwe.so")

    add_library(libbencodetools SHARED IMPORTED)
    set_target_properties(libbencodetools PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libbencodetools.so")

    # Import hivelytracker (Shared)
    add_library(libhivelytracker SHARED IMPORTED)
    set_target_properties(libhivelytracker PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libhivelytracker.so")

    # Import klystrack replayer (Shared)
    add_library(libklystrack SHARED IMPORTED)
    set_target_properties(libklystrack PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libklystrack.so")

    # Import furnace headless engine (Shared)
    add_library(libfurnace SHARED IMPORTED)
    set_target_properties(libfurnace PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfurnace.so")

    # Import libsidplayfp + libresidfp (Shared)
    add_library(libsidplayfp SHARED IMPORTED)
    set_target_properties(libsidplayfp PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsidplayfp.so")

    add_library(libresidfp SHARED IMPORTED)
    set_target_properties(libresidfp PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresidfp.so")

    add_library(libresid SHARED IMPORTED)
    set_target_properties(libresid PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresid.so")

    set(HAS_CRSID_PREBUILT FALSE)
    if (EXISTS "${PREBUILT_DIR}/lib/libcRSID.so")
        add_library(libcRSID SHARED IMPORTED)
        set_target_properties(libcRSID PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libcRSID.so")
        set(HAS_CRSID_PREBUILT TRUE)
    endif()

    set(HAS_OPENSSL_PREBUILT FALSE)
    if (EXISTS "${PREBUILT_DIR}/lib/libssl.so" AND EXISTS "${PREBUILT_DIR}/lib/libcrypto.so")
        add_library(libssl SHARED IMPORTED)
        set_target_properties(libssl PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libssl.so")

        add_library(libcrypto SHARED IMPORTED)
        set_target_properties(libcrypto PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libcrypto.so")
        set(HAS_OPENSSL_PREBUILT TRUE)
    endif()

    # Link libraries
    target_link_libraries(
            siliconplayer
            ${log-lib}
            ${android-lib}
            ${opensles-lib}
            ${mediandk-lib}
            libavutil
            libswresample
            libsoxr
            z # zlib is needed for openmpt/ffmpeg/vgm
            m # libbinio can require libm symbols on some toolchains
            ${dl-lib}
    )

    if (HAS_OPENSSL_PREBUILT)
        target_link_libraries(siliconplayer libssl libcrypto)
    endif()

    add_library(
            siliconplayer_ffmpeg_decoder
            SHARED
            decoders/FFmpegDecoder.cpp
            decoders/FFmpegDecoderPlugin.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_ffmpeg_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_ffmpeg_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_ffmpeg_decoder
            libavformat
            libavcodec
            libavutil
            libswresample
            libsoxr
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_openmpt_decoder
            SHARED
            decoders/LibOpenMPTDecoder.cpp
            decoders/LibOpenMPTDecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_openmpt_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_openmpt_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_openmpt_decoder
            libopenmpt
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_uade_decoder
            SHARED
            decoders/UadeDecoder.cpp
            decoders/UadeDecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_uade_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_uade_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_uade_decoder
            libuade
            libzakalwe
            libbencodetools
            ${log-lib}
            ${dl-lib}
            m
    )

    add_library(
            siliconplayer_vgm_decoder
            SHARED
            decoders/VGMDecoder.cpp
            decoders/VGMDecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_vgm_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_vgm_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_vgm_decoder
            libvgm-player
            libvgm-emu
            libvgm-utils
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_gme_decoder
            SHARED
            decoders/GmeDecoder.cpp
            decoders/GmeDecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_gme_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_gme_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_gme_decoder
            libgme
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_crsid_decoder
            SHARED
            decoders/CRSIDDecoder.cpp
            decoders/CRSIDDecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_crsid_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_crsid_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_crsid_decoder
            libcRSID
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_libsidplayfp_decoder
            SHARED
            decoders/LibSidPlayFpDecoder.cpp
            decoders/LibSidPlayFpDecoderPlugin.cpp
            decoders/sid/ReSidBuilder.cpp
            decoders/sid/ReSidEmu.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_libsidplayfp_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_libsidplayfp_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_libsidplayfp_decoder
            libsidplayfp
            libresidfp
            libresid
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_sc68_decoder
            SHARED
            decoders/Sc68Decoder.cpp
            decoders/Sc68DecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_sc68_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_sc68_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_sc68_decoder
            libsc68
            libfile68
            libunice68
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_adplug_decoder
            SHARED
            decoders/AdPlugDecoder.cpp
            decoders/AdPlugDecoderPlugin.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_adplug_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_adplug_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_adplug_decoder
            libadplug
            libbinio
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_lazyusf2_decoder
            SHARED
            decoders/LazyUsf2Decoder.cpp
            decoders/LazyUsf2DecoderPlugin.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_lazyusf2_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_lazyusf2_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
                "-Wl,--exclude-libs,liblazyusf2.a"
        )
    endif()
    target_link_libraries(
            siliconplayer_lazyusf2_decoder
            liblazyusf2
            ${log-lib}
            z
            m
    )

    add_library(
            siliconplayer_vio2sf_decoder
            SHARED
            decoders/Vio2sfDecoder.cpp
            decoders/Vio2sfDecoderPlugin.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_vio2sf_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_vio2sf_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_vio2sf_decoder
            libvio2sf
            libpsflib
            z
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_hivelytracker_decoder
            SHARED
            decoders/HivelyTrackerDecoder.cpp
            decoders/HivelyTrackerDecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_hivelytracker_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_hivelytracker_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_hivelytracker_decoder
            libhivelytracker
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_klystrack_decoder
            SHARED
            decoders/KlystrackDecoder.cpp
            decoders/KlystrackDecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    if (ANDROID)
        target_compile_options(
                siliconplayer_klystrack_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_klystrack_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_klystrack_decoder
            libklystrack
            ${log-lib}
            m
    )

    add_library(
            siliconplayer_furnace_decoder
            SHARED
            decoders/FurnaceDecoder.cpp
            decoders/FurnaceDecoderPlugin.cpp
            ChannelScopeSharedState.cpp
    )
    target_compile_definitions(siliconplayer_furnace_decoder PRIVATE HAVE_SNDFILE)
    if (ANDROID)
        target_compile_options(
                siliconplayer_furnace_decoder
                PRIVATE
                "-fvisibility=hidden"
                "-fvisibility-inlines-hidden"
        )
        target_link_options(
                siliconplayer_furnace_decoder
                PRIVATE
                "-Wl,-z,max-page-size=16384"
                "-Wl,-z,common-page-size=16384"
        )
    endif()
    target_link_libraries(
            siliconplayer_furnace_decoder
            libfurnace
            ${log-lib}
            m
    )
