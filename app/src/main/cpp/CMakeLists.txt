cmake_minimum_required(VERSION 3.18.1)

# Project name
project("ytproencrypt")

# Add native library
add_library(
    ytproencrypt
    SHARED
    native-lib.cpp
)

# Find required libraries
find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

# Link libraries
target_link_libraries(
    ytproencrypt
    ${log-lib}
    ${android-lib}
)

# C++ standard and properties
set_target_properties(ytproencrypt PROPERTIES
    CXX_STANDARD 14
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
)

# Compilation flags
target_compile_options(ytproencrypt PRIVATE
    -Wall
    -Werror
    -fvisibility=hidden
    -fPIC
)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})
