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
)

include_directories(${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

    # Find Android libraries
    find_library(log-lib log)
    find_library(android-lib android)
    find_library(aaudio-lib aaudio)
    find_library(mediandk-lib mediandk)

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
            z # zlib is needed for openmpt/ffmpeg
    )
