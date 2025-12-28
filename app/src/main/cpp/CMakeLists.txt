cmake_minimum_required(VERSION 3.22.1)

# Project name
project("ytsec")

# Add shared library
add_library(
    ytsec
    SHARED
    native-lib.cpp
)

# Find log library
find_library(
    log-lib
    log
)

# Link libraries
target_link_libraries(
    ytsec
    ${log-lib}
)
