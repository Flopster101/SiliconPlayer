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
)

# Find Android libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(aaudio-lib aaudio)

# Link libraries
target_link_libraries(
        siliconplayer
        ${log-lib}
        ${android-lib}
        ${aaudio-lib}
        # openmpt # Link our new library
)
