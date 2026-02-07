cmake_minimum_required(VERSION 3.22.1)

project("ytpro-native")

# C++17 standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Build native library
add_library(
    ytpro-native
    SHARED
    ytpro-native.cpp
)

# Link Android log library
find_library(log-lib log)

target_link_libraries(
    ytpro-native
    ${log-lib}
)

# Include scripts directory
target_include_directories(
    ytpro-native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/scripts
)
