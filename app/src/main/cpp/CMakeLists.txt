cmake_minimum_required(VERSION 3.18.1)

project("ytproencrypt")

add_library(
        ytproencrypt
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        ytproencrypt
        ${log-lib}
)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
